package io.github.Mehrdadmoradli.c4jnext.runtime;

import io.github.Mehrdadmoradli.c4jnext.ContractError;
import io.github.Mehrdadmoradli.c4jnext.annotations.ClassInvariant;
import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;
import io.github.Mehrdadmoradli.c4jnext.annotations.Target;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.Mehrdadmoradli.c4jnext.Condition.*;
import static io.github.Mehrdadmoradli.c4jnext.runtime.InvocationContext.Phase.*;

public final class InvocationIsolationApp {
    @ContractReference(CounterChecks.class)
    static class Counter {
        final int offset;
        final AtomicInteger concurrentBodies = new AtomicInteger();
        final List<InvocationContext> recursiveFrames = new ArrayList<>();
        Counter guardPeer;
        InvocationContext failedFrame;

        Counter(int offset) {
            this.offset = offset;
        }

        Counter() {
            this(0);
        }

        public int step(int amount) {
            return offset + amount;
        }

        public int nested(Counter inner) {
            var outer = body(this, "nested");
            int result = inner.step(3);
            assert body(this, "nested") == outer;
            var violation = expect(AssertionError.class, () -> inner.step(-1));
            assert violation.getMessage().equals("negative step (pre-condition)");
            assert body(this, "nested") == outer;
            return result + offset;
        }

        public int direct(int depth) {
            var outer = body(this, "direct");
            recursiveFrames.add(outer);
            int result = depth == 0 ? 0 : direct(depth - 1);
            assert body(this, "direct") == outer;
            return result + 1;
        }

        public int even(int depth) {
            var outer = body(this, "even");
            recursiveFrames.add(outer);
            int result = depth == 0 ? 0 : odd(depth - 1);
            assert body(this, "even") == outer;
            return result + 1;
        }

        public int odd(int depth) {
            var outer = body(this, "odd");
            recursiveFrames.add(outer);
            int result = depth == 0 ? 0 : even(depth - 1);
            assert body(this, "odd") == outer;
            return result + 1;
        }

        public int overlapping(int value, CyclicBarrier barrier) {
            body(this, "overlapping");
            concurrentBodies.incrementAndGet();
            return offset + value;
        }

        public int fails(RuntimeException failure) {
            failedFrame = body(this, "fails");
            failure.fillInStackTrace();
            throw failure;
        }

        public int badPost() {
            return 5;
        }
    }

    static class CounterChecks extends Counter {
        @Target private Counter target;
        private InvocationContext entered;

        @ClassInvariant
        void checkContext() {
            var frame = ContractRuntime.current();
            assert frame.target == target && frame.phase == INVARIANT;
            assert !preCondition() && !postCondition() && result() == null;
            checkSuppressedHelper(frame);
        }

        @Override
        public int step(int amount) {
            if (preCondition()) {
                before("step");
                assert amount >= 0 : "negative step";
            }
            if (postCondition()) {
                after("step", target.offset + amount);
            }
            return 0;
        }

        @Override
        public int nested(Counter inner) {
            if (preCondition()) before("nested");
            if (postCondition()) after("nested", inner.offset + 3 + target.offset);
            return 0;
        }

        @Override
        public int direct(int depth) {
            if (preCondition()) before("direct");
            if (postCondition()) after("direct", depth + 1);
            return 0;
        }

        @Override
        public int even(int depth) {
            if (preCondition()) before("even");
            if (postCondition()) after("even", depth + 1);
            return 0;
        }

        @Override
        public int odd(int depth) {
            if (preCondition()) before("odd");
            if (postCondition()) after("odd", depth + 1);
            return 0;
        }

        @Override
        public int overlapping(int value, CyclicBarrier barrier) {
            if (preCondition()) before("overlapping");
            if (postCondition()) {
                await(barrier);
                after("overlapping", target.offset + value);
            }
            return 0;
        }

        @Override
        public int fails(RuntimeException failure) {
            if (postCondition()) {
                throw new AssertionError("postcondition observed a result on failure: " + result());
            }
            return 0;
        }

        @Override
        public int badPost() {
            if (postCondition()) {
                assert result(int.class) == 6 : "bad post";
            }
            return 0;
        }

        private void before(String method) {
            entered = checkFrame(target, method);
            assert entered.phase == PRE && result() == null;
            checkSuppressedHelper(entered);
        }

        private void after(String method, int expected) {
            var current = checkFrame(target, method);
            assert current == entered : "lost invocation or contract instance";
            assert current.phase == POST && result(int.class) == expected;
            assert current.throwable == null;
            checkSuppressedHelper(current);
        }

