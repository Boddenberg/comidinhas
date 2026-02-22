@echo off
REM set_java_env.bat
REM Usage:
REM   set_java_env.bat [path-to-jdk] [--persist]
REM Examples:
REM   set_java_env.bat "C:\Program Files\Java\jdk-25"
REM   set_java_env.bat --persist

.\gradlew.bat clean assembleDebug --no-daemonset "ARG=%~1"
set "PERSIST=%~2"

if defined ARG (
    if "%ARG%"=="--persist" (
        set "ARG="
    ) else (
        set "JAVA_HOME=%~1"
    )
)

REM Try automatic discovery when no path provided
if not defined JAVA_HOME (
    rem try Program Files folders for jdk-25*
    for /D %%D in ("%ProgramFiles%\Java\jdk-25*") do (
        set "JAVA_HOME=%%~fD"
        goto :FOUND_JAVA_HOME
    )
    if not "%ProgramFiles(x86)%"=="" (
        for /D %%D in ("%ProgramFiles(x86)%\Java\jdk-25*") do (
            set "JAVA_HOME=%%~fD"
            goto :FOUND_JAVA_HOME
        )
    )

    rem try to find java from PATH (where java)
    for /f "usebackq tokens=*" %%F in (`where java 2^>nul`) do (
        set "JAVA_EXE=%%~fF"
        goto :HAVE_JAVA_EXE
    )
)

:FOUND_JAVA_HOME
if defined JAVA_HOME goto :SET_SESSION

:HAVE_JAVA_EXE
if defined JAVA_EXE (
    rem JAVA_EXE likely ends with \bin\java.exe -> remove \bin\java.exe to get JAVA_HOME
    set "_p=%JAVA_EXE%"
    rem remove the trailing \java.exe or /java.exe
    for %%A in ("%_p%") do set "_dir=%%~dpA"
    rem _dir ends with \ (e.g. C:\Program Files\Java\jdk-25\bin\)
    if defined _dir (
        rem strip trailing \bin\ if present
        set "_dir2=%_dir:~0,-1%"
        if /I "%_dir2:~-4%"=="\bin" (
            set "JAVA_HOME=%_dir2:~0,-4%"
        ) else (
            rem fallback to parent dir
            set "JAVA_HOME=%_dir2%"
        )
    )
)

if not defined JAVA_HOME (
    echo.
    echo ERROR: Nao foi possivel localizar automaticamente o JDK 25.
    echo Informe o caminho para a instalação do JDK como primeiro parametro.
    echo Exemplo: set_java_env.bat "C:\\Program Files\\Java\\jdk-25"
    echo.
    pause
    exit /b 1
 )

:SET_SESSION
echo Setting JAVA_HOME to: %JAVA_HOME%

REM Export for current session (cmd only)
set "JAVA_HOME=%JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo.
echo Java now:
java -version
echo.

if /I "%PERSIST%"=="--persist" (
    echo Persisting JAVA_HOME to current user environment (setx)...
    echo NOTE: setx affects future terminal sessions. You will need to restart terminals/Android Studio to pick it up.
    setx JAVA_HOME "%JAVA_HOME%" >nul
    echo JAVA_HOME persisted for the current user.
    echo Please add %%JAVA_HOME%%\bin to your System PATH manually if needed (recommended via System > Environment Variables).
    echo Example command (use with caution):
    echo setx PATH "%%JAVA_HOME%%\bin;%%PATH%%"
) else (
    echo Temporary session only: JAVA_HOME set for this CMD window.
)

echo.
echo Now you can run gradle, e.g.:
echo    .\gradlew.bat assembleDebug
echo or reopen Android Studio and Sync Project with Gradle Files.

exit /b 0
