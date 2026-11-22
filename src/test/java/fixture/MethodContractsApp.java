package fixture;

import io.github.Mehrdadmoradli.c4jnext.ContractError;
import io.github.Mehrdadmoradli.c4jnext.UsageError;
import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;
import io.github.Mehrdadmoradli.c4jnext.annotations.Target;

import java.util.Arrays;

import static io.github.Mehrdadmoradli.c4jnext.Condition.*;

public class MethodContractsApp {
    private static final Object TOKEN = new Object();
    private static final RuntimeException CAUSE = new IllegalStateException("contract cause");
    private static final UsageError USAGE = new UsageError("bad contract");
    private static final RuntimeException APPLICATION = new IllegalArgumentException("application failure");

    @ContractReference(Checks.class)
    static class Subject {
        int mutations;

        public int change(int amount) {
            return mutations += amount;
        }

        public int change(String amount) {
            return Integer.parseInt(amount);
        }

        public boolean booleanValue(boolean expected) { return true; }
        public byte byteValue(byte expected) { return (byte) 7; }
        public short shortValue(short expected) { return (short) 7; }
        public char charValue(char expected) { return 'a'; }
        public int intValue(int expected) { return 7; }
        public long longValue(long expected) { return 7L; }
        public float floatValue(float expected) { return 7.5f; }
        public double doubleValue(double expected) { return 7.5; }

        public Object reference(Object value, Object expected) { return value; }
        public void mutate(int expected) { mutations++; }
        public void brokenCheck() {}
        public void badUsage() {}
        public int applicationFailure() { throw APPLICATION; }
    }

    static class BaseChecks extends Subject {
        @Target private Subject receiver;

        @Override
        public int change(int amount) {
            if (preCondition() && amount < 0) {
                throw new AssertionError("negative amount", CAUSE);
            }
            if (postCondition()) {
                assert result(int.class) == receiver.mutations : "wrong balance";
            }
            return 0;
        }
    }

    static class Checks extends BaseChecks {
        @Target private Subject receiver;

        @Override
        public boolean booleanValue(boolean expected) {
            if (postCondition()) {
                assert result(Boolean.class) == expected : "boolean result";
            }
            return expected;
        }

        @Override
        public byte byteValue(byte expected) {
            if (postCondition()) {
                assert result(Byte.class) == expected : "byte result";
            }
            return expected;
        }

        @Override
        public short shortValue(short expected) {
            if (postCondition()) {
                assert result(Short.class) == expected : "short result";
            }
            return expected;
        }

        @Override
        public char charValue(char expected) {
            if (postCondition()) {
                assert result(Character.class) == expected : "char result";
            }
            return expected;
        }

        @Override
        public int intValue(int expected) {
            if (postCondition()) {
                assert result(Integer.class) == expected : "int result";
            }
            return expected;
        }

        @Override
        public long longValue(long expected) {
            if (postCondition()) {
                assert result(Long.class) == expected : "long result";
            }
            return expected;
        }

        @Override
        public float floatValue(float expected) {
            if (postCondition()) {
                assert result(Float.class) == expected : "float result";
            }
            return expected;
        }

        @Override
        public double doubleValue(double expected) {
            if (postCondition()) {
                assert result(Double.class) == expected : "double result";
            }
            return expected;
        }

        @Override
        public Object reference(Object value, Object expected) {
            if (postCondition()) {
                assert result() == expected : "reference result";
            }
            return null;
        }

        @Override
        public void mutate(int expected) {
            if (postCondition()) {
                assert result() == null && receiver.mutations == expected : "void result";
            }
        }

        @Override
        public void brokenCheck() {
            if (postCondition()) {
                throw CAUSE;
            }
        }

        @Override
        public void badUsage() {
            if (preCondition()) {
                throw new IllegalStateException(USAGE);
            }
        }

