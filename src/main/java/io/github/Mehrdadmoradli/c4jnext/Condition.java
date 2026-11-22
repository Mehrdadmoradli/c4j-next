package io.github.Mehrdadmoradli.c4jnext;

import io.github.Mehrdadmoradli.c4jnext.runtime.ContractRuntime;

/** Entry points used by contract methods. */
public class Condition {
    public static boolean preCondition() {
        return ContractRuntime.preCondition();
    }

    public static boolean postCondition() {
        return ContractRuntime.postCondition();
    }

    @SuppressWarnings("unchecked")
    public static <T> T result() {
        return (T) ContractRuntime.result();
    }

    public static <T> T result(Class<T> returnType) {
        return result();
    }

    /** Returns the entry value of a supported expression in an instrumented postcondition. */
    public static <T> T old(T expression) {
        throw new UsageError("old() requires a supported expression directly in an instrumented postcondition");
    }
}
