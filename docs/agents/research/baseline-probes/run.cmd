@echo off
setlocal
if "%~1"=="" (
    echo Usage: run.cmd path-to-shaded-dbcagent.jar [byte-buddy.jar objenesis.jar]
    exit /b 2
)
set "agent=%~f1"
set "probeClasses=%TEMP%\dbcagent-probe-%RANDOM%-%RANDOM%"
mkdir "%probeClasses%" || exit /b 2
javac --release 25 -cp "%agent%" -d "%probeClasses%" "%~dp0BaselineProbe.java" || exit /b 2
set "failures=0"
for %%s in (pre post badResult ctor entryInv exitInv scalar thread superclass exception recovery nested mutable unchanged exceptionMask merge purity) do (
    java -ea "-javaagent:%agent%" -cp "%probeClasses%;%agent%;%~2;%~3" BaselineProbe %%s
    if errorlevel 1 (
        echo FAIL %%s
        set /a failures+=1 >nul
    )
)
echo %failures% failed of 17 probes
if %failures% gtr 0 exit /b 1
exit /b 0
