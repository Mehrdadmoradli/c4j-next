---
date: 2026-11-21
git_commit: b3460cea98c3197d1107fe2962d562449c5dcffc
branch: main
topic: C4J Next Java 25 starting point
tags: [research, java25, instrumentation]
---

# C4J Next baseline

The checkout contains three distinct systems. A successful launch of one cannot establish feature support in another.

| Location | What is present | Reproduction implications |
|---|---|---|
| Root | Maven artifact `io.github.Mehrdadmoradli:c4j-next:0.1.0-SNAPSHOT`. Compiler release 25, Byte Buddy 1.18.15, JUnit Jupiter 5.11.4, Surefire 3.5.2 and Shade 3.6.0. | The manifest selects `C4JNextAgent`. Its current action is printing a startup message. The README is empty and the committed target directory includes generated jars. |
| Pinned dbcagent checkout | Mathis Stein's proof of concept at `221b2971bdde196628488a2f07ca99b4b4fe9c0a`. The analyzed local checkout had a comment-only diff in ContractUtils.java. Source/target 22, Byte Buddy and byte-buddy-agent 1.18.5, Objenesis 3.5, Shade 3.5.0. | Separate POM. Shade sets `Premain-Class` to DbCAgent and also names a Main class absent from the sources. Launch an application with `-ea -javaagent:<shaded-jar>`. Running the jar as the application is a different operation. |
| Pinned C4J 6.0.0 checkout | C4J tag `6.0.0`, commit `c874e8ecf91b1369ca97bea27e4c3cfc060dab63`. The original analysis used its unpacked distribution with .java and .class files, manifest, schemas, and embedded Maven metadata. | The embedded POM declares Java 1.6 and Javassist 3.16.1-GA. The manifest names Javassist 3.18.1-GA. The upstream repository also contains acceptance and system tests. |

The parent repository reports the reference directory as untracked. Preserve the upstream revision, attribution and applicable distribution terms when bringing code into the working project. No license file was located in the dbcagent checkout.

## How a call is checked

Paths below are relative to `src/main/java/com/mat345st/dbcagent/` in the pinned dbcagent checkout recorded in `docs/reference-sources.md`.

1. `agent/DbCAgent.java:20` installs an AgentBuilder. `utils/ContractUtils.java` searches the class, interfaces recursively, then superclass, returning the first binding. The transformer checks that the contract implements or extends the bound specification. It selects concrete, non-native instance methods and constructors.
2. `agent/MethodAdvice.java` resolves the contract again on the receiver, constructs it using its no-arg constructor, injects declared @Target fields and finds a public contract method by name and parameter types. Entry takes a receiver snapshot, records field values, checks declared invariants (unless the contract method is @Pure), then invokes the contract method in the precondition phase.
3. Exit constructs a fresh contract instance, publishes the return value, invokes the contract method in the postcondition phase, then checks invariants. An assertion failure is logged to stderr and unwrapped as AssertionError. Other exceptions from contract evaluation can surface as InvocationTargetException. Java assert expressions depend on enabling assertions in the contract class.
4. `agent/ConstructorAdvise.java` checks declared invariants after normal constructor exits. Constructor chaining and superclass construction need separate fixtures: advice can see a receiver whose most-derived constructor is still running.
5. `runtime/ContractBase.java` exposes phase predicates, result(), old() and unchanged(Object). `runtime/ContractRuntime.java` keeps the guard, phase, current method, snapshot, values and result in separate thread locals. Thread isolation alone does not restore an outer invocation when an inner call exits. Cleanup is on normal paths and can be skipped when a check throws.

## Limits visible in source

