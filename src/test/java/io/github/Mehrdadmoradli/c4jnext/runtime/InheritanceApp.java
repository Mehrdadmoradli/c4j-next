package io.github.Mehrdadmoradli.c4jnext.runtime;

import fixture.LoaderTarget;
import io.github.Mehrdadmoradli.c4jnext.UsageError;
import io.github.Mehrdadmoradli.c4jnext.annotations.ClassInvariant;
import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;
import io.github.Mehrdadmoradli.c4jnext.annotations.Target;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import static io.github.Mehrdadmoradli.c4jnext.Condition.*;

public final class InheritanceApp {
    private static final List<String> EVENTS = new ArrayList<>();

    @ContractReference(SharedChecks.class)
    interface Shared {
        Number move(int amount);
    }

    @ContractReference(LeftChecks.class)
    interface Left extends Shared {}

    @ContractReference(RightChecks.class)
    interface Right extends Shared {}

    @ContractReference(ThirdChecks.class)
    interface Third {
        Number move(int amount);
    }

    static class SharedChecks implements Shared {
        @Target private Shared target;

        @Override
        public Number move(int amount) {
            if (preCondition()) {
                assert target != null;
                assert amount >= 0 : "negative amount";
                EVENTS.add("shared:pre");
            }
            if (postCondition()) {
                assert old("shared").equals("shared");
                EVENTS.add("shared:post");
            }
            return 0;
        }
    }

    static class LeftChecks implements Left {
        @Target private Left target;

        @Override
        public Number move(int amount) {
            if (preCondition()) {
                assert target != null;
                EVENTS.add("left:pre");
            }
            if (postCondition()) {
                assert old("left").equals("left");
                EVENTS.add("left:post");
            }
            return 0;
        }
    }

    static class RightChecks implements Right {
        @Target private Right target;

        @Override
        public Number move(int amount) {
            if (preCondition()) {
                assert target != null;
                EVENTS.add("right:pre");
            }
            if (postCondition()) {
                assert old("right").equals("right");
                EVENTS.add("right:post");
            }
            return 0;
        }
    }

    static class ThirdChecks implements Third {
        @Target private Third target;

        @Override
        public Number move(int amount) {
            if (preCondition()) {
                assert target != null;
                EVENTS.add("third:pre");
            }
            if (postCondition()) {
                assert old("third").equals("third");
                EVENTS.add("third:post");
            }
            return 0;
        }
    }

    @ContractReference(RootChecks.class)
    static class Root {
        public Number move(int amount) {
            EVENTS.add("root:body");
            return amount;
        }

        public Number move(String amount) {
            EVENTS.add("overload:body");
            return Integer.parseInt(amount);
        }
    }

    static class RootChecks extends Root {
        @Override
        public Number move(int amount) {
            if (preCondition()) EVENTS.add("root:pre");
            if (postCondition()) EVENTS.add("root:post");
            return 0;
        }
    }

    @ContractReference(MiddleChecks.class)
    static class Middle extends Root {}

    static class MiddleChecks extends Middle {
        @Override
        public Number move(int amount) {
            if (preCondition()) EVENTS.add("middle:pre");
            if (postCondition()) EVENTS.add("middle:post");
            return 0;
        }
    }

    static class Leaf extends Middle implements Left, Right, Third {
        @Override
        public Integer move(int amount) {
            EVENTS.add("leaf:body");
            return amount;
        }
    }

    static class Inherited extends Root {}

    static class AddedInterface extends Root implements Shared {}

    static class Plain {
        public Number move(int amount) {
            EVENTS.add("plain:body");
            return amount;
        }
    }

    static class InheritedFromPlain extends Plain implements Shared {}

    static class InheritedAgain extends InheritedFromPlain {}

    static class InheritedMiddle extends Middle implements Shared {}

    static class FinalPlain {
        public final Number move(int amount) {
            EVENTS.add("final-inherited:body");
            return amount;
        }
    }

    static class FinalInherited extends FinalPlain implements Shared {}

    static class FinalDirect implements Shared {
        @Override
        public final Number move(int amount) {
            EVENTS.add("final-direct:body");
            return amount;
        }
    }

    abstract static class Abstract implements Shared {
        @Override
        public abstract Number move(int amount);

        public Number call(int amount) {
            return move(amount);
        }
    }

    static class Concrete extends Abstract {
        @Override
        public Number move(int amount) {
            EVENTS.add("concrete:body");
            return amount;
        }
    }

    @ContractReference(DefaultChecks.class)
    interface HasDefault {
        default int value() {
            EVENTS.add("default:body");
            return 7;
        }
    }

    static class DefaultChecks implements HasDefault {
        @Override
        public int value() {
            if (preCondition()) EVENTS.add("default:pre");
            if (postCondition()) EVENTS.add("default:post");
            return 0;
        }
    }

    static class DefaultOnly implements HasDefault {}

    static class OverrideDefault implements HasDefault {
        @Override
        public int value() {
            EVENTS.add("override:body");
            return 8;
        }
    }

    @ContractReference(GenericChecks.class)
    interface Generic<T> {
        T echo(T item);
    }

    static class GenericChecks implements Generic<String> {
        @Override
        public String echo(String item) {
            if (preCondition()) EVENTS.add("generic:pre");
            if (postCondition()) assert result(String.class) == item;
            return null;
        }
    }

