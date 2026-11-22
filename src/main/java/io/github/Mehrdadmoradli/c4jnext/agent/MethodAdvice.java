package io.github.Mehrdadmoradli.c4jnext.agent;

import io.github.Mehrdadmoradli.c4jnext.runtime.ContractRuntime;
import io.github.Mehrdadmoradli.c4jnext.runtime.InvocationContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.lang.reflect.Method;

public final class MethodAdvice {
    private MethodAdvice() {}

    @Advice.OnMethodEnter
    public static InvocationContext enter(@Advice.This Object target, @Advice.Origin Method method,
                                          @Advice.AllArguments Object[] arguments) {
        return ContractRuntime.enter(target, method, arguments);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.Enter InvocationContext context,
                            @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object result,
                            @Advice.Thrown Throwable thrown) {
        ContractRuntime.exit(context, result, thrown);
    }
}
