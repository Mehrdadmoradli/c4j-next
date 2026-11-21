import java.util.concurrent.atomic.AtomicReference;
import com.mat345st.dbcagent.annotations.*;
import com.mat345st.dbcagent.runtime.ContractBase;
public class BaselineProbe {
    static String scenario;
    @ContractReference(Rules.class) public interface Spec { int step(int amount); }
    public static class Counter implements Spec {
        int value;
        int[] box = {0};
        Counter(int value) { this.value = value; }
        public int step(int amount) {
            if (scenario.startsWith("exception")) throw new IllegalArgumentException("application");
            if (scenario.equals("nested") && amount == 2) step(1);
            value += amount;
            box[0]++;
            return scenario.equals("badResult") ? value + 1 : value;
        }
    }
    public static class Derived extends Counter {
        Derived() { super(0); }
        @Override public int step(int amount) { return value += amount; }
    }
    @ContractReference(SecondRules.class)
    public interface SecondSpec { int step(int amount); }
    public static class SecondRules extends ContractBase implements SecondSpec {
        public int step(int amount) {
            if (postCondition()) assert amount != 4 : "second post";
            return 0;
        }
    }
    public static class Both extends Counter implements Spec, SecondSpec {
        Both() { super(0); }
        @Override public int step(int amount) { return value += amount; }
    }
    @ContractReference(PurityRules.class)
    public interface PuritySpec { int mutate(); }
    public static class PurityTarget implements PuritySpec {
        int value;
        public int mutate() { return ++value; }
    }
    public static class PurityRules extends ContractBase implements PuritySpec {
        @Pure public int mutate() { return 0; }
    }
    public static class Rules extends ContractBase implements Spec {
        @Target Counter counter;
        @ClassInvariant public void invariant() { assert counter.value >= 0 : "invariant"; }
        public int step(int amount) {
            if (preCondition()) { assert amount >= 0 || scenario.equals("exitInv") : "pre"; }
            if (postCondition()) {
                assert !scenario.equals("post") : "post";
                assert (int)result() == counter.value : "result";
                if (scenario.equals("scalar") || scenario.equals("thread")) {
                    assert ((Counter)old()).value == counter.value - amount : "scalar";
                }
                if (scenario.equals("nested")) { assert old() != null : "nested"; }
                if (scenario.equals("mutable")) { assert ((Counter)old()).box[0] == counter.box[0] - 1 : "mutable"; }
                if (scenario.equals("unchanged")) { assert !unchanged(counter.box) : "changed array"; }
            }
            return 0;
        }
    }
    static void expect(String message, Runnable action) {
        try { action.run(); } catch (AssertionError error) {
            if (message.equals(error.getMessage())) return;
            throw error;
        }
        throw new AssertionError("missing violation " + message);
    }
    public static void main(String[] args) throws Exception {
        if (!BaselineProbe.class.desiredAssertionStatus()) throw new IllegalStateException("Run with -ea");
        scenario = args[0];
        Counter c = new Counter(0);
        switch (scenario) {
            case "pre" -> { expect("pre", () -> c.step(-1)); assert c.value == 0; }
            case "post" -> { expect("post", () -> c.step(1)); assert c.value == 1; }
            case "badResult" -> expect("result", () -> c.step(1));
            case "ctor" -> expect("invariant", () -> new Counter(-1));
            case "entryInv" -> { c.value = -1; expect("invariant", () -> c.step(1)); assert c.value == -1; }
            case "exitInv" -> expect("invariant", () -> c.step(-1));
            case "recovery" -> { expect("pre", () -> c.step(-1)); expect("pre", () -> c.step(-1)); }
            case "exception", "exceptionMask" -> {
                if (scenario.equals("exceptionMask")) c.value = 3;
                try { c.step(1); throw new AssertionError("missing application exception"); }
                catch (IllegalArgumentException e) { assert e.getMessage().equals("application"); }
            }
            case "superclass" -> expect("pre", () -> new Derived().step(-1));
            case "merge" -> expect("second post", () -> new Both().step(4));
            case "purity" -> {
                PurityTarget target = new PurityTarget();
                try { target.mutate(); }
                catch (AssertionError detected) { break; }
                if (target.value != 0) throw new AssertionError("pure method mutated its target without detection");
            }
            case "nested" -> { assert c.step(2) == 3; }
            case "thread" -> {
                var err = new AtomicReference<Throwable>();
                Runnable work = () -> { try { Counter local = new Counter(0); for (int i=0;i<30;i++) local.step(1); } catch(Throwable e){ err.set(e); } };
                Thread a = new Thread(work), b = new Thread(work); a.start(); b.start(); a.join(); b.join();
                if (err.get()!=null) throw new AssertionError(err.get());
            }
            case "scalar", "mutable", "unchanged" -> { assert c.step(1) == 1; }
            default -> throw new IllegalArgumentException("Unknown scenario " + scenario);
        }
        System.out.println("PASS " + scenario);
    }
}
