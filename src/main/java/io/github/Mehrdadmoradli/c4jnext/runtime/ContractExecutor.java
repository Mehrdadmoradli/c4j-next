package io.github.Mehrdadmoradli.c4jnext.runtime;

import io.github.Mehrdadmoradli.c4jnext.Condition;
import io.github.Mehrdadmoradli.c4jnext.UsageError;
import io.github.Mehrdadmoradli.c4jnext.annotations.ClassInvariant;
import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;
import io.github.Mehrdadmoradli.c4jnext.annotations.Target;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class ContractExecutor {
    record Checks(Method method, boolean pre, boolean post) {}

    record Binding(Class<?> targetType, Class<?> contractType, List<Method> invariants) {}

    private record Phases(boolean pre, boolean post) {}

    private static final Checks NONE = new Checks(null, false, false);
    private static final String CONDITION = Condition.class.descriptorString();
    private static final ClassValue<ConcurrentMap<Method, Checks>> CHECKS = new ClassValue<>() {
        @Override
        protected ConcurrentMap<Method, Checks> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final ClassValue<Binding> BINDINGS = new ClassValue<>() {
        @Override
        protected Binding computeValue(Class<?> type) {
            var reference = type.getDeclaredAnnotation(ContractReference.class);
            if (reference == null) {
                return new Binding(type, null, List.of());
            }
            Class<?> contract = contractType(type, reference);
            return new Binding(type, contract, declaredInvariants(contract));
        }
    };
    private static final ClassValue<List<Binding>> INVARIANTS = new ClassValue<>() {
        @Override
        protected List<Binding> computeValue(Class<?> type) {
            var inherited = type.getSuperclass() == null ? List.<Binding>of()
                    : get(type.getSuperclass());
            var own = bindingFor(type);
            if (own.invariants().isEmpty()) {
                return inherited;
            }
            var result = new ArrayList<>(inherited);
            result.add(own);
            return List.copyOf(result);
        }
    };

    private ContractExecutor() {}

    static Checks checksFor(Method targetMethod) {
        var checks = CHECKS.get(targetMethod.getDeclaringClass())
                .computeIfAbsent(targetMethod, ContractExecutor::resolve);
        return checks == NONE ? null : checks;
    }

    static Binding bindingFor(Class<?> type) {
        return BINDINGS.get(type);
    }

    static List<Binding> invariantBindingsFor(Class<?> type) {
        return INVARIANTS.get(type);
    }

    private static Checks resolve(Method targetMethod) {
        Class<?> targetType = targetMethod.getDeclaringClass();
        Class<?> contractType = bindingFor(targetType).contractType();

        // Walk contract classes only. A method inherited from the target is application code.
        for (Class<?> type = contractType; type != targetType; type = type.getSuperclass()) {
            for (Method candidate : type.getDeclaredMethods()) {
                if (!matches(targetMethod, candidate)) {
                    continue;
                }
                var phases = phasesOf(candidate);
                return phases.pre() || phases.post()
                        ? new Checks(candidate, phases.pre(), phases.post())
                        : NONE;
            }
        }
        return NONE;
    }

    private static boolean matches(Method target, Method candidate) {
        int modifiers = candidate.getModifiers();
        return candidate.getName().equals(target.getName())
                && Arrays.equals(candidate.getParameterTypes(), target.getParameterTypes())
                && target.getReturnType().isAssignableFrom(candidate.getReturnType())
                && !candidate.isAnnotationPresent(ClassInvariant.class)
                && !candidate.isBridge() && !candidate.isSynthetic()
                && !Modifier.isAbstract(modifiers) && !Modifier.isNative(modifiers)
                && !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers);
    }

    private static Class<?> contractType(Class<?> target, ContractReference reference) {
        Class<?> contract = reference.value();
        if (contract == target || !target.isAssignableFrom(contract)) {
            throw new UsageError(contract.getName() + " must extend " + target.getName());
        }
        return contract;
    }

    private static List<Method> declaredInvariants(Class<?> contract) {
        var annotated = new HashMap<String, Method>();
        for (Method method : contract.getDeclaredMethods()) {
            if (method.isSynthetic() || !method.isAnnotationPresent(ClassInvariant.class)) {
                continue;
            }
            int modifiers = method.getModifiers();
            if (method.getParameterCount() != 0 || Modifier.isStatic(modifiers)
                    || Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
                throw new UsageError("@ClassInvariant must be a concrete zero-argument instance method: " + method);
            }
            annotated.put(method.getName() + descriptor(method), method);
        }
        if (annotated.isEmpty()) {
            return List.of();
        }
        // C4J enumerates declared invariants in class-file order. Reflection does not guarantee that order.
        var ordered = new ArrayList<Method>();
        for (var method : classFileOf(contract).methods()) {
            Method invariant = annotated.get(method.methodName().stringValue() + method.methodType().stringValue());
            if (invariant != null) {
                ordered.add(invariant);
            }
        }
        return List.copyOf(ordered);
    }

    private static String descriptor(Method method) {
        return MethodType.methodType(method.getReturnType(), method.getParameterTypes()).descriptorString();
    }

    private static Phases phasesOf(Method method) {
        String descriptor = descriptor(method);
        for (var candidate : classFileOf(method.getDeclaringClass()).methods()) {
            if (!candidate.methodName().equalsString(method.getName())
                    || !candidate.methodType().equalsString(descriptor)) {
                continue;
            }
            boolean pre = false;
            boolean post = false;
            for (var element : candidate.code().orElseThrow()) {
                if (element instanceof InvokeInstruction call
                        && call.owner().asSymbol().descriptorString().equals(CONDITION)
                        && call.type().equalsString("()Z")) {
                    pre |= call.name().equalsString("preCondition");
                    post |= call.name().equalsString("postCondition");
                }
            }
            return new Phases(pre, post);
        }
        throw new UsageError("Cannot find contract bytecode for " + method);
    }

    private static ClassModel classFileOf(Class<?> owner) {
        String resource = "/" + owner.getName().replace('.', '/') + ".class";
        try (var bytes = owner.getResourceAsStream(resource)) {
            if (bytes == null) {
                throw new UsageError("Cannot read contract bytecode for " + owner.getName());
            }
            return ClassFile.of().parse(bytes.readAllBytes());
        } catch (IOException failure) {
            var error = new UsageError("Cannot read contract bytecode for " + owner.getName());
            error.initCause(failure);
            throw error;
        }
    }

    static void evaluate(InvocationContext frame) {
        evaluate(frame, frame.binding, frame.checks.method(), frame.arguments);
    }

    static void evaluate(InvocationContext frame, Binding binding, Method method, Object... arguments) {
        try {
            Object contract = frame.contracts.get(binding.targetType());
            if (contract == null) {
                contract = newContract(binding, frame.target);
                frame.contracts.put(binding.targetType(), contract);
            }
            if (!method.trySetAccessible()) {
                throw new IllegalAccessException(method.toString());
            }
            method.invoke(contract, arguments);
        } catch (InvocationTargetException failure) {
            throw ContractViolationFactory.violation(frame.phase, failure.getCause());
        } catch (Throwable failure) {
            throw ContractViolationFactory.violation(frame.phase, failure);
        }
    }

    private static Object newContract(Binding binding, Object target) throws ReflectiveOperationException {
        Class<?> contractType = binding.contractType();
        Class<?> targetType = binding.targetType();
        Object[] arguments = new Object[0];
        Constructor<?> constructor;
        try {
            constructor = contractType.getDeclaredConstructor();
        } catch (NoSuchMethodException missingNoArgumentConstructor) {
            Class<?> outerType = targetType.getEnclosingClass();
            if (!targetType.isMemberClass() || Modifier.isStatic(targetType.getModifiers())) {
                throw missingNoArgumentConstructor;
            }
            constructor = contractType.getDeclaredConstructor(outerType);
            arguments = new Object[] { enclosingInstance(target, targetType, outerType) };
        }
        if (!constructor.trySetAccessible()) {
            throw new IllegalAccessException(constructor.toString());
        }
        Object contract = constructor.newInstance(arguments);
        for (Class<?> type = contractType; type != targetType; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Target.class)) {
                    continue;
                }
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())
                        || !field.getType().isInstance(target) || !field.trySetAccessible()) {
                    throw new UsageError("Cannot inject @Target field " + field);
                }
                field.set(contract, target);
            }
        }
        return contract;
    }

    private static Object enclosingInstance(Object target, Class<?> targetType, Class<?> outerType)
            throws ReflectiveOperationException {
        for (var field : targetType.getDeclaredFields()) {
            if (field.isSynthetic() && field.getName().startsWith("this$") && field.getType() == outerType) {
                if (!field.trySetAccessible()) {
                    throw new IllegalAccessException(field.toString());
                }
                return field.get(target);
            }
        }
        throw new UsageError("Cannot find retained enclosing instance for " + targetType.getName());
    }
}
