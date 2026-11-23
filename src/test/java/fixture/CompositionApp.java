package fixture;

import io.github.Mehrdadmoradli.c4jnext.annotations.ClassInvariant;
import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.github.Mehrdadmoradli.c4jnext.Condition.*;

public class CompositionApp {
    private static final List<String> EVENTS = new ArrayList<>();
    private static final String LSP = "LSP Violation: Invalid multiple inheritance, as two pre-conditions "
            + "returned different results. (pre-condition)";
    public static int invariantMask;

    @ContractReference(ParentChecks.class)
    public static class Parent {
        public int work(int accepted, int rejectedPost, int invariants) {
            return body(accepted, invariants);
        }
    }

    @ContractReference(ChildChecks.class)
    public static class Child extends Parent {
        @Override
        public int work(int accepted, int rejectedPost, int invariants) {
            return body(accepted, invariants);
        }
    }

    @ContractReference(LeftChecks.class)
    public interface Left {
        int work(int accepted, int rejectedPost, int invariants);
    }

    @ContractReference(RightChecks.class)
    public interface Right {
        int work(int accepted, int rejectedPost, int invariants);
    }

    public static class Horizontal implements Left, Right {
        @Override
        public int work(int accepted, int rejectedPost, int invariants) {
            return body(accepted, invariants);
        }
    }

    public static class Mixed extends Parent implements Left, Right {
        @Override
        public int work(int accepted, int rejectedPost, int invariants) {
            return body(accepted, invariants);
        }
    }

    public static class ParentChecks extends Parent {
        @Override
        public int work(int accepted, int rejectedPost, int invariants) {
            if (preCondition()) {
                record("parent:pre");
                assert (accepted & 1) != 0 : "parent rejected";
            }
            if (postCondition()) {
                record("parent:post");
                assert (rejectedPost & 1) == 0 : "parent post rejected";
                assert result(int.class) == accepted;
            }
            return 0;
        }

        @ClassInvariant
        public void parentInvariant() {
            if (invariantMask != 0) {
                record("parent:invariant");
                assert (invariantMask & 1) == 0 : "parent invariant rejected";
            }
        }
    }

    public static class ChildChecks extends Child {
        @Override
        public int work(int accepted, int rejectedPost, int invariants) {
            if (preCondition()) {
                record("child:pre");
                assert (accepted & 2) != 0 : "child rejected";
            }
            if (postCondition()) {
                record("child:post");
                assert (rejectedPost & 2) == 0 : "child post rejected";
                assert result(int.class) == accepted;
            }
            return 0;
        }

        @ClassInvariant
        public void childInvariant() {
            if (invariantMask != 0) {
                record("child:invariant");
                assert (invariantMask & 2) == 0 : "child invariant rejected";
            }
        }
    }

    public static class LeftChecks implements Left {
        @Override
        public int work(int accepted, int rejectedPost, int invariants) {
            if (preCondition()) {
                record("left:pre");
                assert (accepted & 2) != 0 : "left rejected";
            }
            if (postCondition()) {
                record("left:post");
                assert (rejectedPost & 2) == 0 : "left post rejected";
                assert result(int.class) == accepted;
            }
            return 0;
        }

        @ClassInvariant
        public void leftInvariant() {
            if (invariantMask != 0) {
                record("left:invariant");
                assert (invariantMask & 2) == 0 : "left invariant rejected";
            }
        }
    }

    public static class RightChecks implements Right {
        @Override
        public int work(int accepted, int rejectedPost, int invariants) {
            if (preCondition()) {
                record("right:pre");
                assert (accepted & 4) != 0 : "right rejected";
            }
            if (postCondition()) {
                record("right:post");
                assert (rejectedPost & 4) == 0 : "right post rejected";
                assert result(int.class) == accepted;
            }
            return 0;
        }

        @ClassInvariant
        public void rightInvariant() {
            if (invariantMask != 0) {
                record("right:invariant");
                assert (invariantMask & 4) == 0 : "right invariant rejected";
            }
        }
    }

