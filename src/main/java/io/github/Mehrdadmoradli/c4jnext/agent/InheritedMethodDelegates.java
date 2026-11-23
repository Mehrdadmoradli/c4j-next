package io.github.Mehrdadmoradli.c4jnext.agent;

import io.github.Mehrdadmoradli.c4jnext.annotations.ClassInvariant;
import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;
import io.github.Mehrdadmoradli.c4jnext.runtime.ContractResolver;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.jar.asm.Opcodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

final class InheritedMethodDelegates {
    private InheritedMethodDelegates() {}

    static DynamicType.Builder<?> addTo(DynamicType.Builder<?> builder, TypeDescription type) {
        var inheritedTypes = type.getSuperClass() == null ? List.<TypeDescription>of()
                : orderedTypes(type.getSuperClass().asErasure());
        var introduced = new ArrayList<MethodDescription.InDefinedShape>();
        for (var target : orderedTypes(type)) {
            if (inheritedTypes.contains(target)
                    || !target.getDeclaredAnnotations().isAnnotationPresent(ContractReference.class)) {
                continue;
            }
            var reference = target.getDeclaredAnnotations().ofType(ContractReference.class);
            var value = reference.getValue("value");
            if (!value.getState().isResolved()) {
                continue;
            }
            TypeDescription contract = value.resolve(TypeDescription.class);
            if (contract.isInterface() || contract.isAbstract()
                    || contract.isAssignableFrom(type) || !target.isAssignableFrom(contract)) {
                continue;
            }
            for (var owner = contract; owner != null && !owner.equals(target) && !owner.represents(Object.class);
                 owner = owner.getSuperClass() == null ? null : owner.getSuperClass().asErasure()) {
                for (var check : owner.getDeclaredMethods()) {
                    if (check.isMethod() && eligible(check)
                            && !check.getDeclaredAnnotations().isAnnotationPresent(ClassInvariant.class)) {
                        introduced.add(check);
                    }
                }
            }
        }
        if (introduced.isEmpty()) {
            return builder;
        }

        var advice = Advice.withCustomMapping().bind(MethodAdvice.Delegate.class, true).to(MethodAdvice.class);
        var graph = MethodGraph.Compiler.Default.forJavaHierarchy().compile((TypeDefinition) type);
        for (var node : graph.listNodes()) {
            if (!node.getSort().isResolved()) {
                continue;
            }
            var method = node.getRepresentative().asDefined();
            var owner = method.getDeclaringType();
            if (owner.equals(type) || owner.isInterface() || method.isFinal() || !eligible(method)
                    || !method.isVisibleTo(type)
                    || introduced.stream().noneMatch(check -> matches(method, check))) {
                continue;
            }
            int modifiers = Opcodes.ACC_SYNTHETIC
                    | (method.getModifiers() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_VARARGS));
            builder = builder.defineMethod(method.getName(), method.getReturnType().asErasure(), modifiers)
                    .withParameters(method.getParameters().asTypeList().asErasures())
                    .intercept(advice.wrap(SuperMethodCall.INSTANCE));
        }
        return builder;
    }

    private static boolean eligible(MethodDescription method) {
        return !method.isStatic() && !method.isPrivate() && !method.isAbstract() && !method.isNative()
                && !method.isBridge() && !method.isSynthetic();
    }

    private static boolean matches(MethodDescription left, MethodDescription right) {
        var a = left.getReturnType().asErasure();
        var b = right.getReturnType().asErasure();
        return left.getName().equals(right.getName())
                && left.getParameters().asTypeList().asErasures().equals(right.getParameters().asTypeList().asErasures())
                && (a.isAssignableFrom(b) || b.isAssignableFrom(a));
    }

    private static List<TypeDescription> orderedTypes(TypeDescription type) {
        return orderedTypes(type, new HashMap<>());
    }

    private static List<TypeDescription> orderedTypes(TypeDescription type,
                                                       HashMap<TypeDescription, List<TypeDescription>> cache) {
        // Recursive calls can add other keys, so avoid computeIfAbsent on the mutable map.
        var existing = cache.get(type);
        if (existing != null) {
            return existing;
        }
        var ordered = ContractResolver.inspect(type, parent -> orderedTypes(parent, cache),
                node -> node.getSuperClass() == null ? null : node.getSuperClass().asErasure(),
                node -> node.getInterfaces().asErasures());
        cache.put(type, ordered);
        return ordered;
    }
}
