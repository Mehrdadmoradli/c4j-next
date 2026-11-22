# C4J Next feature support

Phase 2 results for the Java 25 prototype. Integration tests launch the packaged agent in a separate JVM.

| ID | Feature | Current evidence |
|---|---|---|
| 1 | Preconditions | Implemented for the direct instance-method scope. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies rejection before mutation and repeated enforcement after caught violations. |
| 2 | Postconditions | Implemented for normal returns in the direct instance-method scope. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies passing and failing checks. |
| 3 | Class invariants | Unsupported in Day 1. |
| 4 | result() | Implemented for normal returns. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies all primitive types, reference identity, null, void, and cleanup. |
| 5 | Contract discovery and association | Partial. [DiscoveryIT](../src/test/java/fixture/DiscoveryIT.java) verifies direct association on a concrete class. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies overloads, an inherited contract method, and target injection. |
| 6 | Contract violation handling | Partial. [MethodContractsIT](../src/test/java/fixture/MethodContractsIT.java) verifies method phase labels, causes, stack frames, and usage error propagation. Invariant and initializer violations remain pending. |
| 7 | Constructor invariant checking | Unsupported in Day 1. |
| 8 | old() | Unsupported in Day 1. |
| 9 | Superclass contract inheritance | Unsupported in Day 1. |
| 10 | Interface contract inheritance | Unsupported in Day 1. |
| 11 | Contract merging | Unsupported in Day 1. |
| 12 | LSP behavior | Unsupported in Day 1. |
| 13 | unchanged() | Unsupported in Day 1. |
| 14 | Exceptional method execution | Unsupported in Day 1. |
| 15 | @Pure | Unsupported in Day 1. |
| 16 | Static method contracts | Unsupported in Day 1. |
| 17 | ignored() | Unsupported in Day 1. |
| 18 | maxTime() | Unsupported in Day 1. |
| 19 | Advanced inheritance | Unsupported in Day 1. |
| 20 | External contract discovery | Unsupported in Day 1. |
