package io.github.Mehrdadmoradli.c4jnext;

public class ContractError extends Error {
    public ContractError() {}

    public ContractError(String message) {
        super(message);
    }

    public ContractError(Throwable cause) {
        super(cause);
    }

    public ContractError(String message, Throwable cause) {
        super(message, cause);
    }
}
