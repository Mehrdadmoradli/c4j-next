package io.github.Mehrdadmoradli.c4jnext.runtime;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

import static io.github.Mehrdadmoradli.c4jnext.runtime.InvocationContext.Phase.*;

public final class ContractRuntime {
    private static final ThreadLocal<Deque<InvocationContext>> INVOCATIONS = new ThreadLocal<>();

    private ContractRuntime() {}

    public static InvocationContext enter(Object target, Method method, Object[] arguments) {
        var outer = current();
        if (outer != null && outer.phase != BODY) {
            return null;
        }
        var checks = ContractExecutor.checksFor(method);
        if (checks == null) {
            return null;
        }
        var frame = new InvocationContext(target, method, arguments, checks);
        var stack = INVOCATIONS.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            INVOCATIONS.set(stack);
        }
        stack.push(frame);
        boolean entered = false;
        try {
            if (checks.pre()) {
                frame.phase = PRE;
                ContractExecutor.evaluate(frame);
            }
            frame.phase = BODY;
            entered = true;
            return frame;
        } finally {
            if (!entered) {
                pop();
            }
        }
    }

    public static void exit(InvocationContext frame, Object result, Throwable thrown) {
        if (frame == null) {
            return;
        }
        try {
            if (thrown == null && frame.checks.post()) {
                frame.phase = POST;
                frame.result = result;
                ContractExecutor.evaluate(frame);
            }
        } finally {
            pop();
        }
    }

    public static boolean preCondition() {
        var frame = current();
        return frame != null && frame.phase == PRE;
    }

    public static boolean postCondition() {
        var frame = current();
        return frame != null && frame.phase == POST;
    }

    public static Object result() {
        var frame = current();
        return frame != null && frame.phase == POST ? frame.result : null;
    }

    private static InvocationContext current() {
        var stack = INVOCATIONS.get();
        return stack == null ? null : stack.peek();
    }

    private static void pop() {
        var stack = INVOCATIONS.get();
        stack.pop();
        if (stack.isEmpty()) {
            INVOCATIONS.remove();
        }
    }
}
