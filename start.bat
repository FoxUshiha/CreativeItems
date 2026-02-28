@echo off
title Compilador CreativeItem v1.0 - Java 17

echo ============================================
echo Compilador do Plugin CreativeItem
echo (Sistema de Gerenciamento de Itens Criativos)
echo ============================================
echo.

echo Procurando Java 17 instalado...
echo.

set JDK_PATH=

rem Procura JDK 17 em locais comuns
for /d %%i in ("C:\Program Files\Java\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Java\jdk17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\AdoptOpenJDK\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\OpenJDK\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Amazon Corretto\jdk17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Microsoft\jdk-17*") do set JDK_PATH=%%i

if "%JDK_PATH%"=="" (
    echo ============================================
    echo ERRO: JDK 17 nao encontrado!
    echo Instale o Java 17 JDK e tente novamente.
    echo ============================================
    pause
    exit /b 1
)

echo Java 17 encontrado em: %JDK_PATH%
echo.

set JAVAC="%JDK_PATH%\bin\javac.exe"
set JAR="%JDK_PATH%\bin\jar.exe"

echo ============================================
echo Preparando ambiente de compilacao...
echo ============================================
echo.

echo Limpando pasta out...
if exist out (
    rmdir /s /q out >nul 2>&1
)
mkdir out
mkdir out\com
mkdir out\com\foxsrv
mkdir out\com\foxsrv\creativeitem

echo.
echo ============================================
echo Verificando dependencias...
echo ============================================
echo.

REM Verificar Spigot API
if not exist spigot-api-1.20.1-R0.1-SNAPSHOT.jar (
    echo [ERRO] spigot-api-1.20.1-R0.1-SNAPSHOT.jar nao encontrado!
    echo.
    echo Certifique-se de que o arquivo spigot-api-1.20.1-R0.1-SNAPSHOT.jar esta na pasta raiz.
    pause
    exit /b 1
) else (
    echo [OK] Spigot API encontrado
    set SPIGOT_PATH=spigot-api-1.20.1-R0.1-SNAPSHOT.jar
)

REM Verificar Vault API (opcional para este plugin)
if not exist Vault.jar (
    echo [AVISO] Vault.jar nao encontrado na pasta raiz!
    echo O plugin CreativeItem nao requer Vault, mas pode ser usado para integracoes futuras.
    echo Continuando compilacao normalmente...
    echo.
    set VAULT_PATH=
) else (
    echo [OK] Vault API encontrado (opcional)
    set VAULT_PATH=Vault.jar
)

REM Verificar CoinCard API (opcional para este plugin)
if not exist CoinCard.jar (
    echo [AVISO] CoinCard.jar nao encontrado na pasta raiz!
    echo O plugin CreativeItem nao requer CoinCard, mas pode ser usado para integracoes futuras.
    echo Continuando compilacao normalmente...
    echo.
    set COINCARD_PATH=
) else (
    echo [OK] CoinCard API encontrado (opcional)
    set COINCARD_PATH=CoinCard.jar
)

echo.
echo ============================================
echo Compilando CreativeItem...
echo ============================================
echo.

REM Montar classpath
set CLASSPATH="%SPIGOT_PATH%"
if defined COINCARD_PATH (
    set CLASSPATH=%CLASSPATH%;CoinCard.jar
)
if defined VAULT_PATH (
    set CLASSPATH=%CLASSPATH%;Vault.jar
)

REM Mostrar classpath para debug
echo Classpath: %CLASSPATH%
echo.

REM Verificar se o arquivo fonte existe
if not exist src\com\foxsrv\creativeitem\CreativeItem.java (
    echo ============================================
    echo ERRO: Arquivo fonte nao encontrado!
    echo ============================================
    echo.
    echo Caminho esperado: src\com\foxsrv\creativeitem\CreativeItem.java
    echo.
    echo Estrutura de diretorios atual:
    echo.
    if exist src (
        echo Conteudo de src:
        dir /s /b src
    ) else (
        echo Pasta src nao encontrada!
    )
    echo.
    echo Criando estrutura de diretorios necessaria...
    mkdir src\com\foxsrv\creativeitem 2>nul
    echo Por favor, coloque o arquivo CreativeItem.java em src\com\foxsrv\creativeitem\
    pause
    exit /b 1
)

REM Compilar com as dependências necessárias
echo Compilando CreativeItem.java...
%JAVAC% --release 17 -d out ^
-classpath %CLASSPATH% ^
-sourcepath src ^
src\com\foxsrv\creativeitem\CreativeItem.java

