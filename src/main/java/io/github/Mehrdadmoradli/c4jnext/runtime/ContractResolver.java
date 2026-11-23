package io.github.Mehrdadmoradli.c4jnext.runtime;

import io.github.Mehrdadmoradli.c4jnext.UsageError;
import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

public final class ContractResolver {
    private static final ClassValue<List<Class<?>>> TYPES = new ClassValue<>() {
        @Override
        protected List<Class<?>> computeValue(Class<?> type) {
            return inspect(type, node -> get(node), Class::getSuperclass, node -> List.of(node.getInterfaces()));
        }
    };

    private static final ClassValue<List<ContractBinding>> BINDINGS = new ClassValue<>() {
        @Override
        protected List<ContractBinding> computeValue(Class<?> type) {
            var bindings = new ArrayList<ContractBinding>();
            var owners = new HashMap<Class<?>, Class<?>>();
            for (Class<?> target : TYPES.get(type)) {
                var reference = target.getDeclaredAnnotation(ContractReference.class);
                if (reference == null) {
                    continue;
                }
                Class<?> contract = contractFor(target, reference);
                // The contract class itself can be instantiated by the runtime or directly by callers.
                // It is never subject to the binding that names it.
                if (contract.isAssignableFrom(type)) {
                    continue;
                }
                Class<?> previous = owners.putIfAbsent(contract, target);
                if (previous != null) {
                    throw new UsageError("Ambiguous contract " + contract.getName() + " for "
                            + previous.getName() + " and " + target.getName());
                }
                bindings.add(new ContractBinding(target, contract, ContractExecutor.declaredInvariants(contract)));
            }
            return List.copyOf(bindings);
        }
    };

    private ContractResolver() {}

    static List<ContractBinding> bindingsFor(Class<?> boundary) {
        return BINDINGS.get(boundary);
    }

    static List<ContractBinding> introducedAt(Class<?> boundary) {
        var inherited = boundary.getSuperclass() == null
                ? List.<ContractBinding>of() : bindingsFor(boundary.getSuperclass());
        return bindingsFor(boundary).stream().filter(binding -> !inherited.contains(binding)).toList();
    }

    // Matches C4J's InvolvedTypeInspector: combine already ordered subtrees, then reverse at this node.
    public static <T> List<T> inspect(T type, Function<T, List<T>> inspectParent,
                                      Function<T, T> superclass, Function<T, Iterable<T>> interfaces) {
        var types = new LinkedHashSet<T>();
        types.add(type);
        T parent = superclass.apply(type);
        if (parent != null) {
            types.addAll(inspectParent.apply(parent));
        }
        for (T interfaze : interfaces.apply(type)) {
            types.addAll(inspectParent.apply(interfaze));
        }
        return List.copyOf(new ArrayList<>(types).reversed());
    }

    private static Class<?> contractFor(Class<?> target, ContractReference reference) {
        Class<?> contract;
        try {
            contract = reference.value();
        } catch (TypeNotPresentException failure) {
            var error = new UsageError("Cannot resolve the contract for " + target.getName());
            error.initCause(failure);
            throw error;
        }
        if (contract == target || contract.isInterface() || Modifier.isAbstract(contract.getModifiers())
                || !target.isAssignableFrom(contract)) {
            throw new UsageError("Contract " + contract.getName() + " must be a concrete class that "
                    + (target.isInterface() ? "implements " : "extends ") + target.getName());
        }
        return contract;
    }
}
