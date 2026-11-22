package io.github.Mehrdadmoradli.c4jnext.runtime;

import java.lang.StackWalker.StackFrame;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class ConstructionRuntime {
    private static final String OWNER = ConstructionRuntime.class.descriptorString();
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ThreadLocal<Deque<Object>> RECEIVERS = new ThreadLocal<>();
    private static final Map<ClassLoader, Map<String, Map<String, Integer>>> SITES = new WeakHashMap<>();
    private static final ClassValue<Map<String, Integer>> INITIALIZERS = new ClassValue<>() {
        @Override
        protected Map<String, Integer> computeValue(Class<?> type) {
            synchronized (SITES) {
                var classes = SITES.get(type.getClassLoader());
                return classes == null ? Map.of() : classes.getOrDefault(type.getName(), Map.of());
            }
        }
    };

    private ConstructionRuntime() {}

    // Kept adjacent to the initializing invokespecial in the transformed constructor.
    public static void initializing() {}

    public static void register(ClassLoader loader, String name, byte[] bytecode) {
        var sites = new HashMap<String, Integer>();
        for (var method : ClassFile.of().parse(bytecode).methods()) {
            if (!method.methodName().equalsString("<init>")) {
                continue;
            }
            int bci = 0;
            for (var element : method.code().orElseThrow()) {
                if (element instanceof Instruction instruction) {
                    bci += instruction.sizeInBytes();
                    if (instruction instanceof InvokeInstruction call
                            && call.name().equalsString("initializing")
                            && call.owner().asSymbol().descriptorString().equals(OWNER)) {
                        sites.put(method.methodType().stringValue(), bci);
                        break;
                    }
                }
            }
        }
        if (!sites.isEmpty()) {
            synchronized (SITES) {
                SITES.computeIfAbsent(loader, ignored -> new HashMap<>()).put(name, Map.copyOf(sites));
            }
        }
    }

    public static void enteredBody(Object receiver) {
        var stack = RECEIVERS.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            RECEIVERS.set(stack);
        }
        stack.push(receiver);
    }

    public static void aborted() {
        var stack = RECEIVERS.get();
        stack.pop();
        if (stack.isEmpty()) {
            RECEIVERS.remove();
        }
    }

    public static void completed(Object receiver) {
        aborted();
        if (ContractRuntime.evaluating()) {
            return;
        }
        // The immediate caller is a constructor for this receiver only at its initialization site.
        boolean delegated = WALKER.walk(frames -> frames.skip(2).findFirst()
                .filter(ConstructionRuntime::atInitialization).isPresent());
        if (!delegated) {
            ContractRuntime.checkConstructed(receiver);
        }
    }

    static boolean isConstructing(Object receiver) {
        var stack = RECEIVERS.get();
        if (stack != null && stack.stream().anyMatch(current -> current == receiver)) {
            return true;
        }
        // An uninstrumented superconstructor can call an override before its receiver can be bound.
        return WALKER.walk(frames -> {
            StackFrame callee = null;
            var iterator = frames.iterator();
            while (iterator.hasNext()) {
                StackFrame caller = iterator.next();
                if (callee != null && callee.getMethodName().equals("<init>")
                        && INITIALIZERS.get(callee.getDeclaringClass()).isEmpty()
                        && caller.getDeclaringClass().isAssignableFrom(receiver.getClass())
                        && atInitialization(caller)) {
                    return true;
                }
                callee = caller;
            }
            return false;
        });
    }

    private static boolean atInitialization(StackFrame frame) {
        if (!frame.getMethodName().equals("<init>")) {
            return false;
        }
        Integer site = INITIALIZERS.get(frame.getDeclaringClass()).get(frame.getDescriptor());
        return site != null && site == frame.getByteCodeIndex();
    }
}