if %errorlevel% neq 0 (
    echo ============================================
    echo ERRO AO COMPILAR O PLUGIN!
    echo ============================================
    echo.
    echo Verifique os erros acima e corrija o codigo.
    echo.
    echo Possiveis causas:
    echo 1 - Erro de sintaxe no codigo
    echo 2 - Dependencias faltando
    echo 3 - Versao do Java incorreta
    pause
    exit /b 1
)

echo.
echo Compilacao concluida com sucesso!
echo.

echo ============================================
echo Copiando arquivos de recursos...
echo ============================================
echo.

REM Copiar plugin.yml
if exist resources\plugin.yml (
    copy resources\plugin.yml out\ >nul
    echo [OK] plugin.yml copiado
) else (
    echo [AVISO] plugin.yml nao encontrado em resources\
    echo Criando plugin.yml padrao...
    
    (
        echo name: CreativeItem
        echo version: 1.0
        echo main: com.foxsrv.creativeitem.CreativeItem
        echo api-version: 1.20
        echo author: FoxOficial2
        echo description: Creative item management system
        echo.
        echo commands:
        echo   creativeitem:
        echo     description: Main creative item command
        echo     usage: /creativeitem remove
        echo     aliases: [ci]
        echo   ci:
        echo     description: Alias for /creativeitem
        echo     usage: /ci remove
        echo.
        echo permissions:
        echo   creativeitem.admin:
        echo     description: Allows bypassing creative item restrictions
        echo     default: op
    ) > out\plugin.yml
    echo [OK] plugin.yml criado automaticamente
)

REM Copiar config.yml
if exist resources\config.yml (
    copy resources\config.yml out\ >nul
    echo [OK] config.yml copiado
) else (
    echo [AVISO] config.yml nao encontrado em resources\
    echo Criando config.yml padrao...
    
    (
        echo # CreativeItem Configuration
        echo.
        echo # Lore text for creative items
        echo CreativeItemLore: "&c&lCreative Item"
        echo.
        echo # Whether to log creative item removal
        echo LogRemovals: true
        echo.
        echo # Whether to allow admins to bypass restrictions
        echo AdminBypass: true
    ) > out\config.yml
    echo [OK] config.yml criado automaticamente
)

echo.
echo ============================================
echo Criando arquivo JAR...
echo ============================================
echo.

cd out

REM Criar JAR com todos os recursos
echo Criando CreativeItem.jar...
%JAR% cf CreativeItem.jar com plugin.yml config.yml

cd ..

echo.
echo ============================================
echo PLUGIN COMPILADO COM SUCESSO!
echo ============================================
echo.
echo Arquivo gerado: out\CreativeItem.jar
echo.
dir out\CreativeItem.jar
echo.
echo ============================================
echo RESUMO DA COMPILACAO:
echo ============================================
echo.
echo - Data/Hora: %date% %time%
echo - Java Version: 17
echo - Spigot API: OK
if defined COINCARD_PATH (
    echo - CoinCard API: OK (opcional)
) else (
    echo - CoinCard API: NAO ENCONTRADO (opcional)
)
if defined VAULT_PATH (
    echo - Vault API: OK (opcional)
) else (
    echo - Vault API: NAO ENCONTRADO (opcional)
)
echo - Arquivo fonte: src\com\foxsrv\creativeitem\CreativeItem.java
echo.
echo ============================================
echo IMPORTANTE - REQUISITOS PARA EXECUCAO:
echo ============================================
echo.
echo 1 - O plugin nao requer dependencias externas
echo 2 - Apenas Spigot/Paper 1.20+ necessario
echo.
echo ============================================
echo Para instalar:
echo ============================================
echo.
echo 1 - Copie out\CreativeItem.jar para a pasta plugins do servidor
echo 2 - Reinicie o servidor ou use /reload confirm
echo 3 - Edite plugins/CreativeItem/config.yml se necessario
echo.
echo ============================================
echo Comandos basicos apos instalacao:
echo ============================================
echo.
echo /ci - Mostra ajuda
echo /ci remove - Remove a tag creative do item na mao
echo.
echo ============================================
echo Funcionalidades:
echo ============================================
echo.
echo - Itens pegos no criativo ganham tag automaticamente
echo - Nao pode dropar itens criativos
echo - Nao pode colocar em baus/containers
echo - Nao pode equipar itens criativos
echo - Inventario salvo ao entrar no criativo
echo - Inventario restaurado ao sair do criativo
echo - Permissao creativeitem.admin para bypass
echo.
echo ============================================
echo.

pause