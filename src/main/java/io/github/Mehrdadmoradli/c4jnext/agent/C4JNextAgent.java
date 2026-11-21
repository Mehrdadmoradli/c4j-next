package io.github.Mehrdadmoradli.c4jnext.agent;

import java.lang.instrument.Instrumentation;
import io.github.Mehrdadmoradli.c4jnext.ContractReference;
import net.bytebuddy.agent.builder.AgentBuilder;
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

        AgentBuilder agent = new AgentBuilder.Default()
                .disableClassFormatChanges()
                .type(isAnnotatedWith(ContractReference.class)
                        .and(not(isInterface())).and(not(isAbstract())))
                .transform((builder, type, loader, module, domain) -> builder);

        if ("debug".equals(agentArgs)) {
            agent = agent.with(new AgentBuilder.Listener.Adapter() {
                @Override
                public void onTransformation(TypeDescription type, ClassLoader loader, JavaModule module,
                                             boolean loaded, DynamicType dynamicType) {
                    TypeDescription contract = type.getDeclaredAnnotations().ofType(ContractReference.class)
                            .getValue("value").resolve(TypeDescription.class);
                    System.err.println("C4J Next discovered " + type.getName() + " -> " + contract.getName());
                }
            });
        }
        agent.installOn(instrumentation);
    }
}
