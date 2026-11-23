package io.github.Mehrdadmoradli.c4jnext.runtime;

import io.github.Mehrdadmoradli.c4jnext.ContractError;
import io.github.Mehrdadmoradli.c4jnext.UsageError;
import io.github.Mehrdadmoradli.c4jnext.annotations.Target;

import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class OldValueStore {
    private record Key(Class<?> owner, String site) {}
    private record Saved(Object value, Throwable failure, int index) {}

    record Capture(Class<?> owner, OldExpressionTransformer.Site site, Field field) {
        Object read(Object receiver) throws IllegalAccessException {
            Object value = field == null ? site.literal() : field.get(receiver);
            if (site.read() != null && site.read().unbox()) {
                Objects.requireNonNull(value, "old() expression unboxed null");
            }
            if (site.box() != null) {
                return switch (site.box()) {
                    case "Z" -> Boolean.valueOf((Boolean) value);
                    case "B" -> Byte.valueOf((Byte) value);
                    case "S" -> Short.valueOf((Short) value);
                    case "C" -> Character.valueOf((Character) value);
                    case "I" -> Integer.valueOf((Integer) value);
                    case "J" -> Long.valueOf((Long) value);
                    case "F" -> Float.valueOf((Float) value);
                    case "D" -> Double.valueOf((Double) value);
                    default -> throw new IllegalStateException("Invalid primitive type " + site.box());
                };
            }
            return field == null && value instanceof String literal ? literal.intern() : value;
        }
    }

    private final Map<Key, Saved> values = new HashMap<>();

    static List<Capture> prepare(Method contract, Class<?> targetType, boolean post, int expectedSites) {
        if (expectedSites == 0) {
            return List.of();
        }
        Class<?> owner = contract.getDeclaringClass();
        var sites = OldExpressionTransformer.sitesFor(owner, contract.getName() + ContractExecutor.descriptor(contract));
        if (!post || sites.size() != expectedSites) {
            throw new UsageError("old() requires a transformed contract method with a postcondition: " + contract);
        }

        var captures = new ArrayList<Capture>();
        for (var site : sites) {
            if (!site.supported()) {
                throw unsupported(contract, site.index(), "expected a literal, direct @Target field or approved getter");
            }
            try {
                Field field = site.read() == null ? null : resolveRead(owner, targetType, site.read());
                captures.add(new Capture(owner, site, field));
            } catch (ReflectiveOperationException | IllegalArgumentException failure) {
                var error = unsupported(contract, site.index(), failure.getMessage());
                error.initCause(failure);
                throw error;
            }
        }
        return List.copyOf(captures);
    }

    void save(List<Capture> captures, Object receiver) {
        for (var capture : captures) {
            Saved saved;
            try {
                saved = new Saved(capture.read(receiver), null, capture.site().index());
            } catch (Throwable failure) {
                saved = new Saved(null, failure, capture.site().index());
            }
            values.put(new Key(capture.owner(), capture.site().key()), saved);
        }
    }

    Object get(Class<?> owner, String site) {
        Saved saved = values.get(new Key(owner, site));
        if (saved == null) {
            throw new UsageError("No old() entry value for " + owner.getName() + "." + site
                    + "; use old() directly in the active contract postcondition");
        }
        if (saved.failure() != null) {
            throw new ContractError("Contract Error, old statement #" + saved.index()
                    + " has thrown exception when evaluating the expression at the beginning of the method.",
                    saved.failure());
        }
        return saved.value();
    }

    private static Field resolveRead(Class<?> owner, Class<?> targetType, OldExpressionTransformer.Read read)
            throws ReflectiveOperationException {
        ClassLoader loader = owner.getClassLoader();
        Field injected = findField(loader, read.target());
        Class<?> declaring = injected.getDeclaringClass();
        int modifiers = injected.getModifiers();
        if (!injected.isAnnotationPresent(Target.class) || Modifier.isStatic(modifiers)
                || Modifier.isFinal(modifiers) || declaring == targetType
                || (!targetType.isInterface() && !targetType.isAssignableFrom(declaring))
                || !declaring.isAssignableFrom(owner)
                || !injected.getType().isAssignableFrom(targetType)) {
            throw new IllegalArgumentException("receiver must be the contract's injected @Target field");
        }
        Field field = read.getter() ? getterField(loader, read.value()) : findField(loader, read.value());
        if (Modifier.isStatic(field.getModifiers()) || !field.getDeclaringClass().isAssignableFrom(targetType)
                || !field.trySetAccessible()) {
            throw new IllegalArgumentException("cannot read target field " + field);
        }
        return field;
    }

    private static Field getterField(ClassLoader loader, OldExpressionTransformer.Member call)
            throws ReflectiveOperationException {
        Method getter = null;
        for (Class<?> type = load(loader, call.owner()); type != null && getter == null; type = type.getSuperclass()) {
            for (Method candidate : type.getDeclaredMethods()) {
                if (candidate.getName().equals(call.name())
                        && ContractExecutor.descriptor(candidate).equals(call.descriptor())) {
                    getter = candidate;
                    break;
                }
            }
        }
        if (getter == null || !Modifier.isFinal(getter.getModifiers()) || Modifier.isStatic(getter.getModifiers())
                || Modifier.isSynchronized(getter.getModifiers()) || Modifier.isNative(getter.getModifiers())) {
            throw new IllegalArgumentException("getter must be a final, non-synchronized field reader");
        }
        for (var method : ContractExecutor.classFileOf(getter.getDeclaringClass()).methods()) {
            if (!method.methodName().equalsString(call.name()) || !method.methodType().equalsString(call.descriptor())) {
                continue;
            }
            var code = method.code().orElseThrow();
            var instructions = code.elementList().stream().filter(Instruction.class::isInstance).toList();
            if (code.exceptionHandlers().isEmpty() && instructions.size() == 3
                    && instructions.get(0) instanceof LoadInstruction load && load.slot() == 0
                    && instructions.get(1) instanceof FieldInstruction field && field.opcode() == Opcode.GETFIELD
                    && field.type().equalsString(call.descriptor().substring(2))
                    && instructions.get(2) instanceof ReturnInstruction) {
                return findField(loader, new OldExpressionTransformer.Member(
                        field.owner().asInternalName(), field.name().stringValue(), field.type().stringValue()));
            }
            break;
        }
        throw new IllegalArgumentException("getter body must return a direct field");
    }

    private static Field findField(ClassLoader loader, OldExpressionTransformer.Member member)
            throws ReflectiveOperationException {
        for (Class<?> type = load(loader, member.owner()); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getName().equals(member.name())
                        && field.getType().descriptorString().equals(member.descriptor())) {
                    return field;
                }
            }
        }
        throw new NoSuchFieldException(member.owner() + "." + member.name());
    }

    private static Class<?> load(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name.replace('/', '.'), false, loader);
    }

    private static UsageError unsupported(Method method, int index, String reason) {
        return new UsageError("Unsupported old() expression #" + index + " in " + method + ": " + reason);
    }
}
