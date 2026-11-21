# C4J Next feature support

Day 1 results for the Java 25 prototype. The discovery test launches the packaged agent in a separate JVM. The other rows describe planned functionality.

| ID | Feature | Current evidence |
|---|---|---|
| 1 | Preconditions | Unsupported in Day 1. |
| 2 | Postconditions | Unsupported in Day 1. |
| 3 | Class invariants | Unsupported in Day 1. |
| 4 | result() | Unsupported in Day 1. |
| 5 | Contract discovery and association | Partial. [DiscoveryIT](../src/test/java/fixture/DiscoveryIT.java) verifies direct association on a concrete class. Method dispatch and target injection are pending. |
| 6 | Contract violation handling | Unsupported in Day 1. |
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
