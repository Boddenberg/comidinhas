@echo off
echo ========================================
echo  Comidinhas - Gerar APK para Enviar
echo ========================================
echo.

echo [1/2] Configurando ambiente...
REM Configurar JAVA_HOME
if exist "%LOCALAPPDATA%\Programs\Android\Android Studio\jbr" (
    set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android\Android Studio\jbr"
) else if exist "C:\Program Files\Android\Android Studio\jbr" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
) else if exist "%ProgramFiles%\Android\Android Studio\jbr" (
    set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
) else (
    echo ERRO: Java do Android Studio nao encontrado!
    pause
    exit /b 1
)

echo OK
echo.

echo [2/2] Gerando APK...
call gradlew.bat assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERRO ao gerar APK!
    pause
    exit /b 1
)

set APK_PATH=app\build\outputs\apk\debug\app-debug.apk

if not exist "%APK_PATH%" (
    echo ERRO: APK nao foi gerado!
    pause
    exit /b 1
)

echo.
echo ========================================
echo  SUCESSO! APK Gerado
echo ========================================
echo.
echo O APK esta em:
echo %APK_PATH%
echo.
echo Tamanho do arquivo:
for %%A in ("%APK_PATH%") do echo %%~zA bytes (aprox. %%~zA / 1048576 MB)
echo.
echo ========================================
echo  COMO INSTALAR NO CELULAR:
echo ========================================
echo.
echo OPCAO 1 - WhatsApp/Telegram:
echo   1. Envie o arquivo APK para si mesmo
echo   2. Abra no celular e instale
echo.
echo OPCAO 2 - Google Drive/Dropbox:
echo   1. Faca upload do APK
echo   2. Baixe no celular e instale
echo.
echo OPCAO 3 - Email:
echo   1. Anexe o APK em um email para voce mesmo
echo   2. Abra no celular e instale
echo.
echo OPCAO 4 - Servidor Local (sem internet):
echo   Execute: servir_apk.bat
echo   Acesse no celular: http://[IP-DO-PC]:8000
echo.
echo IMPORTANTE:
echo   - Ative "Fontes Desconhecidas" ou "Instalar apps desconhecidos"
echo   - No Android 8+: Configuracoes ^> Apps ^> Acesso especial ^>
echo     Instalar apps desconhecidos ^> Selecione o navegador/WhatsApp
echo.
echo Abrindo pasta do APK...
start "" "%~dp0app\build\outputs\apk\debug"
echo.

pause

