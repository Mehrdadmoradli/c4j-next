package io.github.Mehrdadmoradli.c4jnext.runtime;

import java.lang.reflect.Method;
import java.util.List;

record MethodContractPlan(List<Check> checks, List<ContractBinding> invariants) {
    MethodContractPlan {
        checks = List.copyOf(checks);
        invariants = List.copyOf(invariants);
    }

    record Check(ContractBinding binding, Method method, boolean pre, boolean post,
                 List<OldValueStore.Capture> captures) {}
}
