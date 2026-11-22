# C4J Next feature support

Phase 5 results for the Java 25 prototype. Integration tests launch the packaged agent in a separate JVM.

| ID | Feature | Current evidence |
|---|---|---|
| 1 | Preconditions | Implemented for the direct instance-method scope. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies rejection before mutation and repeated enforcement after caught violations. |
| 2 | Postconditions | Implemented for normal returns in the direct instance-method scope. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies passing and failing checks. |
| 3 | Class invariants | Partial. [InvariantIT](../src/test/java/fixture/InvariantIT.java) verifies method entry and normal exit, superclass and declaration order, and re-entry protection. Entry evaluation is a documented difference from C4J 6.0.0. Interface association and purity-based skipping remain pending. |
| 4 | result() | Implemented for normal returns. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies all primitive types, reference identity, null, void, and cleanup. [InvocationIsolationIT](../src/test/java/fixture/InvocationIsolationIT.java) verifies isolation across nested, recursive, and concurrent calls. |
| 5 | Contract discovery and association | Partial. [DiscoveryIT](../src/test/java/fixture/DiscoveryIT.java) verifies direct association on a concrete class. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies overloads, an inherited contract method, and target injection. |
| 6 | Contract violation handling | Partial. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies method phase labels, causes, stack frames, and usage error propagation. [InvariantIT](../src/test/java/fixture/InvariantIT.java) verifies the invariant phase label. Initializer violations remain pending. |
| 7 | Constructor invariant checking | Partial. [InvariantIT](../src/test/java/fixture/InvariantIT.java) verifies simple, delegated, nested, superclass/subclass, and retained-enclosing-instance member constructors, including exceptional exits. See the [Phase 3 notes](agents/plans/2026-11-21-c4j-next-mvp.md#phase-3-class-invariants-and-construction) for limitations. |
| 8 | old() | Partial. [OldExpressionsIT](../src/test/java/fixture/OldExpressionsIT.java) verifies the [supported postcondition expressions](#old-expressions), reference identity, deferred capture failure, and isolation across nested, recursive, and concurrent calls. |
| 9 | Superclass contract inheritance | Partial. [InvariantIT](../src/test/java/fixture/InvariantIT.java) verifies inherited class invariants at method and constructor boundaries. Inherited preconditions and postconditions remain pending. |
| 10 | Interface contract inheritance | Unsupported in Day 1. |
| 11 | Contract merging | Unsupported in Day 1. |
| 12 | LSP behavior | Unsupported in Day 1. |
| 13 | unchanged() | Unsupported in Day 1. |
| 14 | Exceptional method execution | Unsupported for exceptional contract checks and helpers. [InvocationIsolationIT](../src/test/java/fixture/InvocationIsolationIT.java) verifies that exceptional exits clear the frame, skip normal postconditions, and preserve the original application throwable. |
| 15 | @Pure | Unsupported in Day 1. |
| 16 | Static method contracts | Unsupported in Day 1. |
| 17 | ignored() | Unsupported in Day 1. |
| 18 | maxTime() | Unsupported in Day 1. |
| 19 | Advanced inheritance | Unsupported in Day 1. |
| 20 | External contract discovery | Unsupported in Day 1. |

## Old expressions

Use `Condition.old(expression)` directly in a method contract's postcondition.
For example, a deposit contract can compare the new balance with the balance
from before the application method ran:

```java
if (postCondition()) {
    assert account.balance == old(account.balance) + amount;
}
```

Here `account` is the contract's injected `@Target` field. The agent supports
the following argument bytecode:

- Primitive literals, string literals, and `null`.
- A direct instance field read through `@Target`, for any primitive or reference type.
- A zero-argument final getter through `@Target` whose body only returns one
  direct field. The getter must be non-synchronized. For example, a final
  `balance()` that contains only `return balance;` supports `old(account.balance())`.
- Java's primitive boxing for these values and same-type unboxing of a directly
  read wrapper, such as `old(account.boxedBalance.intValue())`.

The agent captures each site once per invocation, after passing preconditions and
before the target body. Reference values keep their identity, so a saved reference
still sees mutations to that object. If an entry read fails, the error surfaces as a
postcondition `ContractError` when that site is read. An unused saved failure
allows the invocation to finish normally.

Locals and parameters, arithmetic, nested dereferences, array elements, arbitrary
getter bodies, and calls routed through helpers are outside this subset. Unsupported
direct sites in matched contract methods report `UsageError`. Invariant and constructor
uses also remain outside this phase.
