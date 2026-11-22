package io.github.Mehrdadmoradli.c4jnext.runtime;

import io.github.Mehrdadmoradli.c4jnext.Condition;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static org.objectweb.asm.Opcodes.*;

/** Replaces each supported argument expression with a lookup of its entry value. */
public final class OldExpressionTransformer implements ClassFileTransformer {
    static final String OLD_DESCRIPTOR = "(Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String CONDITION = Type.getInternalName(Condition.class);
    private static final String RUNTIME = Type.getInternalName(ContractRuntime.class);
    private static final byte[] CONDITION_BYTES = CONDITION.getBytes(StandardCharsets.US_ASCII);

    record Member(String owner, String name, String descriptor) {}

    record Read(Member target, Member value, boolean getter, boolean unbox) {}

    record Site(String key, int index, Read read, Object literal, String box, boolean supported) {}

    private record Argument(AbstractInsnNode first, Read read, Object literal, String box) {}

    private static final Map<ClassLoader, Map<String, Map<String, List<Site>>>> BY_LOADER = new WeakHashMap<>();
    private static final ClassValue<Map<String, List<Site>>> BY_CLASS = new ClassValue<>() {
        @Override
        protected Map<String, List<Site>> computeValue(Class<?> type) {
            synchronized (BY_LOADER) {
                var definitions = BY_LOADER.get(type.getClassLoader());
                return definitions == null ? Map.of() : definitions.getOrDefault(type.getName(), Map.of());
            }
        }
    };

    static List<Site> sitesFor(Class<?> type, String method) {
        return BY_CLASS.get(type).getOrDefault(method, List.of());
    }

