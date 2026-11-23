# C4J Next feature support

Phase 7 results for the Java 25 prototype. Integration tests launch the packaged agent in a separate JVM. See the [thesis checkpoint](thesis-freeze.md) for the evaluated inputs and reference comparison.

Implemented and partial rows cite passing tests for the scope stated. Failing means a test contradicts a claimed behavior. Unsupported means the prototype lacks the behavior. Not tested means the behavior has no qualifying evidence. Not applicable means the behavior falls outside the evaluated feature definition. Only implemented or partial scope contributes passing evidence.

| ID | Feature | Current evidence |
|---|---|---|
| 1 | Preconditions | Implemented for the tested instance methods. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies rejection before mutation and recovery. [CompositionIT](../src/test/java/fixture/CompositionIT.java) verifies inherited applicability and outcome handling. |
| 2 | Postconditions | Implemented for normal returns on the tested instance methods. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies passing and failing checks. [InheritanceIT](../src/test/java/fixture/InheritanceIT.java) verifies inherited checks on normal return. |
| 3 | Class invariants | Partial. [InvariantIT](../src/test/java/fixture/InvariantIT.java) verifies method entry and normal exit, superclass and declaration order, and re-entry protection. [InheritanceIT](../src/test/java/fixture/InheritanceIT.java) verifies an interface invariant at method and construction boundaries. Entry evaluation differs from C4J 6.0.0. Purity-based skipping remains pending. |
| 4 | result() | Implemented for normal returns. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies all primitive types, reference identity, null, void, and cleanup. [InvocationIsolationIT](../src/test/java/fixture/InvocationIsolationIT.java) verifies isolation across nested, recursive, and concurrent calls. |
| 5 | Contract discovery and association | Partial. [DiscoveryIT](../src/test/java/fixture/DiscoveryIT.java) verifies direct association. [InheritanceIT](../src/test/java/fixture/InheritanceIT.java) verifies ordered superclass and interface associations, duplicate removal, diagnostics for invalid bindings, and class-loader isolation. External and inverse discovery remain pending. |
| 6 | Contract violation handling | Partial. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies method phase labels, causes, stack frames, and usage error propagation. [InvariantIT](../src/test/java/fixture/InvariantIT.java) verifies the invariant phase label. Initializer violations remain pending. |
| 7 | Constructor invariant checking | Partial. [InvariantIT](../src/test/java/fixture/InvariantIT.java) verifies simple, delegated, nested, superclass/subclass, and retained-enclosing-instance member constructors, including exceptional exits. See the [Phase 3 notes](agents/plans/2026-11-21-c4j-next-mvp.md#phase-3-class-invariants-and-construction) for limitations. |
| 8 | old() | Partial. [OldExpressionsIT](../src/test/java/fixture/OldExpressionsIT.java) verifies the [supported postcondition expressions](#old-expressions), reference identity, deferred capture failure, and isolation across nested, recursive, and concurrent calls. |
| 9 | Superclass contract inheritance | Implemented for the [tested method boundaries](#inheritance-and-method-boundaries). [InheritanceIT](../src/test/java/fixture/InheritanceIT.java) covers overrides, multiple superclass levels, and inherited implementations. |
| 10 | Interface contract inheritance | Partial. [InheritanceIT](../src/test/java/fixture/InheritanceIT.java) covers direct, inherited, and diamond interface associations, including implementations inherited from a superclass. Default-only bodies and newly applicable checks on inherited final methods have no extra boundary. |
| 11 | Contract merging | Partial. [CompositionIT](../src/test/java/fixture/CompositionIT.java) verifies vertical, horizontal, and mixed checks, their order, and failure handling at one method boundary. Cross-boundary and deeper failure combinations have no parity claim. |
| 12 | LSP behavior | Implemented for the [tested composition scope](thesis-freeze.md#composition-evidence). [CompositionIT](../src/test/java/fixture/CompositionIT.java) verifies both orders of conflicting outcomes, the ordinary all-failure result, and suppression of strengthening preconditions. |
| 13 | unchanged() | Unsupported. |
| 14 | Exceptional method execution | Unsupported for exceptional contract checks and helpers. [InvocationIsolationIT](../src/test/java/fixture/InvocationIsolationIT.java) verifies that exceptional exits clear the frame, skip normal postconditions, and preserve the original application throwable. |
| 15 | @Pure | Unsupported. |
| 16 | Static method contracts | Unsupported. |
| 17 | ignored() | Unsupported. |
| 18 | maxTime() | Unsupported. |
| 19 | Advanced inheritance | Not tested for C4J-compatible failure composition across deeper hierarchies. The basic diamond discovery evidence is in row 10. |
| 20 | External contract discovery | Unsupported. |

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

## Inheritance and method boundaries

`ContractResolver` follows C4J's `InvolvedTypeInspector`. At each node it
adds the node, the already ordered superclass subtree, and the already ordered
interface subtrees in declaration order. It keeps the first occurrence of each
type and then reverses that node's list. For a class that implements three
interfaces where the first two share a parent interface, that parent contributes
one binding. Constructor and method plans use the same traversal.

`ContractExecutor` caches immutable plans in `ClassValue` entries on the
declaring class. It matches methods by name and erased parameter types, with
compatible covariant return types. Concrete overrides receive the applicable
bindings. Compiler bridges and abstract declarations add no separate check.

An inherited method with no new bindings keeps its existing boundary. If a
subclass introduces a contract for an inherited, overridable class method, the
agent adds a synthetic delegate for the new binding. The superclass body keeps
its existing checks. An explicit application call to `super` also enters the
superclass boundary. Interface default methods with no class override and
newly applicable checks on inherited final methods have no delegate.

Within each boundary, the runtime aggregates applicable precondition outcomes,
reports the last failure if all fail, and reports an LSP violation as soon as
the outcomes disagree. A precondition on a descendant contract is omitted
when a contracted ancestor already declares the method. Postconditions and
invariants run in plan order and stop at the first violation. Malformed
bindings use `UsageError`. This prototype requires a
concrete contract class assignable to its annotated type, while the pinned C4J
source can warn and continue for some non-inheriting contract classes.