    @ContractReference(ExplicitPreChecks.class)
    public interface ExplicitPre {
        int dependency(int input);
    }

    @ContractReference(OldDependencyChecks.class)
    public interface OldDependency {
        int dependency(int input);
    }

    public static class ExplicitPreChecks implements ExplicitPre {
        @Override
        public int dependency(int input) {
            if (preCondition()) {
                record("explicit:pre");
                assert input > 0 : "nonpositive input";
            }
            return 0;
        }
    }

    public static class OldDependencyChecks implements OldDependency {
        @Override
        public int dependency(int input) {
            if (postCondition()) {
                record("dependency:post");
                assert "entry".equals(old("entry"));
            }
            return 0;
        }
    }

    public static class OldOnly implements OldDependency {
        @Override
        public int dependency(int input) {
            return body(input, 0);
        }
    }

    public static class WithDependency implements ExplicitPre, OldDependency {
        @Override
        public int dependency(int input) {
            return body(input, 0);
        }
    }

    public static int body(int accepted, int invariants) {
        record("body");
        invariantMask = invariants;
        return accepted;
    }

    public static void record(String phase) {
        EVENTS.add(phase);
    }

    public static void main(String[] args) {
        Child vertical = new Child();
        assert vertical.work(3, 0, 0) == 3;
        events("parent:pre", "body", "parent:post", "child:post");
        assert vertical.work(1, 0, 0) == 1;
        events("parent:pre", "body", "parent:post", "child:post");
        rejects(() -> vertical.work(2, 0, 0), "parent rejected (pre-condition)", "parent:pre");

        Horizontal horizontal = new Horizontal();
        assert horizontal.work(6, 0, 0) == 6;
        events("right:pre", "left:pre", "body", "right:post", "left:post");
        rejects(() -> horizontal.work(0, 0, 0), "left rejected (pre-condition)", "right:pre", "left:pre");
        rejects(() -> horizontal.work(2, 0, 0), LSP, "right:pre", "left:pre");

        Mixed mixed = new Mixed();
        rejects(() -> mixed.work(4, 0, 0), LSP, "right:pre", "left:pre");
        rejects(() -> mixed.work(0, 0, 0), "parent rejected (pre-condition)",
                "right:pre", "left:pre", "parent:pre");
        rejects(() -> mixed.work(7, 2, 0), "left post rejected (post-condition)",
                "right:pre", "left:pre", "parent:pre", "body", "right:post", "left:post");
        rejects(() -> mixed.work(7, 0, 2), "left invariant rejected (class-invariant)",
                "right:pre", "left:pre", "parent:pre", "body", "right:post", "left:post", "parent:post",
                "right:invariant", "left:invariant");
        assert mixed.work(7, 0, 8) == 7;
        events("right:pre", "left:pre", "parent:pre", "body", "right:post", "left:post", "parent:post",
                "right:invariant", "left:invariant", "parent:invariant");

        // The reference's old() rewrite fails Java 25 bytecode verification for this case.
        if (!Arrays.asList(args).contains("--skip-entry-dependencies")) {
            assert new OldOnly().dependency(1) == 1;
            events("body", "dependency:post");
            WithDependency withDependency = new WithDependency();
            rejects(() -> withDependency.dependency(0), LSP, "explicit:pre");
            assert withDependency.dependency(1) == 1;
            events("explicit:pre", "body", "dependency:post");
        }

        System.out.println("contract composition passed");
    }

    private static void rejects(Runnable call, String message, String... trace) {
        try {
            call.run();
        } catch (AssertionError error) {
            if (!message.equals(error.getMessage())) {
                throw new AssertionError("expected " + message + ", got " + error, error);
            }
            events(trace);
            return;
        }
        throw new AssertionError("Expected " + message);
    }

    private static void events(String... expected) {
        assert EVENTS.equals(Arrays.asList(expected)) : "expected " + Arrays.toString(expected) + ", got " + EVENTS;
        EVENTS.clear();
        invariantMask = 0;
    }
}
