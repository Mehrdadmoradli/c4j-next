package io.github.Mehrdadmoradli.c4jnext.agent;

import java.lang.instrument.Instrumentation;
import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;
import io.github.Mehrdadmoradli.c4jnext.runtime.ConstructionRuntime;
import io.github.Mehrdadmoradli.c4jnext.runtime.OldExpressionTransformer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

import static net.bytebuddy.matcher.ElementMatchers.*;

public final class C4JNextAgent {

    private C4JNextAgent() {
    }

    public static void premain(
            String agentArgs,
            Instrumentation instrumentation) {

        instrumentation.addTransformer(new OldExpressionTransformer());
        AgentBuilder agent = new AgentBuilder.Default()
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onTransformation(TypeDescription type, ClassLoader loader, JavaModule module,
                                                 boolean loaded, DynamicType dynamicType) {
                        ConstructionRuntime.register(loader, type.getName(), dynamicType.getBytes());
                    }

                    @Override
                    public void onError(String typeName, ClassLoader loader, JavaModule module,
                                        boolean loaded, Throwable error) {
                        System.err.println("C4J Next could not transform " + typeName + " (loader " + loader + ")");
                        error.printStackTrace(System.err);
                    }
                })
                .type(hasSuperType(isAnnotatedWith(ContractReference.class)).and(not(isInterface())))
                .transform((builder, type, loader, module, domain) -> {
                    var withBoundaries = builder.visit(ConstructorAdvice.forType(type.getName(), loader))
                            .visit(Advice.withCustomMapping().bind(MethodAdvice.Delegate.class, false)
                                    .to(MethodAdvice.class)
                            .on(isMethod().and(isDeclaredBy(type))
                                    .and(not(isStatic().or(isPrivate()).or(isAbstract()).or(isNative())
                                            .or(isBridge()).or(isSynthetic())))));
                    return InheritedMethodDelegates.addTo(withBoundaries, type);
                });

        if ("debug".equals(agentArgs)) {
            agent = agent.with(new AgentBuilder.Listener.Adapter() {
                @Override
                public void onTransformation(TypeDescription type, ClassLoader loader, JavaModule module,
                                             boolean loaded, DynamicType dynamicType) {
                    var association = type.getDeclaredAnnotations().ofType(ContractReference.class);
                    if (association != null && !type.isAbstract()) {
                        TypeDescription contract = association.getValue("value").resolve(TypeDescription.class);
                        System.err.println("C4J Next discovered " + type.getName() + " -> " + contract.getName());
                    }
                }
            });
        }
        agent.installOn(instrumentation);
    }
}