        private void checkSuppressedHelper(InvocationContext frame) {
            if (target.guardPeer != null) {
                var phase = frame.phase;
                Object value = result();
                assert target.guardPeer.step(-1) == target.guardPeer.offset - 1;
                assert ContractRuntime.current() == frame && frame.phase == phase && result() == value;
            }
        }
    }

    @ContractReference(UnconstructableChecks.class)
    static class Unconstructable {
        public void before() {
            throw new AssertionError("body ran after contract construction failed");
        }

        public int after() {
            return 1;
        }
    }

    static class UnconstructableChecks extends Unconstructable {
        UnconstructableChecks(int ignored) {}

        @Override
        public void before() {
            if (preCondition()) {
                throw new AssertionError("unreachable");
            }
        }

        @Override
        public int after() {
            if (postCondition()) {
                throw new AssertionError("unreachable");
            }
            return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        var receiver = new Counter(10);
        var other = new Counter(20);
        assert receiver.nested(other) == 33;
        inactive();
        assert receiver.direct(4) == 5;
        checkRecursiveFrames(receiver, 5);
        assert receiver.even(5) == 6;
        checkRecursiveFrames(receiver, 6);
        inactive();

        receiver.guardPeer = other;
        assert receiver.step(2) == 12;
        inactive();
        expect(AssertionError.class, () -> other.step(-1));
        inactive();
        expect(AssertionError.class, () -> other.step(-1));
        inactive();
        var post = expect(AssertionError.class, receiver::badPost);
        assert post.getMessage().equals("bad post (post-condition)");
        inactive();

        var unavailable = new Unconstructable();
        reflectionFailure(unavailable::before, "pre-condition");
        reflectionFailure(unavailable::after, "post-condition");

        var original = new IllegalStateException("application failure");
        var suppressed = new IllegalArgumentException("suppressed");
        original.addSuppressed(suppressed);
        var actual = expect(IllegalStateException.class, () -> receiver.fails(original));
        assert actual == original && actual.getMessage().equals("application failure");
        assert actual.getSuppressed().length == 1 && actual.getSuppressed()[0] == suppressed;
        assert actual.getStackTrace()[0].getClassName().equals(Counter.class.getName());
        assert actual.getStackTrace()[0].getMethodName().equals("fails");
        assert receiver.failedFrame.throwable == original && receiver.failedFrame.result == null;
        inactive();
        assert receiver.step(1) == 11;
        inactive();

        overlap(new Counter(10), new Counter(20));
        var shared = new Counter(50);
        overlap(shared, shared);
        assert shared.concurrentBodies.get() == 2;
        System.out.println("invocation isolation passed");
    }

    private static void overlap(Counter first, Counter second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var a = threads.submit(() -> overlapping(first, 101, barrier));
            var b = threads.submit(() -> overlapping(second, 202, barrier));
            assert a.get(10, TimeUnit.SECONDS) == first.offset + 101;
            assert b.get(10, TimeUnit.SECONDS) == second.offset + 202;
        }
    }

    private static void checkRecursiveFrames(Counter receiver, int count) {
        var frames = receiver.recursiveFrames;
        assert frames.size() == count && new HashSet<>(frames).size() == count;
        for (int i = 0; i < count; i++) {
            assert frames.get(i).result.equals(count - i);
        }
        frames.clear();
    }

    private static int overlapping(Counter target, int value, CyclicBarrier barrier) {
        int returned = target.overlapping(value, barrier);
        inactive();
        return returned;
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static void reflectionFailure(Runnable action, String phase) {
        var failure = expect(ContractError.class, action);
        assert failure.getMessage().equals("Contract Error in " + phase + ".");
        assert failure.getCause() instanceof NoSuchMethodException;
        inactive();
    }

    private static InvocationContext body(Counter receiver, String method) {
        var frame = checkFrame(receiver, method);
        assert frame.phase == BODY && frame.result == null && frame.throwable == null;
        assert !preCondition() && !postCondition() && result() == null;
        return frame;
    }

    private static InvocationContext checkFrame(Counter receiver, String method) {
        var frame = ContractRuntime.current();
        assert frame != null && frame.target == receiver;
        assert frame.method.getDeclaringClass() == Counter.class && frame.method.getName().equals(method);
        return frame;
    }

    private static void inactive() {
        assert ContractRuntime.current() == null;
        assert !preCondition() && !postCondition() && result() == null;
    }

    private static <T extends Throwable> T expect(Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                return type.cast(failure);
            }
            throw failure;
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