    @Override
    public byte[] transform(Module module, ClassLoader loader, String name, Class<?> redefining,
                            ProtectionDomain domain, byte[] bytecode) {
        if (loader == null || name == null || !referencesCondition(bytecode)) {
            return null;
        }
        var reader = new ClassReader(bytecode);
        var model = new ClassNode();
        reader.accept(model, 0);
        var methods = new HashMap<String, List<Site>>();
        boolean rewritten = false;
        for (MethodNode method : model.methods) {
            if (method.name.startsWith("<")) {
                continue;
            }
            List<MethodInsnNode> calls = new ArrayList<>();
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.getOpcode() == INVOKESTATIC
                        && call.owner.equals(CONDITION) && call.name.equals("old")
                        && call.desc.equals(OLD_DESCRIPTOR)) {
                    calls.add(call);
                }
            }
            if (calls.isEmpty()) {
                continue;
            }
            String methodKey = method.name + method.desc;
            Set<LabelNode> boundaries = controlBoundaries(method);
            var sites = new ArrayList<Site>();
            for (int i = 0; i < calls.size(); i++) {
                var call = calls.get(i);
                var argument = argumentOf(call, boundaries);
                String key = methodKey + "#" + i;
                if (argument == null) {
                    sites.add(new Site(key, i, null, null, null, false));
                    continue;
                }
                sites.add(new Site(key, i, argument.read(), argument.literal(), argument.box(), true));
                replace(method.instructions, argument.first(), call, model.name, key);
                rewritten = true;
            }
            methods.put(methodKey, List.copyOf(sites));
        }
        if (methods.isEmpty()) {
            return null;
        }
        byte[] transformed = null;
        if (rewritten) {
            // The replaced expression still leaves one Object on the stack, so its stack map is unchanged.
            var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            model.accept(writer);
            transformed = writer.toByteArray();
        }
        synchronized (BY_LOADER) {
            BY_LOADER.computeIfAbsent(loader, ignored -> new HashMap<>())
                    .put(model.name.replace('/', '.'), Map.copyOf(methods));
        }
        return transformed;
    }

    private static Argument argumentOf(MethodInsnNode old, Set<LabelNode> boundaries) {
        AbstractInsnNode value = previous(old, boundaries);
        String boxed = null;
        if (value instanceof MethodInsnNode box && box.getOpcode() == INVOKESTATIC
                && box.name.equals("valueOf") && box.desc.length() >= 4) {
            String primitive = box.desc.substring(1, 2);
            if (box.owner.equals(wrapper(primitive)) && box.desc.equals("(" + primitive + ")L" + box.owner + ";")) {
                boxed = primitive;
                value = previous(box, boundaries);
            }
        }

        String unboxed = null;
        if (boxed != null && value instanceof MethodInsnNode unbox && unbox.getOpcode() == INVOKEVIRTUAL
                && unbox.owner.equals(wrapper(boxed)) && unbox.name.equals(unboxName(boxed))
                && unbox.desc.equals("()" + boxed)) {
            unboxed = "L" + unbox.owner + ";";
            value = previous(unbox, boundaries);
        }

        if (value instanceof FieldInsnNode field && field.getOpcode() == GETFIELD) {
            return readArgument(value, member(field), field.desc, false, boxed, unboxed, boundaries);
        }
        if (value instanceof MethodInsnNode getter && getter.getOpcode() == INVOKEVIRTUAL
                && getter.desc.startsWith("()") && !getter.desc.equals("()V")) {
            return readArgument(value, new Member(getter.owner, getter.name, getter.desc),
                    getter.desc.substring(2), true, boxed, unboxed, boundaries);
        }
        return unboxed == null ? literalArgument(value, boxed) : null;
    }

    private static Argument readArgument(AbstractInsnNode value, Member member, String descriptor, boolean getter,
                                         String boxed, String unboxed, Set<LabelNode> boundaries) {
        boolean compatible = unboxed != null ? descriptor.equals(unboxed)
                : boxed != null ? descriptor.equals(boxed) : isReference(descriptor);
        if (!compatible || !(previous(value, boundaries) instanceof FieldInsnNode target)
                || target.getOpcode() != GETFIELD) {
            return null;
        }
        AbstractInsnNode load = previous(target, boundaries);
        if (!(load instanceof VarInsnNode variable) || variable.getOpcode() != ALOAD || variable.var != 0) {
            return null;
        }
        return new Argument(load, new Read(member(target), member, getter, unboxed != null), null, boxed);
    }

    private static Argument literalArgument(AbstractInsnNode value, String boxed) {
        if (value == null) {
            return null;
        }
        int opcode = value.getOpcode();
        if (boxed == null) {
            if (opcode == ACONST_NULL) {
                return new Argument(value, null, null, null);
            }
            return value instanceof LdcInsnNode ldc && ldc.cst instanceof String text
                    ? new Argument(value, null, text, null) : null;
        }

        Object number = switch (opcode) {
            case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5 -> opcode - ICONST_0;
            case LCONST_0, LCONST_1 -> (long) (opcode - LCONST_0);
            case FCONST_0, FCONST_1, FCONST_2 -> (float) (opcode - FCONST_0);
            case DCONST_0, DCONST_1 -> (double) (opcode - DCONST_0);
            case BIPUSH, SIPUSH -> ((IntInsnNode) value).operand;
            case LDC -> ((LdcInsnNode) value).cst;
            default -> null;
        };
        Object literal = switch (boxed) {
            case "Z" -> number instanceof Integer n ? n != 0 : null;
            case "B" -> number instanceof Integer n ? n.byteValue() : null;
            case "S" -> number instanceof Integer n ? n.shortValue() : null;
            case "C" -> number instanceof Integer n ? (char) n.intValue() : null;
            case "I" -> number instanceof Integer ? number : null;
            case "J" -> number instanceof Long ? number : null;
            case "F" -> number instanceof Float ? number : null;
            case "D" -> number instanceof Double ? number : null;
            default -> null;
        };
        return literal == null ? null : new Argument(value, null, literal, boxed);
    }

    private static void replace(InsnList instructions, AbstractInsnNode first, MethodInsnNode call,
                                String owner, String key) {
        var lookup = new InsnList();
        lookup.add(new LdcInsnNode(Type.getObjectType(owner)));
        lookup.add(new LdcInsnNode(key));
        lookup.add(new MethodInsnNode(INVOKESTATIC, RUNTIME, "oldValue",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;", false));
        for (var instruction = first; instruction != call;) {
            var next = instruction.getNext();
            if (instruction.getOpcode() >= 0) {
                instructions.remove(instruction);
            }
            instruction = next;
        }
        instructions.insertBefore(call, lookup);
        instructions.remove(call);
    }

    private static Set<LabelNode> controlBoundaries(MethodNode method) {
        var labels = new HashSet<LabelNode>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode jump) {
                labels.add(jump.label);
            } else if (instruction instanceof TableSwitchInsnNode table) {
                labels.add(table.dflt);
                labels.addAll(table.labels);
            } else if (instruction instanceof LookupSwitchInsnNode lookup) {
                labels.add(lookup.dflt);
                labels.addAll(lookup.labels);
            }
        }
        for (TryCatchBlockNode handler : method.tryCatchBlocks) {
            labels.add(handler.start);
            labels.add(handler.end);
            labels.add(handler.handler);
        }
        return labels;
    }

    private static AbstractInsnNode previous(AbstractInsnNode start, Set<LabelNode> boundaries) {
        for (AbstractInsnNode instruction = start.getPrevious(); instruction != null;
             instruction = instruction.getPrevious()) {
            if (instruction instanceof FrameNode || instruction instanceof LabelNode label && boundaries.contains(label)) {
                return null;
            }
            if (instruction.getOpcode() >= 0) {
                return instruction;
            }
        }
        return null;
    }

    private static Member member(FieldInsnNode field) {
        return new Member(field.owner, field.name, field.desc);
    }

    private static boolean isReference(String descriptor) {
        return descriptor.startsWith("L") || descriptor.startsWith("[");
    }

    private static String wrapper(String primitive) {
        String name = switch (primitive) {
            case "Z" -> "Boolean";
            case "B" -> "Byte";
            case "S" -> "Short";
            case "C" -> "Character";
            case "I" -> "Integer";
            case "J" -> "Long";
            case "F" -> "Float";
            case "D" -> "Double";
            default -> null;
        };
        return name == null ? null : "java/lang/" + name;
    }

    private static String unboxName(String primitive) {
        return switch (primitive) {
            case "Z" -> "booleanValue";
            case "B" -> "byteValue";
            case "S" -> "shortValue";
            case "C" -> "charValue";
            case "I" -> "intValue";
            case "J" -> "longValue";
            case "F" -> "floatValue";
            case "D" -> "doubleValue";
            default -> throw new IllegalArgumentException(primitive);
        };
    }

    private static boolean referencesCondition(byte[] bytecode) {
        for (int i = 0; i <= bytecode.length - CONDITION_BYTES.length; i++) {
            int match = 0;
            while (match < CONDITION_BYTES.length && bytecode[i + match] == CONDITION_BYTES[match]) {
                match++;
            }
            if (match == CONDITION_BYTES.length) {
                return true;
            }
        }
        return false;
    }
}