        @Override
        public int applicationFailure() {
            if (postCondition()) {
                throw new AssertionError("postcondition ran for an application exception");
            }
            return 0;
        }
    }

    @ContractReference(MissingConstructorChecks.class)
    static class MissingConstructorSubject {
        public void check() {}
    }

    static class MissingConstructorChecks extends MissingConstructorSubject {
        MissingConstructorChecks(int ignored) {}

        @Override
        public void check() {
            if (preCondition()) {
                throw new AssertionError("unreachable contract");
            }
        }
    }

    public static void main(String[] args) {
        Subject subject = new Subject();
        for (int attempt = 0; attempt < 2; attempt++) {
            var violation = expect(AssertionError.class, () -> subject.change(-1));
            assert violation.getMessage().equals("negative amount (pre-condition)");
            assert violation.getCause() == CAUSE;
            assert hasFrame(violation, BaseChecks.class, "change");
            assert hasFrame(violation, Subject.class, "change");
            assert subject.mutations == 0;
            assertInactive();
        }
        assert subject.change(3) == 3 && subject.mutations == 3;
        assert subject.change("-4") == -4;

        assert subject.booleanValue(true) == true;
        rejected(() -> subject.booleanValue(false), "boolean result");
        assert subject.byteValue((byte) 7) == (byte) 7;
        rejected(() -> subject.byteValue((byte) 8), "byte result");
        assert subject.shortValue((short) 7) == (short) 7;
        rejected(() -> subject.shortValue((short) 8), "short result");
        assert subject.charValue('a') == 'a';
        rejected(() -> subject.charValue('b'), "char result");
        assert subject.intValue(7) == 7;
        rejected(() -> subject.intValue(8), "int result");
        assert subject.longValue(7L) == 7L;
        rejected(() -> subject.longValue(8L), "long result");
        assert subject.floatValue(7.5f) == 7.5f;
        rejected(() -> subject.floatValue(8.5f), "float result");
        assert subject.doubleValue(7.5) == 7.5;
        rejected(() -> subject.doubleValue(8.5), "double result");

        assert subject.reference(TOKEN, TOKEN) == TOKEN;
        assert subject.reference(null, null) == null;
        rejected(() -> subject.reference(TOKEN, null), "reference result");
        rejected(() -> subject.reference(null, TOKEN), "reference result");
        subject.mutate(4);
        rejected(() -> subject.mutate(4), "void result");
        assert subject.mutations == 5;

        var broken = expect(ContractError.class, subject::brokenCheck);
        assert broken.getMessage().equals("Contract Error in post-condition.");
        assert broken.getCause() == CAUSE;
        assertInactive();
        assert expect(UsageError.class, subject::badUsage) == USAGE;
        assertInactive();
        assert expect(RuntimeException.class, subject::applicationFailure) == APPLICATION;
        assertInactive();
        var reflection = expect(ContractError.class, new MissingConstructorSubject()::check);
        assert reflection.getMessage().equals("Contract Error in pre-condition.");
        assert reflection.getCause() instanceof NoSuchMethodException;
        assertInactive();
        assert subject.change(1) == 6;
        System.out.println("method contracts passed");
    }

    private static void rejected(Runnable call, String message) {
        var violation = expect(AssertionError.class, call);
        assert violation.getMessage().equals(message + " (post-condition)") : violation;
        assert hasFrame(violation, Checks.class, violation.getStackTrace()[0].getMethodName());
        assertInactive();
    }

    private static boolean hasFrame(Throwable error, Class<?> type, String method) {
        return Arrays.stream(error.getStackTrace()).anyMatch(frame ->
                frame.getClassName().equals(type.getName()) && frame.getMethodName().equals(method));
    }

    private static void assertInactive() {
        assert !preCondition() && !postCondition() && result() == null;
    }

    private static <T extends Throwable> T expect(Class<T> type, Runnable call) {
        try {
            call.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) {
                return type.cast(error);
            }
            throw error;
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
