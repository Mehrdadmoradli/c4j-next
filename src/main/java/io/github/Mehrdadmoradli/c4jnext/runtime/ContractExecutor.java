package io.github.Mehrdadmoradli.c4jnext.runtime;

import io.github.Mehrdadmoradli.c4jnext.Condition;
import io.github.Mehrdadmoradli.c4jnext.UsageError;
import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;
import io.github.Mehrdadmoradli.c4jnext.annotations.Target;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class ContractExecutor {
    record Checks(Class<?> contractType, Method method, boolean pre, boolean post) {}

    private record Phases(boolean pre, boolean post) {}

    private static final Checks NONE = new Checks(null, null, false, false);
    private static final String CONDITION = Condition.class.descriptorString();
    private static final ClassValue<ConcurrentMap<Method, Checks>> CHECKS = new ClassValue<>() {
        @Override
        protected ConcurrentMap<Method, Checks> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private ContractExecutor() {}

    static Checks checksFor(Method targetMethod) {
        var checks = CHECKS.get(targetMethod.getDeclaringClass())
                .computeIfAbsent(targetMethod, ContractExecutor::resolve);
        return checks == NONE ? null : checks;
    }

    private static Checks resolve(Method targetMethod) {
        Class<?> targetType = targetMethod.getDeclaringClass();
        Class<?> contractType = targetType.getDeclaredAnnotation(ContractReference.class).value();
        if (contractType == targetType || !targetType.isAssignableFrom(contractType)) {
            throw new UsageError(contractType.getName() + " must extend " + targetType.getName());
        }

        // Walk contract classes only. A method inherited from the target is application code.
        for (Class<?> type = contractType; type != targetType; type = type.getSuperclass()) {
            for (Method candidate : type.getDeclaredMethods()) {
                if (!matches(targetMethod, candidate)) {
                    continue;
                }
                var phases = phasesOf(candidate);
                return phases.pre() || phases.post()
                        ? new Checks(contractType, candidate, phases.pre(), phases.post())
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
                && !candidate.isBridge() && !candidate.isSynthetic()
                && !Modifier.isAbstract(modifiers) && !Modifier.isNative(modifiers)
                && !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers);
    }

    private static Phases phasesOf(Method method) {
        Class<?> owner = method.getDeclaringClass();
        String resource = "/" + owner.getName().replace('.', '/') + ".class";
        String descriptor = MethodType.methodType(
                method.getReturnType(), method.getParameterTypes()).descriptorString();
        try (var bytes = owner.getResourceAsStream(resource)) {
            if (bytes == null) {
                throw new UsageError("Cannot read contract bytecode for " + method);
            }
            var model = ClassFile.of().parse(bytes.readAllBytes());
            for (var candidate : model.methods()) {
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
        } catch (IOException failure) {
            var error = new UsageError("Cannot read contract bytecode for " + method);
            error.initCause(failure);
            throw error;
        }
    }

    static void evaluate(InvocationContext frame) {
        try {
            if (frame.contract == null) {
                frame.contract = newContract(frame);
            }
            Method method = frame.checks.method();
            if (!method.trySetAccessible()) {
                throw new IllegalAccessException(method.toString());
            }
            method.invoke(frame.contract, frame.arguments);
        } catch (InvocationTargetException failure) {
            throw ContractViolationFactory.violation(frame.phase, failure.getCause());
        } catch (Throwable failure) {
            throw ContractViolationFactory.violation(frame.phase, failure);
        }
    }

    private static Object newContract(InvocationContext frame) throws ReflectiveOperationException {
        Class<?> contractType = frame.checks.contractType();
        var constructor = contractType.getDeclaredConstructor();
        if (!constructor.trySetAccessible()) {
            throw new IllegalAccessException(constructor.toString());
        }
        Object contract = constructor.newInstance();
        for (Class<?> type = contractType; type != frame.method.getDeclaringClass(); type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Target.class)) {
                    continue;
                }
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())
                        || !field.getType().isInstance(frame.target) || !field.trySetAccessible()) {
                    throw new UsageError("Cannot inject @Target field " + field);
                }
                field.set(contract, frame.target);
            }
        }
        return contract;
    }
}
