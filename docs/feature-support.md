# C4J Next feature support

Phase 4 results for the Java 25 prototype. Integration tests launch the packaged agent in a separate JVM.

| ID | Feature | Current evidence |
|---|---|---|
| 1 | Preconditions | Implemented for the direct instance-method scope. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies rejection before mutation and repeated enforcement after caught violations. |
| 2 | Postconditions | Implemented for normal returns in the direct instance-method scope. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies passing and failing checks. |
| 3 | Class invariants | Partial. [InvariantIT](../src/test/java/fixture/InvariantIT.java) verifies method entry and normal exit, superclass and declaration order, and re-entry protection. Entry evaluation is a documented difference from C4J 6.0.0. Interface association and purity-based skipping remain pending. |
| 4 | result() | Implemented for normal returns. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies all primitive types, reference identity, null, void, and cleanup. [InvocationIsolationIT](../src/test/java/fixture/InvocationIsolationIT.java) verifies isolation across nested, recursive, and concurrent calls. |
| 5 | Contract discovery and association | Partial. [DiscoveryIT](../src/test/java/fixture/DiscoveryIT.java) verifies direct association on a concrete class. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies overloads, an inherited contract method, and target injection. |
| 6 | Contract violation handling | Partial. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies method phase labels, causes, stack frames, and usage error propagation. [InvariantIT](../src/test/java/fixture/InvariantIT.java) verifies the invariant phase label. Initializer violations remain pending. |
| 7 | Constructor invariant checking | Partial. [InvariantIT](../src/test/java/fixture/InvariantIT.java) verifies simple, delegated, nested, superclass/subclass, and retained-enclosing-instance member constructors, including exceptional exits. See the [Phase 3 notes](agents/plans/2026-11-21-c4j-next-mvp.md#phase-3-class-invariants-and-construction) for limitations. |
| 8 | old() | Unsupported in Day 1. |
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
