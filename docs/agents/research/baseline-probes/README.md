# dbcagent characterization on Java 25

Run from the repository root with JDK 25 on PATH. Pass the shaded JAR from a build of the pinned dbcagent revision in `docs/reference-sources.md`. The JAR under the root project's target directory has a different API.

```powershell
docs/agents/research/baseline-probes/run.cmd path/to/DbCAgent-1.0-SNAPSHOT.jar
```

The runner compiles the fixture into a fresh temporary directory and launches a JVM with assertions enabled for each scenario. The process exits with status 1 when any scenario fails. The `recovery` case deliberately performs both invalid calls on the same thread in the same JVM.

For the source-based run, we compiled the unmodified reference sources for Java 22 with JDK 25, reproduced the POM's premain entry in a thin JAR, and supplied its runtime dependencies on the fixture classpath. The reference POM also declares byte-buddy-agent. The premain path used here needs only Byte Buddy core and Objenesis. Equivalent commands on Windows with the pinned dependencies in the local Maven cache are below.

```powershell
$work = New-Item -ItemType Directory -Path (Join-Path $env:TEMP ([guid]::NewGuid().ToString()))
$dbc = Join-Path $work 'dbcagent'
git clone git@gitlab.git.nrw:mathis.stein/dbcagent.git $dbc
git -C $dbc checkout 221b2971bdde196628488a2f07ca99b4b4fe9c0a
$repo = Join-Path $env:USERPROFILE '.m2/repository'
$bb = Join-Path $repo 'net/bytebuddy/byte-buddy/1.18.5/byte-buddy-1.18.5.jar'
$obj = Join-Path $repo 'org/objenesis/objenesis/3.5/objenesis-3.5.jar'
$sources = @(Get-ChildItem (Join-Path $dbc 'src/main/java') -Recurse -Filter *.java | ForEach-Object FullName)
javac --release 22 -cp "$bb;$obj" -d "$work/classes" $sources
'Premain-Class: com.mat345st.dbcagent.agent.DbCAgent' | Set-Content "$work/MANIFEST.MF" -Encoding ascii
jar cfm "$work/dbc.jar" "$work/MANIFEST.MF" -C "$work/classes" .
docs/agents/research/baseline-probes/run.cmd "$work/dbc.jar" $bb $obj
```

Observed on Temurin 25.0.5 with Java 25 application bytecode:

| Result | Scenarios |
| --- | --- |
| Passed (10) | pre, post, badResult, ctor, entryInv, exitInv, scalar, thread, superclass, exception |
| Failed (7) | recovery, nested, mutable, unchanged, exceptionMask, merge, purity |

`thread` exercises two threads with separate receivers. `superclass` exercises a subclass override whose superclass implements the annotated interface. `exception` has a normal postcondition that coincidentally accepts the default primitive result during exceptional exit. `exceptionMask` changes the receiver state so that the same postcondition fails and replaces the application exception. These results describe the fixtures above. They do not establish comprehensive concurrency, inheritance or exceptional-exit support.
