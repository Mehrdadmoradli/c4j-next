package io.github.Mehrdadmoradli.c4jnext.runtime;

import java.lang.reflect.Method;

public final class InvocationContext {
    enum Phase {
        PRE("pre-condition"), BODY(""), POST("post-condition");

        final String label;

        Phase(String label) {
            this.label = label;
        }
    }

    final Object target;
    final Method method;
    final Object[] arguments;
    final ContractExecutor.Checks checks;
    Phase phase;
    Object contract;
    Object result;

    InvocationContext(Object target, Method method, Object[] arguments, ContractExecutor.Checks checks) {
        this.target = target;
        this.method = method;
        this.arguments = arguments;
        this.checks = checks;
    }
}
