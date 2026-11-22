package io.github.Mehrdadmoradli.c4jnext.runtime;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InvocationContext {
    enum Phase {
        PRE("pre-condition"), CAPTURE("old"), BODY(""), POST("post-condition"), INVARIANT("class-invariant");

        final String label;

        Phase(String label) {
            this.label = label;
        }
    }

    final Object target;
    final Method method;
    final ContractExecutor.Binding binding;
    final Object[] arguments;
    final ContractExecutor.Checks checks;
    final List<ContractExecutor.Binding> invariants;
    final boolean constructor;
    final Map<Class<?>, Object> contracts = new HashMap<>();
    Phase phase = Phase.BODY;
    OldValueStore oldValues;
    Object result;
    Throwable throwable;

    InvocationContext(Object target, Method method, ContractExecutor.Binding binding, Object[] arguments,
                      ContractExecutor.Checks checks, List<ContractExecutor.Binding> invariants, boolean constructor) {
        this.target = target;
        this.method = method;
        this.binding = binding;
        this.arguments = arguments;
        this.checks = checks;
        this.invariants = invariants;
        this.constructor = constructor;
    }
}