- `runtime/Snapshots.java` allocates a receiver without calling its constructor, then reflectively copies all declared fields up the hierarchy. This runs on ordinary calls even when the contract never reads old(). Mutable referents remain shared. Static fields are included and reflective writes to static final fields can fail. Access to private fields across named modules and runtime visibility in isolated class loaders require separate verification.
- `unchanged` indexes old values by field *value*, rather than by field or expression. Equal values from unrelated fields can collide and in-place changes can affect both sides. C4J's `de/vksi/c4j/Condition.java` specifies entry evaluation of an expression for old(T), yielding a primitive value or reference. dbcagent's no-argument receiver snapshot has a different API and semantics.
- First-match discovery can inherit one definition. It cannot combine alternative preconditions, cumulative guarantees or invariants from multiple contracts. Invariant discovery and target injection inspect only declared members. Missing matching public contract methods produce IllegalStateException, including for implementation helpers selected by the broad matcher.
- @Pure suppresses surrounding invariant checks. There is no mutation detector. Static methods are explicitly excluded. ContractBase.ignored() is private and the runtime helper returns a Byte Buddy StackManipulation for non-void methods instead of the application's return value.
- Method exit advice runs on Throwable but never binds the original throwable. A normal postcondition can therefore hide an application exception. Contract creation and reflective lookup are repeated on entry and exit. These are concrete maintainability and behavior concerns; neither a performance measurement nor a general verdict on Byte Buddy versus Javassist follows from them.
- The original C4J `internal/Agent.java` registers a ClassFileTransformer backed by RootTransformer and Javassist. Its Condition API includes exception access and constructor context. Treat those sources as semantic references. Record the modernized comparator as **not tested** until its exact build, launch flags and test corpus are available.

## Java 25 feature evidence

Environment: Windows, Eclipse Temurin OpenJDK 25.0.5+8, Apache Maven 3.10.0. The executable fixtures are in [baseline-probes](baseline-probes/BaselineProbe.java). Each case runs in a fresh JVM with assertions enabled; the recovery case deliberately makes both invalid calls in the same thread. The methods under test belong to Java 25 application classes. These are newly added characterization cases. Neither Maven source tree contained existing tests.

| Feature | dbcagent observation | Boundary of claim |
|---|---|---|
| Preconditions | Negative input raised the expected AssertionError before mutation. Without the agent the same case reported a missing violation. | **Partial**: after catching a violation, a second invalid call escaped checking. |
| Postconditions and result | An intentionally failed postcondition rejected the call after the body changed state. A wrong primitive return was detected. | Passing and failing examples for an int return. Other return types remain untested. |
| Class and constructor invariants | Invalid starting state rejected before method body, invalid ending state rejected on return, invalid constructor argument produced an invariant violation. | Single concrete receiver fixture. Constructor delegation and partially initialized subclasses remain separate cases. |
| Violations | stderr and AssertionError message identify the failed assertion in the tested paths. | Does not guarantee later checks run on that thread. Invariant diagnostics repeat the invariant method name in place of the class. |
| old() | Scalar receiver field example passed; two threads each completed 30 independent updates. Recursive re-entry lost the outer snapshot; array mutation was visible through the saved reference. | **Partial**. No claim of arbitrary entry-expression evaluation, deep copying or correctness with shared mutable receivers. |
| Inheritance | A contract on an interface applied to an implementer and to an overriding subclass reached through its superclass. A second applicable interface's postcondition was missed. | **Partial** for one inherited contract. Merging and LSP-preserving combinations unsupported. A directly annotated superclass is a distinct case. |
| Exceptional exits | The original IllegalArgumentException propagated when the ordinary postcondition happened to succeed. Another call replaced it with a result assertion. | **Failing** for general preservation. No user-facing exceptional-postcondition API. |
| unchanged() | Mutating an array in place was reported as unchanged. | **Failing** for change detection in this case. |
| @Pure | Annotated contract method allowed target mutation. | Invariant suppression exists; purity enforcement unsupported. |
| Static methods / ignored() | Static methods excluded by matcher; ignored() is private on ContractBase. | **Unsupported** by source inspection; no runtime pass counted. |

The original packaged C4J sources establish API intent only. Every modernized-C4J runtime comparison row is **not tested**. A difference between old() and old(expression) should be reported even when both can express the same scalar-field example.
