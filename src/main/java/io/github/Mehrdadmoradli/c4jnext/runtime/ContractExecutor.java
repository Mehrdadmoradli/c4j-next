package io.github.Mehrdadmoradli.c4jnext.runtime;

import io.github.Mehrdadmoradli.c4jnext.Condition;
import io.github.Mehrdadmoradli.c4jnext.UsageError;
import io.github.Mehrdadmoradli.c4jnext.annotations.ClassInvariant;
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
    private record Boundary(Method method, boolean delegate) {}
    private record Phases(boolean pre, boolean post, int oldSites) {}

    private static final String CONDITION = Condition.class.descriptorString();
    private static final ClassValue<ConcurrentMap<Boundary, MethodContractPlan>> PLANS = new ClassValue<>() {
        @Override
        protected ConcurrentMap<Boundary, MethodContractPlan> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final ClassValue<MethodContractPlan> CONSTRUCTOR_PLANS = new ClassValue<>() {
        @Override
        protected MethodContractPlan computeValue(Class<?> type) {
            return new MethodContractPlan(List.of(), withInvariants(ContractResolver.bindingsFor(type)));
        }
    };

    private ContractExecutor() {}

    static MethodContractPlan planFor(Method method, boolean delegate) {
        return PLANS.get(method.getDeclaringClass()).computeIfAbsent(new Boundary(method, delegate),
                ContractExecutor::resolve);
    }

    static MethodContractPlan constructorPlanFor(Class<?> type) {
        return CONSTRUCTOR_PLANS.get(type);
    }

    private static MethodContractPlan resolve(Boundary boundary) {
        Class<?> owner = boundary.method().getDeclaringClass();
        var bindings = boundary.delegate() ? ContractResolver.introducedAt(owner)
                : ContractResolver.bindingsFor(owner);
        var checks = new ArrayList<MethodContractPlan.Check>();
        for (var binding : bindings) {
            Method candidate = contractMethod(boundary.method(), binding);
            if (candidate == null) {
                continue;
            }
            var phases = phasesOf(candidate);
            var captures = OldValueStore.prepare(candidate, binding.targetType(), phases.post(), phases.oldSites());
            boolean pre = phases.pre() && !strengthensInheritedPrecondition(binding, candidate);
            if (pre || phases.post()) {
                checks.add(new MethodContractPlan.Check(binding, candidate, pre, phases.post(), captures));
            }
        }
        return new MethodContractPlan(checks, withInvariants(bindings));
    }

    private static boolean strengthensInheritedPrecondition(ContractBinding binding, Method method) {
        // C4J's default "undefined" policy tests the declarations of other contracted involved types.
        for (var ancestor : ContractResolver.bindingsFor(binding.targetType())) {
            if (ancestor.targetType() == binding.targetType()) {
                continue;
            }
            try {
                ancestor.targetType().getDeclaredMethod(method.getName(), method.getParameterTypes());
                return true;
            } catch (NoSuchMethodException absent) {
                // This contracted ancestor does not declare the signature.
            }
        }
        return false;
    }

    private static List<ContractBinding> withInvariants(List<ContractBinding> bindings) {
        return bindings.stream().filter(binding -> !binding.invariants().isEmpty()).toList();
    }

    private static Method contractMethod(Method targetMethod, ContractBinding binding) {
        // Walk contract classes only. A method inherited from the target is application code.
        for (Class<?> type = binding.contractType(); type != Object.class && type != binding.targetType();
             type = type.getSuperclass()) {
            for (Method candidate : type.getDeclaredMethods()) {
                if (matches(targetMethod, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean matches(Method target, Method candidate) {
        int modifiers = candidate.getModifiers();
        return candidate.getName().equals(target.getName())
                && Arrays.equals(candidate.getParameterTypes(), target.getParameterTypes())
                && (target.getReturnType().isAssignableFrom(candidate.getReturnType())
                    || candidate.getReturnType().isAssignableFrom(target.getReturnType()))
                && !candidate.isAnnotationPresent(ClassInvariant.class)
                && !candidate.isBridge() && !candidate.isSynthetic()
                && !Modifier.isAbstract(modifiers) && !Modifier.isNative(modifiers)
                && !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers);
    }

    static List<Method> declaredInvariants(Class<?> contract) {
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
                for (var instruction : method.code().orElseThrow()) {
                    if (instruction instanceof InvokeInstruction call && isOld(call)) {
                        throw new UsageError("old() is supported in method postconditions only: " + invariant);
                    }
                }
                ordered.add(invariant);
            }
        }
        return List.copyOf(ordered);
    }

    static String descriptor(Method method) {
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
            int oldSites = 0;
            for (var element : candidate.code().orElseThrow()) {
                if (element instanceof InvokeInstruction call
                        && call.owner().asSymbol().descriptorString().equals(CONDITION)) {
                    if (call.type().equalsString("()Z")) {
                        pre |= call.name().equalsString("preCondition");
                        post |= call.name().equalsString("postCondition");
                    } else if (isOld(call)) {
                        oldSites++;
                    }
                }
            }
            return new Phases(pre, post, oldSites);
        }
        throw new UsageError("Cannot find contract bytecode for " + method);
    }

    private static boolean isOld(InvokeInstruction call) {
        return call.owner().asSymbol().descriptorString().equals(CONDITION)
                && call.name().equalsString("old") && call.type().equalsString(OldExpressionTransformer.OLD_DESCRIPTOR);
    }

    static ClassModel classFileOf(Class<?> owner) {
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

    static void evaluatePreconditions(InvocationContext frame) {
        boolean passed = false;
        Throwable lastFailure = null;
        for (var check : frame.plan.checks()) {
            // C4J counts an entry dependency such as old() as an applicable precondition.
            if (!check.pre() && check.captures().isEmpty()) {
                continue;
            }
            Throwable failure = null;
            try {
                if (check.pre()) {
                    invoke(frame, check.binding(), check.method(), frame.arguments);
                } else {
                    contractInstance(frame, check.binding());
                }
            } catch (Throwable cause) {
                failure = cause;
            }
            if (failure == null) {
                if (lastFailure != null) {
                    throw ContractViolationFactory.lspViolation();
                }
                passed = true;
            } else {
                if (passed) {
                    throw ContractViolationFactory.lspViolation();
                }
                lastFailure = failure;
            }
        }
        if (lastFailure != null) {
            throw ContractViolationFactory.violation(frame.phase, lastFailure);
        }
    }

    static void evaluate(InvocationContext frame, ContractBinding binding, Method method, Object... arguments) {
        try {
            invoke(frame, binding, method, arguments);
        } catch (Throwable failure) {
            throw ContractViolationFactory.violation(frame.phase, failure);
        }
    }

    private static void invoke(InvocationContext frame, ContractBinding binding, Method method, Object[] arguments)
            throws Throwable {
        Object contract = contractInstance(frame, binding);
        try {
            if (!method.trySetAccessible()) {
                throw new IllegalAccessException(method.toString());
            }
            method.invoke(contract, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static Object contractInstance(InvocationContext frame, ContractBinding binding) throws Throwable {
        Object contract = frame.contracts.get(binding.targetType());
        if (contract == null) {
            try {
                contract = newContract(binding, frame.target);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
            frame.contracts.put(binding.targetType(), contract);
        }
        return contract;
    }

    private static Object newContract(ContractBinding binding, Object target) throws ReflectiveOperationException {
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
        for (Class<?> type = contractType; type != Object.class && type != targetType; type = type.getSuperclass()) {
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
