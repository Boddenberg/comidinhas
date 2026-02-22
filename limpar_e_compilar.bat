@echo off
echo ========================================
echo  Limpando Cache e Recompilando
echo ========================================

echo.
echo Limpando build...
call gradlew.bat clean --no-daemon

echo.
echo Deletando cache Gradle...
rmdir /s /q .gradle 2>nul
rmdir /s /q app\build 2>nul
rmdir /s /q build 2>nul

echo.
echo Recompilando projeto...
call gradlew.bat assembleDebug --no-daemon

echo.
echo ========================================
echo  Concluido!
echo ========================================
pause

