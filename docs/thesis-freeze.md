# Phase 7 thesis checkpoint

The checkpoint `phase-7-thesis-freeze` covers the mandatory and high-priority Java 25 prototype on 2026-11-23. The Git base is `1c9ba4a6b7da564d8d9e14137ca87835205993d4`. The tested Phase 7 changes are in the working tree, so this base identifies the parent of the evaluated changes. The SHA-256 fingerprint for the exact 37 build inputs is `460338ec7fc5daad0121fe8e44d73dc67bc66503a27de6975ce709e4d5385d38`. Record the Phase 7 commit ID after the reviewed changes are committed.

## Reproduce the prototype

The verification used Windows 11, Eclipse Temurin 25.0.5+8, Maven 3.10.0, Byte Buddy 1.18.15, ASM 9.10.1, ASM Commons and ASM Tree 9.7, and JUnit Jupiter 5.11.4. The source and test release is Java 25. From a clean checkout root:

```powershell
mvn clean verify
if ($LASTEXITCODE) { exit $LASTEXITCODE }
mvn clean verify
if ($LASTEXITCODE) { exit $LASTEXITCODE }
```

Both runs passed all seven Failsafe integration tests. Verification used an isolated copy of the project root with the existing Maven cache and `-o`. Each agent fixture runs in a fresh JVM with `-ea` and `-javaagent:target/c4j-next-0.1.0-SNAPSHOT.jar`. The fixture classpath contains the test classes and shaded JAR. `DiscoveryIT` also runs a control without the agent and uses `=debug` for discovery output. The remaining tests use the default configuration. Named tests and their supported scope are in the [feature matrix](feature-support.md).

The fingerprint hashes the files under `src` and `pom.xml` as read for the build. It hashes the UTF-8 text of sorted `relative/path sha256` rows separated by LF, without a trailing newline. Individual hashes use the raw file bytes, so keep checkout line endings the same when comparing fingerprints.

```powershell
$root = (Get-Location).Path
$files = @(Get-Item pom.xml) + @(Get-ChildItem src -Recurse -File)
$rows = foreach ($file in $files | Sort-Object FullName) {
    $path = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
    "$path $((Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToLowerInvariant())"
}
$sha = [Security.Cryptography.SHA256]::Create()
try {
    -join ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($rows -join "`n")) |
        ForEach-Object { $_.ToString('x2') })
} finally {
    $sha.Dispose()
}
```

## Composition evidence

The [comparison script](agents/research/compare-composition.ps1) builds the pinned [C4J source](reference-sources.md), then runs [CompositionApp](../src/test/java/fixture/CompositionApp.java) against both agents. The fixtures differ only in their API imports. Both are compiled with `javac --release 8` and run on Java 25 with `-ea`, `-javaagent`, and `--skip-entry-dependencies` as a fixture argument. The reference run also opens `java.base/java.lang` to unnamed modules.

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File docs/agents/research/compare-composition.ps1 `
    -ReferenceRoot 'C:\refs\C4J' -LegacyLib 'C:\refs\C4J-libs'
```

The reference uses Javassist 3.33.0-GA and Log4j 1.2.16 on its agent boot classpath. The supplied `c4j-6.0.0-for-java-8.jar` contributes just the generated JAXB configuration classes. Its SHA-256 is `60245e6dd7136425f4304cc79638ebf2e69ebd5b0ab6520c209fa77953cfa2a7`. The legacy library directory must also contain `jakarta.xml.bind-api-2.3.3.jar`. The script uses JAXB Runtime and TXW2 2.3.6, iStack Commons Runtime 3.0.12, and Jakarta Activation 1.2.2 from the Maven cache. Use `-MavenCache` or `-NextJar` to point to a different cache or shaded JAR.

| Fixture | Compared behavior |
|---|---|
| Vertical superclass and subclass | The ancestor declares the signature, so C4J suppresses the descendant's explicit precondition. Both postconditions still run in order. A rejected ancestor precondition blocks the body. |
| Horizontal two-interface implementation | Independent preconditions run in discovery order. All pass enters the body, all fail reports the last failure, and mixed outcomes report an LSP violation. |
| Mixed superclass and two interfaces | The first disagreement blocks the body and remaining preconditions. On normal return, postconditions and invariants run in discovery order and each stops at its first violation. |

The packaged-agent test also covers a supported `old()` entry dependency participating in the precondition result. C4J's `PreAndPostConditionTransformer.getPreConditions()` includes methods with such dependencies. Executable parity for this case is unavailable on the verification setup because the legacy rewrite produces a `VerifyError` for a missing stack map frame. Run the comparison with `-IncludeEntryDependencies` to reproduce that limitation.

## Architecture and limits

Method advice drives a thread-local stack of invocation frames. An immutable plan carries ordered checks at each boundary and holds the association metadata outside the invocation. Entry snapshots retain object identity. Constructor instrumentation tracks receiver initialization and checks invariants after successful outermost construction. Byte Buddy drives method instrumentation and ASM handles constructor boundaries and `old()` call sites.

The comparison covers C4J's default precondition policy with violations thrown. C4J Next also evaluates ordinary invariants at method entry as required by Phase 3. The reference runs ordinary invariant bodies at exit. C4J Next uses contract instances per invocation and pairs synthetic delegates with separate superclass boundaries. Preconditions across a generated delegate and the superclass body are evaluated in their respective frames. Exact composition across those frames has no parity claim. The same applies to deeper mixtures of interface and superclass failures and to explicit `super` calls that enter another instrumented method. Basic diamond discovery is covered by `InheritanceIT`.

A default-only interface body and a newly applicable check on an inherited final method have no added method boundary. Contracts on exceptional exits, constructor and invariant `old()`, purity, static checks, external association, configurable C4J violation actions, and arbitrary expression capture remain outside the evaluated subset. See the [feature matrix](feature-support.md) for narrower supported cases and test evidence.

## Implementation effort

| Phase | Planned development days | Recorded elapsed session effort |
|---|---:|---|
| Agent and public API | 1 | Build evidence recorded |
| Method contracts | 1 | About five minutes |
| Invariants and construction | 1 | About ten minutes |
| Invocation isolation | 1 | A few minutes |
| Old expressions | 2 | Several minutes |
| Inheritance discovery | 1 | Several minutes |
| Composition and checkpoint | 1 | Several minutes |

The elapsed notes describe implementation sessions. They cover a different measure from the planned development days. The detailed work and verification for each phase are in the [implementation plan](agents/plans/2026-11-21-c4j-next-mvp.md).
