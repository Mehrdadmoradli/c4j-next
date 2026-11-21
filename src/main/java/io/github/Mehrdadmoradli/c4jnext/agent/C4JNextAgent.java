package io.github.Mehrdadmoradli.c4jnext.agent;

import java.lang.instrument.Instrumentation;

public final class C4JNextAgent {

    private C4JNextAgent() {
    }

    public static void premain(
            String agentArgs,
            Instrumentation instrumentation) {

        System.out.println("C4J Next agent started");
    }
}
