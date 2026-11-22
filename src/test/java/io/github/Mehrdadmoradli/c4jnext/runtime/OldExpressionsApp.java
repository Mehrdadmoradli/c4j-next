package io.github.Mehrdadmoradli.c4jnext.runtime;

import io.github.Mehrdadmoradli.c4jnext.ContractError;
import io.github.Mehrdadmoradli.c4jnext.UsageError;
import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;
import io.github.Mehrdadmoradli.c4jnext.annotations.Target;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.github.Mehrdadmoradli.c4jnext.Condition.*;

public final class OldExpressionsApp {
    private static final String TEXT = new String("entry");
    private static final Integer BOXED = 1000;

    @ContractReference(Checks.class)
    static class Sample {
        volatile int number = 20;
        boolean flag = true;
        byte small = 3;
        short shortValue = 40;
        char letter = 'a';
        long wide = 700L;
        float fraction = 1.5f;
        double precise = 2.5;
        Integer boxed = BOXED;
        String text = TEXT;
        Object nullable;
        List<String> mutable = new ArrayList<>(List.of("entry"));
        final IllegalStateException getterFailure = new IllegalStateException("getter failure");
        Other peer;
        int getterCalls;
        int bodies;

        public final int number() {
            return number;
        }

        public final int sideEffect() {
            return ++getterCalls;
        }

        public final IllegalStateException throwingGetter() {
            throw getterFailure;
        }

        public void change(List<String> previousList) {
            flag = false;
            small = 4;
            shortValue = 41;
            letter = 'b';
            number++;
            wide++;
            fraction++;
            precise++;
            boxed = 2000;
            text = "exit";
            nullable = new Object();
            mutable.add("mutated");
            mutable = List.of("replacement");
        }

        public int step(int depth) {
            number++;
            if (depth > 0) {
                return step(depth - 1);
            }
            if (peer != null) {
                peer.step(5);
            }
            return number;
        }

        public int nest(Other other) {
            return step(1) + other.step(5);
        }

        public void overlap(int expected, CountDownLatch entered, CountDownLatch release, boolean first) {
            number += 100;
            if (first) {
                entered.countDown();
                await(release);
            } else {
                release.countDown();
            }
        }

        public int repair(boolean read, int expected) {
            bodies++;
            boxed = 2000;
            return boxed;
        }

        public void unsupportedLocal(int input) { bodies++; }
        public void unsupportedArithmetic() { bodies++; }
        public void unsupportedGetter() { bodies++; }
        public void unsupportedThrowingGetter() { bodies++; }
        public void unsupportedReceiver() { bodies++; }
    }

    static class Checks extends Sample {
        @Target private Sample target;

        @Override
        public void change(List<String> previousList) {
            if (postCondition()) {
                assert old(target.flag);
                assert old(target.small) == 3;
                assert old(target.shortValue) == 40;
                assert old(target.letter) == 'a';
                assert old(target.number) == 20;
                assert old(target.wide) == 700L;
                assert old(target.fraction) == 1.5f;
                assert old(target.precise) == 2.5;
                assert old(target.boxed) == BOXED;
                assert old(target.text) == TEXT;
                assert old(target.nullable) == null;
                assert old(target.number()) == 20;
                assert old(
                        target.number
                ) == 20;
                assert old(1234) == 1234 && old("literal") == "literal" && old(null) == null;
                List<String> entry = old(target.mutable);
                assert entry == previousList && entry != target.mutable && entry.equals(List.of("entry", "mutated"));
            }
        }

        @Override
        public int step(int depth) {
            if (postCondition()) {
                assert result(int.class) == old(target.number) + depth + 1;
            }
            return 0;
        }

        @Override
        public int nest(Other other) {
            if (postCondition()) {
                assert target.number == old(target.number) + 2;
                assert result(int.class) == 34;
            }
            return 0;
        }

        @Override
        public void overlap(int expected, CountDownLatch entered, CountDownLatch release, boolean first) {
            if (postCondition()) {
                assert old(target.number) == expected;
            }
        }

        @Override
        public int repair(boolean read, int expected) {
            if (postCondition() && read) {
                assert old(target.boxed.intValue()) == expected;
            }
            return 0;
        }

        @Override
        public void unsupportedLocal(int input) {
            if (postCondition()) assert old(input) == input;
        }

        @Override
        public void unsupportedArithmetic() {
            if (postCondition()) assert old(target.number + 1) > 0;
        }

        @Override
        public void unsupportedGetter() {
            if (postCondition()) assert old(target.sideEffect()) > 0;
        }

        @Override
        public void unsupportedThrowingGetter() {
            if (postCondition()) assert old(target.throwingGetter()) != null;
        }

        @Override
        public void unsupportedReceiver() {
            if (postCondition()) assert old(this.number) > 0;
        }
    }

    @ContractReference(OtherChecks.class)
    static class Other {
        int number = 7;

        public int step(int amount) {
            return number += amount;
        }
    }

    static class OtherChecks extends Other {
        @Target private Other target;

        @Override
        public int step(int amount) {
            if (postCondition()) {
                assert result(int.class) == old(target.number) + amount;
            }
            return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        var values = new Sample();
        var originalList = values.mutable;
        values.change(originalList);
        assert values.number == 21 && originalList.equals(List.of("entry", "mutated"));
        inactive();

        var nested = new Sample();
        assert nested.nest(new Other()) == 34;
        nested.peer = new Other();
        assert nested.step(3) == 26 && nested.peer.number == 12;
        inactive();

        var separate = new Sample();
        separate.number = 40;
        overlap(new Sample(), separate, 40);
        var shared = new Sample();
        overlap(shared, shared, 120);
        assert shared.number == 220;

        var failure = new Sample();
        failure.boxed = null;
        var error = expect(ContractError.class, () -> failure.repair(true, 0));
        assert error.getMessage().equals("Contract Error in post-condition.") && failure.bodies == 1;
        assert error.getCause() instanceof ContractError;
        assert error.getCause().getMessage().contains("beginning of the method");
        assert error.getCause().getCause() instanceof NullPointerException;
        inactive();
        failure.boxed = null;
        assert failure.repair(false, 0) == 2000;
        assert failure.repair(true, 2000) == 2000;
        inactive();

        var unsupported = new Sample();
        rejects(() -> unsupported.unsupportedLocal(4));
        rejects(unsupported::unsupportedArithmetic);
        rejects(unsupported::unsupportedGetter);
        rejects(unsupported::unsupportedThrowingGetter);
        rejects(unsupported::unsupportedReceiver);
        assert unsupported.bodies == 0 && unsupported.getterCalls == 0;
        System.out.println("old expressions passed");
    }

    private static void overlap(Sample first, Sample second, int secondExpected) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> {
                first.overlap(20, entered, release, true);
                inactive();
            });
            await(entered);
            var b = pool.submit(() -> {
                second.overlap(secondExpected, entered, release, false);
                inactive();
            });
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assert latch.await(5, TimeUnit.SECONDS) : "timed out waiting for overlap";
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static void rejects(Runnable action) {
        var failure = expect(UsageError.class, action);
        assert failure.getMessage().contains("Unsupported old() expression #0");
        inactive();
    }

    private static void inactive() {
        assert ContractRuntime.current() == null;
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
