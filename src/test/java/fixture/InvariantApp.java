package fixture;

import io.github.Mehrdadmoradli.c4jnext.annotations.ClassInvariant;
import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;
import io.github.Mehrdadmoradli.c4jnext.annotations.Target;

import java.util.ArrayList;
import java.util.List;

import static io.github.Mehrdadmoradli.c4jnext.Condition.*;

public class InvariantApp {
    private static final List<String> EVENTS = new ArrayList<>();
    private static final RuntimeException APPLICATION_FAILURE = new IllegalStateException("application failure");

    @ContractReference(AccountChecks.class)
    static class Account {
        int balance;
        boolean ready;

        Account() {
            this(0);
            ready = true;
        }

        Account(int amount) {
            this(amount, false);
        }

        Account(int amount, boolean ready) {
            // This helper runs before the object is ready.
            getBalance();
            this.balance = amount;
            this.ready = ready;
        }

        Account(boolean fail) {
            this();
            if (fail) {
                throw APPLICATION_FAILURE;
            }
        }

        Account(Account other) {
            this(other.getBalance());
            ready = true;
        }

        Account(String value) {
            this(new Account(Integer.parseInt(value), true));
        }

        Account(char ignored) {
            this(failingArgument());
        }

        private static int failingArgument() {
            throw APPLICATION_FAILURE;
        }

        public int getBalance() {
            return balance;
        }

        public void add(int amount) {
            EVENTS.add("body");
            balance += amount;
        }

        public void fail() {
            balance = -1;
            throw APPLICATION_FAILURE;
        }

        private void privateHelper() {
            EVENTS.add("private");
        }
    }

    static class UnregisteredChecks extends Account {
        @ClassInvariant
        void inheritedHelper() {
            throw new AssertionError("unregistered contract invariant");
        }
    }

    static class AccountChecks extends UnregisteredChecks {
        @Target private Account receiver;

        AccountChecks() {
            getBalance();
        }

        @ClassInvariant
        private void zReadyAndNonnegative() {
            assert !preCondition() && !postCondition() && result() == null;
            assert receiver.ready : "account not ready";
            assert receiver.getBalance() >= 0 : "negative balance";
            EVENTS.add("first:" + receiver.balance);
        }

        @ClassInvariant
        void aSecond() {
            EVENTS.add("second:" + receiver.balance);
        }

        @Override
        public void add(int amount) {
            if (preCondition()) {
                EVENTS.add("pre");
            }
            if (postCondition()) {
                EVENTS.add("post");
            }
        }
    }

    @ContractReference(PremiumChecks.class)
    static class Premium extends Account {
        int limit;

        Premium() {
            super(3);
            getBalance();
            limit = 10;
            ready = true;
        }

        public int limit() {
            return limit;
        }
    }

    static class PremiumChecks extends Premium {
        @Target private Premium receiver;

        @ClassInvariant
        void limitInitialized() {
            assert receiver.limit == 10 : "limit not initialized";
            EVENTS.add("premium");
        }
    }

    static class UnannotatedPremium extends Premium {
        UnannotatedPremium() {
            limit = 0;
            getBalance();
            limit = 10;
        }
    }

    static class ParentWithoutContract {
        ParentWithoutContract(boolean fail) {
            value();
            if (fail) {
                throw APPLICATION_FAILURE;
            }
        }

        public int value() {
            return 0;
        }
    }

    @ContractReference(ChildChecks.class)
    static class Child extends ParentWithoutContract {
        private final int initialized;

        Child() {
            this(false);
        }

        Child(boolean fail) {
            super(fail);
            initialized = 12;
        }

        @Override
        public int value() {
            return initialized;
        }
    }

    static class ChildChecks extends Child {
        @Target private Child target;

        @ClassInvariant
        void initialized() {
            assert target.value() == 12 : "child not initialized";
            EVENTS.add("child");
        }
    }

    static class Wallet {
        private final int initial;

        Wallet(int initial) {
            this.initial = initial;
        }

        @ContractReference(PocketChecks.class)
        class Pocket {
            int balance;

            Pocket() {
                this(0);
                balance = initial;
            }

            Pocket(int balance) {
                this.balance = balance;
            }

            public int amount() {
                return balance;
            }

            public int configuredInitial() {
                return initial;
            }
        }

        static class PocketChecks extends Pocket {
            @Target private Pocket receiver;

            PocketChecks(Wallet wallet) {
                wallet.super();
            }

            @ClassInvariant
            void funded() {
                assert receiver.balance == receiver.configuredInitial() : "inner constructor has not finished";
                EVENTS.add("pocket");
            }
        }
    }

    public static void main(String[] args) {
        Account account = new Account();
        events("first:0", "second:0");

        account.add(2);
        events("first:0", "second:0", "pre", "body", "post", "first:2", "second:2");
        account.privateHelper();
        events("private");

        // Entry rejection stops the body. Exit rejection observes the body's write.
        account.balance = -1;
        violation(() -> account.add(2), "negative balance");
        assert account.balance == -1;
        events();
        account.balance = 1;
        violation(() -> account.add(-2), "negative balance");
        assert account.balance == -1;
        events("first:1", "second:1", "pre", "body", "post");

        // The original application failure wins, and the next call still checks its invariant.
        account.balance = 1;
        expectApplicationFailure(account::fail);
        events("first:1", "second:1");
        violation(account::getBalance, "negative balance");
        events();

        new Account(5, true);
        events("first:5", "second:5");
        violation(() -> new Account(-1, true), "negative balance");
        events();
        expectApplicationFailure(() -> new Account(true));
        events();
        expectApplicationFailure(() -> new Account('x'));
        events();
        new Account("7");
        events("first:7", "second:7", "first:7", "second:7",
                "first:7", "second:7", "first:7", "second:7");

        var premium = new Premium();
        events("first:3", "second:3", "premium");
        assert premium.limit() == 10;
        events("first:3", "second:3", "premium", "first:3", "second:3", "premium");
        new UnannotatedPremium();
        events("first:3", "second:3", "premium");
        expectApplicationFailure(() -> new Child(true));
        events();
        var child = new Child();
        events("child");
        assert child.value() == 12;
        events("child", "child");

        var pocket = new Wallet(4).new Pocket();
        events("pocket");
        assert pocket.amount() == 4;
        events("pocket", "pocket");
        System.out.println("invariants passed");
    }

    private static void violation(Runnable action, String message) {
        try {
            action.run();
        } catch (AssertionError failure) {
            assert failure.getMessage().equals(message + " (class-invariant)") : failure;
            assert !preCondition() && !postCondition() && result() == null;
            return;
        }
        throw new AssertionError("Expected class invariant violation");
    }

    private static void expectApplicationFailure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            assert failure == APPLICATION_FAILURE : failure;
            return;
        }
        throw new AssertionError("Expected application failure");
    }

    private static void events(String... expected) {
        assert EVENTS.equals(List.of(expected)) : "expected " + List.of(expected) + ", got " + EVENTS;
        EVENTS.clear();
    }
}