    static class GenericImpl implements Generic<String> {
        @Override
        public String echo(String item) {
            EVENTS.add("generic:body");
            return item;
        }
    }

    @ContractReference(PositiveChecks.class)
    interface Positive {
        int value();
    }

    static class PositiveChecks implements Positive {
        @Target private Positive target;

        @ClassInvariant
        void positive() {
            assert target.value() > 0 : "nonpositive value";
            EVENTS.add("positive:invariant");
        }

        @Override
        public int value() {
            return 0;
        }
    }

    static class PositiveImpl implements Positive {
        private final int value;

        PositiveImpl(int value) {
            this.value = value;
        }

        @Override
        public int value() {
            return value;
        }
    }

    @ContractReference(String.class)
    interface Invalid {}

    static class BadTarget implements Invalid {}

    @ContractReference(SharedImplementation.class)
    interface AmbiguousA {}

    @ContractReference(SharedImplementation.class)
    interface AmbiguousB {}

    static class SharedImplementation implements AmbiguousA, AmbiguousB {}

    static class AmbiguousTarget implements AmbiguousA, AmbiguousB {}

    public static void main(String[] args) throws Exception {
        Root leaf = new Leaf();
        assert ((Integer) leaf.move(4)) == 4;
        events("third:pre", "shared:pre", "root:pre",
                "leaf:body", "third:post", "right:post", "left:post", "shared:post", "middle:post", "root:post");

        assert leaf.move("2").intValue() == 2;
        events("overload:body");

        assert new Inherited().move(3).intValue() == 3;
        events("root:pre", "root:body", "root:post");
        assert new Middle().move(3).intValue() == 3;
        events("root:pre", "root:body", "root:post", "middle:post");
        assert new AddedInterface().move(3).intValue() == 3;
        events("shared:pre", "root:pre", "root:body", "root:post", "shared:post");
        assert new InheritedFromPlain().move(3).intValue() == 3;
        events("shared:pre", "plain:body", "shared:post");
        var inheritedAgain = new InheritedAgain();
        assert inheritedAgain.move(3).intValue() == 3;
        events("shared:pre", "plain:body", "shared:post");
        expect(AssertionError.class, () -> inheritedAgain.move(-1), "negative amount (pre-condition)");
        events();
        assert new InheritedMiddle().move(3).intValue() == 3;
        events("shared:pre", "root:pre", "root:body", "root:post", "middle:post", "shared:post");

        assert new FinalInherited().move(3).intValue() == 3;
        events("final-inherited:body");
        assert new FinalDirect().move(3).intValue() == 3;
        events("shared:pre", "final-direct:body", "shared:post");
        assert new Concrete().call(3).intValue() == 3;
        events("shared:pre", "concrete:body", "shared:post");
        assert new DefaultOnly().value() == 7;
        events("default:body");
        assert new OverrideDefault().value() == 8;
        events("default:pre", "override:body", "default:post");

        Generic<String> generic = new GenericImpl();
        assert generic.echo("value").equals("value");
        events("generic:pre", "generic:body");

        expect(AssertionError.class, () -> new PositiveImpl(0), "nonpositive value (class-invariant)");
        var positive = new PositiveImpl(1);
        events("positive:invariant");
        assert positive.value() == 1;
        events("positive:invariant", "positive:invariant");

        expect(UsageError.class, BadTarget::new, "must be a concrete class that implements");
        expect(UsageError.class, AmbiguousTarget::new, "Ambiguous contract");
        loaderPlans();
        assert ContractRuntime.current() == null;
        System.out.println("contract inheritance passed");
    }

    private static void loaderPlans() throws Exception {
        URL testClasses = LoaderTarget.class.getProtectionDomain().getCodeSource().getLocation();
        String targetName = LoaderTarget.class.getName();
        var parent = new ClassLoader(InheritanceApp.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(targetName) || name.startsWith(targetName + "$")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        try (var first = new URLClassLoader(new URL[] {testClasses}, parent);
             var second = new URLClassLoader(new URL[] {testClasses}, parent)) {
            var a = Class.forName(targetName, true, first).getMethod("run", int.class);
            var b = Class.forName(targetName, true, second).getMethod("run", int.class);
            var planA = ContractExecutor.planFor(a, false);
            var planB = ContractExecutor.planFor(b, false);
            assert planA == ContractExecutor.planFor(a, false) && planA != planB;
            assert planA.checks().size() == 1 && planB.checks().size() == 1;
            assert planA.checks().getFirst().method().getDeclaringClass().getClassLoader() == first;
            assert planB.checks().getFirst().method().getDeclaringClass().getClassLoader() == second;
            expect(UnsupportedOperationException.class, planA.checks()::clear, null);
            assert a.invoke(a.getDeclaringClass().getConstructor().newInstance(), 5).equals(5);
            assert b.invoke(b.getDeclaringClass().getConstructor().newInstance(), 6).equals(6);
        }
    }

    private static void events(String... expected) {
        assert EVENTS.equals(List.of(expected)) : "expected " + List.of(expected) + ", got " + EVENTS;
        EVENTS.clear();
    }

    private static void expect(Class<? extends Throwable> type, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                assert message == null || failure.getMessage().contains(message) : failure;
                return;
            }
            throw failure;
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
