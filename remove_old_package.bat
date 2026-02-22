@echo off
echo ========================================
echo  Removendo Pacote Antigo (alugueldetemporada)
echo ========================================
echo.
echo ATENCAO: Este script vai DELETAR PERMANENTEMENTE
echo todo o pacote antigo "alugueldetemporada".
echo.
echo Isso vai corrigir erros de redeclaracao como:
echo - Redeclaration: class DallEImageGenerator
echo - E outros conflitos de classes duplicadas
echo.
pause

cd "C:\Users\Admin\AndroidStudioProjects\Comidinhas"

echo.
echo Verificando se o pacote antigo existe...
if exist "app\src\main\java\br\com\boddenb\alugueldetemporada\" (
    echo ENCONTRADO! Removendo pacote alugueldetemporada...
    rmdir /s /q "app\src\main\java\br\com\boddenb\alugueldetemporada\"

    if %errorlevel% equ 0 (
        echo.
        echo ========================================
        echo  SUCESSO! Pacote antigo removido.
        echo ========================================
    ) else (
        echo.
        echo ========================================
        echo  ERRO ao remover o pacote.
        echo ========================================
        echo.
        echo Tente fechar o Android Studio e execute novamente.
    )
) else (
    echo.
    echo ========================================
    echo  Pacote antigo NAO ENCONTRADO
    echo ========================================
    echo.
    echo O pacote alugueldetemporada ja foi removido
    echo ou nao existe neste caminho.
)

echo.
echo Verificando estrutura atual...
if exist "app\src\main\java\br\com\boddenb\comidinhas\" (
    echo [OK] Pacote correto "comidinhas" encontrado!
) else (
    echo [ERRO] Pacote "comidinhas" NAO encontrado!
)

echo.
echo ========================================
echo  Proximo passo no Android Studio:
echo ========================================
echo 1. File -^> Invalidate Caches / Restart
echo 2. File -^> Sync Project with Gradle Files
echo 3. Build -^> Rebuild Project

