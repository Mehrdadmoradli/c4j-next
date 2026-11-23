package io.github.Mehrdadmoradli.c4jnext.runtime;

import java.lang.reflect.Method;
import java.util.List;

record ContractBinding(Class<?> targetType, Class<?> contractType, List<Method> invariants) {
    ContractBinding {
        invariants = List.copyOf(invariants);
    }
}
