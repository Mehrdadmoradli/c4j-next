param(
    [Parameter(Mandatory)] [string]$ReferenceRoot,
    [Parameter(Mandatory)] [string]$LegacyLib,
    [string]$NextJar = (Join-Path $PSScriptRoot '../../../target/c4j-next-0.1.0-SNAPSHOT.jar'),
    [string]$MavenCache = (Join-Path $env:USERPROFILE '.m2/repository'),
    [switch]$IncludeEntryDependencies
)

$source = (Resolve-Path -LiteralPath $ReferenceRoot).Path
$legacy = (Resolve-Path -LiteralPath $LegacyLib).Path
$cache = (Resolve-Path -LiteralPath $MavenCache).Path
$next = (Resolve-Path -LiteralPath $NextJar).Path
$fixture = Join-Path $PSScriptRoot '../../../src/test/java/fixture/CompositionApp.java'
$pinned = 'c874e8ecf91b1369ca97bea27e4c3cfc060dab63'
$revision = git -c "safe.directory=$($source.Replace('\','/'))" -C $source rev-parse HEAD
if ($LASTEXITCODE -or $revision -ne $pinned) {
    throw "Expected the pinned C4J checkout at $pinned."
}
$changes = git -c "safe.directory=$($source.Replace('\','/'))" -C $source status --porcelain -- c4j/src
if ($LASTEXITCODE -or $changes) {
    throw "The pinned C4J sources must be clean."
}

$work=Join-Path $env:TEMP ('c4j-phase7-oracle-'+[guid]::NewGuid())
$classes=Join-Path $work 'classes'
New-Item -ItemType Directory -Path $classes | Out-Null
Write-Output "Comparison workspace: $work"
Copy-Item "$legacy/javassist-3.33.0-GA.jar" "$work/javassist.jar"
Copy-Item "$legacy/log4j-1.2.16.jar" "$work/log4j.jar"
$support="$legacy/c4j-6.0.0-for-java-8.jar"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($support)
try {
 foreach($entry in $zip.Entries) {
   if ($entry.FullName -like 'de/vksi/c4j/internal/configuration/*.class') {
      $destination=Join-Path $classes $entry.FullName
      New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
      [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$destination)
   }
 }
} finally { $zip.Dispose() }
$javaSource=Join-Path $source 'c4j/src/main/java'
$sourcePaths=Get-ChildItem $javaSource -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }
[IO.File]::WriteAllLines("$work/sources.txt",[string[]]$sourcePaths)
$cp=@("$classes","$work/javassist.jar","$work/log4j.jar") -join [IO.Path]::PathSeparator
& javac --release 8 -cp $cp -d $classes "@$work/sources.txt"
if ($LASTEXITCODE) { throw "Compiling the pinned reference failed." }
Copy-Item "$source/c4j/src/main/resources/*" $classes
[IO.File]::WriteAllText("$work/MANIFEST.MF","Manifest-Version: 1.0`r`nPremain-Class: de.vksi.c4j.internal.Agent`r`nBoot-Class-Path: javassist.jar log4j.jar`r`n`r`n")
$oracle="$work/pinned-c4j.jar"
& jar cfm $oracle "$work/MANIFEST.MF" -C $classes .
if ($LASTEXITCODE) { throw "Packaging the pinned reference failed." }

$separator = [IO.Path]::PathSeparator
$referenceDependencies = @(
    "$legacy/jakarta.xml.bind-api-2.3.3.jar"
    "$cache/org/glassfish/jaxb/jaxb-runtime/2.3.6/jaxb-runtime-2.3.6.jar"
    "$cache/org/glassfish/jaxb/txw2/2.3.6/txw2-2.3.6.jar"
    "$cache/com/sun/istack/istack-commons-runtime/3.0.12/istack-commons-runtime-3.0.12.jar"
    "$cache/com/sun/activation/jakarta.activation/1.2.2/jakarta.activation-1.2.2.jar"
)
foreach ($dependency in $referenceDependencies) {
    if (!(Test-Path -LiteralPath $dependency -PathType Leaf)) {
        throw "Missing reference dependency $dependency"
    }
}

function Test-Composition([string]$Name, [string]$AgentJar, [string]$FixtureSource,
                          [string[]]$RuntimeDependencies, [string[]]$JvmOptions) {
    $run = Join-Path $work $Name
    New-Item -ItemType Directory -Path $run | Out-Null
    $java = Join-Path $run 'CompositionApp.java'
    [IO.File]::WriteAllText($java, $FixtureSource)

    & javac --release 8 -cp $AgentJar -d $run $java
    if ($LASTEXITCODE) {
        throw "Compiling the $Name fixture failed."
    }

    $runtime = (@($run, $AgentJar) + $RuntimeDependencies) -join $separator
    $fixtureOptions = @()
    if (!$IncludeEntryDependencies) {
        $fixtureOptions += '--skip-entry-dependencies'
    }
    & java -ea @JvmOptions "-javaagent:$AgentJar" -cp $runtime fixture.CompositionApp @fixtureOptions > "$run/stdout.log" 2> "$run/stderr.log"
    $status = $LASTEXITCODE
    $output = Get-Content "$run/stdout.log", "$run/stderr.log"
    $transformErrors = $output -match "Transformation failed for class 'fixture\.CompositionApp|C4J Next could not transform"
    if ($status -or $output -notcontains 'contract composition passed' -or $transformErrors) {
        $output | Select-Object -Last 60
        throw "The $Name comparison failed. Logs are in $run."
    }
    Write-Output "$Name passed the composition fixture."
}

$nextSource = Get-Content -Raw -LiteralPath $fixture
$referenceSource = $nextSource.Replace('io.github.Mehrdadmoradli.c4jnext.annotations.', 'de.vksi.c4j.')
$referenceSource = $referenceSource.Replace('io.github.Mehrdadmoradli.c4jnext.', 'de.vksi.c4j.')
Test-Composition 'reference' $oracle $referenceSource $referenceDependencies @('--add-opens=java.base/java.lang=ALL-UNNAMED')
Test-Composition 'next' $next $nextSource @() @()
