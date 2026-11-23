package io.github.Mehrdadmoradli.c4jnext.runtime;

import java.lang.reflect.Method;
import java.util.HashMap;
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
    final Object[] arguments;
    final MethodContractPlan plan;
    final boolean constructor;
    final Map<Class<?>, Object> contracts = new HashMap<>();
    Phase phase = Phase.BODY;
    OldValueStore oldValues;
    Object result;
    Throwable throwable;

    InvocationContext(Object target, Method method, Object[] arguments, MethodContractPlan plan, boolean constructor) {
        this.target = target;
        this.method = method;
        this.arguments = arguments;
        this.plan = plan;
        this.constructor = constructor;
    }
}
