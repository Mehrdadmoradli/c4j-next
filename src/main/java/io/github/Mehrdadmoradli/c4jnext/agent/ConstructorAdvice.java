package io.github.Mehrdadmoradli.c4jnext.agent;

import io.github.Mehrdadmoradli.c4jnext.runtime.ConstructionRuntime;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.AnalyzerAdapter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;

final class ConstructorAdvice {
    static final String RUNTIME = ConstructionRuntime.class.getName().replace('.', '/');

    private ConstructorAdvice() {}

    static AsmVisitorWrapper forType(String typeName, ClassLoader loader) {
        byte[] original;
        try {
            original = ClassFileLocator.ForClassLoader.of(loader).locate(typeName).resolve();
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read constructors of " + typeName, failure);
        }
        Map<String, Set<Integer>> sites = initializationSites(original);
        return new AsmVisitorWrapper.ForDeclaredMethods()
                .writerFlags(net.bytebuddy.jar.asm.ClassWriter.COMPUTE_FRAMES)
                .constructor(isConstructor(), (type, method, visitor, context, pool, writer, reader) ->
                        new ConstructorVisitor(visitor, sites.getOrDefault(method.getDescriptor(), Set.of())));
    }

    private static Map<String, Set<Integer>> initializationSites(byte[] original) {
        // The operand stack distinguishes this()/super() from new expressions in constructor arguments.
        var sites = new HashMap<String, Set<Integer>>();
        var reader = new ClassReader(original);
        reader.accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor,
                                                                String signature, String[] exceptions) {
                if (!name.equals("<init>")) {
                    return null;
                }
                return new AnalyzerAdapter(api, reader.getClassName(), access, name, descriptor, null) {
                    private int callIndex;

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String calledName,
                                                String calledDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESPECIAL && calledName.equals("<init>") && stack != null) {
                            int argumentSlots = (org.objectweb.asm.Type.getArgumentsAndReturnSizes(calledDescriptor)
                                    >> 2) - 1;
                            if (stack.get(stack.size() - argumentSlots - 1)
                                    == org.objectweb.asm.Opcodes.UNINITIALIZED_THIS) {
                                sites.computeIfAbsent(descriptor, ignored -> new HashSet<>()).add(callIndex);
                            }
                        }
                        callIndex++;
                        super.visitMethodInsn(opcode, owner, calledName, calledDescriptor, isInterface);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES | ClassReader.SKIP_DEBUG);
        return sites;
    }

    private static final class ConstructorVisitor extends MethodVisitor {
        private final Set<Integer> initializationSites;
        private final Label bodyStart = new Label();
        private final Label bodyEnd = new Label();
        private final Label success = new Label();
        private final Label failure = new Label();
        private int callIndex;
        private boolean hasBody;

        ConstructorVisitor(MethodVisitor visitor, Set<Integer> initializationSites) {
            super(Opcodes.ASM9, visitor);
            this.initializationSites = initializationSites;
            if (initializationSites.size() > 1) {
                throw new IllegalArgumentException("Constructor has multiple receiver initialization sites");
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            boolean initializesReceiver = initializationSites.contains(callIndex++);
            if (initializesReceiver) {
                // Marks the adjacent invokespecial so a returning callee can recognize delegation.
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME, "initializing", "()V", false);
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            if (initializesReceiver) {
                callRuntime("enteredBody");
                mv.visitLabel(bodyStart);
                hasBody = true;
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.RETURN && hasBody) {
                mv.visitJumpInsn(Opcodes.GOTO, success);
                return;
            }
            super.visitInsn(opcode);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            if (hasBody) {
                // The verifier prohibits a normal receiver binding before the initializing invokespecial.
                mv.visitLabel(bodyEnd);
                mv.visitTryCatchBlock(bodyStart, bodyEnd, failure, null);
                mv.visitLabel(failure);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME, "aborted", "()V", false);
                mv.visitInsn(Opcodes.ATHROW);
                mv.visitLabel(success);
                callRuntime("completed");
                mv.visitInsn(Opcodes.RETURN);
            }
            super.visitMaxs(maxStack, maxLocals);
        }

        private void callRuntime(String method) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME, method, "(Ljava/lang/Object;)V", false);
        }
    }
}
