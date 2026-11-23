package io.github.Mehrdadmoradli.c4jnext.runtime;

import io.github.Mehrdadmoradli.c4jnext.ContractError;
import io.github.Mehrdadmoradli.c4jnext.UsageError;

import java.util.Collections;
import java.util.IdentityHashMap;

final class ContractViolationFactory {
    private ContractViolationFactory() {}

    static Error lspViolation() {
        return violation(InvocationContext.Phase.PRE, new AssertionError(
                "LSP Violation: Invalid multiple inheritance, as two pre-conditions returned different results."));
    }

    static Error violation(InvocationContext.Phase phase, Throwable failure) {
        if (failure instanceof AssertionError assertion) {
            String detail = assertion.getMessage();
            String prefix = detail == null || detail.isEmpty() ? "" : detail + " ";
            var enhanced = new AssertionError(prefix + "(" + phase.label + ")");
            enhanced.initCause(assertion.getCause());
            enhanced.setStackTrace(assertion.getStackTrace());
            return enhanced;
        }

        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof UsageError usage) {
                return usage;
            }
        }
        return new ContractError("Contract Error in " + phase.label + ".", failure);
    }
}
