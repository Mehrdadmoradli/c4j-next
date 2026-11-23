package io.github.Mehrdadmoradli.c4jnext.runtime;

import io.github.Mehrdadmoradli.c4jnext.UsageError;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

import static io.github.Mehrdadmoradli.c4jnext.runtime.InvocationContext.Phase.*;

public final class ContractRuntime {
    private static final ThreadLocal<Deque<InvocationContext>> INVOCATIONS = new ThreadLocal<>();

    private ContractRuntime() {}

    public static InvocationContext enter(Object target, Method method, Object[] arguments, boolean delegate) {
        // Calls from contract code run their bodies under the active evaluation.
        // Calls from application bodies enter normally and get their own frame.
        if (evaluating()) {
            return null;
        }
        var plan = ContractExecutor.planFor(method, delegate);
        if (plan.checks().isEmpty() && plan.invariants().isEmpty()) {
            return null;
        }
        var frame = new InvocationContext(target, method, arguments, plan, false);
        push(frame);
        boolean entered = false;
        try {
            checkInvariants(frame);
            frame.phase = PRE;
            ContractExecutor.evaluatePreconditions(frame);
            frame.phase = CAPTURE;
            for (var check : plan.checks()) {
                if (!check.captures().isEmpty()) {
                    if (frame.oldValues == null) {
                        frame.oldValues = new OldValueStore();
                    }
                    frame.oldValues.save(check.captures(), target);
                }
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
            frame.throwable = thrown;
            if (frame.throwable != null) {
                return;
            }
            frame.result = result;
            frame.phase = POST;
            for (var check : frame.plan.checks()) {
                if (check.post()) {
                    ContractExecutor.evaluate(frame, check.binding(), check.method(), frame.arguments);
                }
            }
            checkInvariants(frame);
        } finally {
            pop();
        }
    }

    static boolean evaluating() {
        var frame = current();
        return frame != null && frame.phase != BODY;
    }

    static void checkConstructed(Object target) {
        var plan = ContractExecutor.constructorPlanFor(target.getClass());
        if (plan.invariants().isEmpty()) {
            return;
        }
        var frame = new InvocationContext(target, null, new Object[0], plan, true);
        push(frame);
        try {
            checkInvariants(frame);
        } finally {
            pop();
        }
    }

    private static void checkInvariants(InvocationContext frame) {
        if (frame.plan.invariants().isEmpty()
                || (!frame.constructor && ConstructionRuntime.isConstructing(frame.target))) {
            return;
        }
        frame.phase = INVARIANT;
        for (var binding : frame.plan.invariants()) {
            for (var method : binding.invariants()) {
                ContractExecutor.evaluate(frame, binding, method);
            }
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

    /** Called by rewritten old() sites in contract bytecode. */
    public static Object oldValue(Class<?> owner, String site) {
        var frame = current();
        if (frame == null || frame.phase != POST || frame.oldValues == null) {
            throw new UsageError("old() is only available in an active contract postcondition: "
                    + owner.getName() + "." + site);
        }
        return frame.oldValues.get(owner, site);
    }

    static InvocationContext current() {
        var stack = INVOCATIONS.get();
        return stack == null ? null : stack.peek();
    }

    private static void push(InvocationContext frame) {
        var stack = INVOCATIONS.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            INVOCATIONS.set(stack);
        }
        stack.push(frame);
    }

    private static void pop() {
        var stack = INVOCATIONS.get();
        stack.pop();
        if (stack.isEmpty()) {
            INVOCATIONS.remove();
        }
    }
}
