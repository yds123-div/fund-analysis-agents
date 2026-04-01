@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

:: ============================================
:: Fund Analysis Agents - One-click Setup Script
:: Windows version
:: ============================================

echo.
echo   _____ _   _ _   _ ____       _    ____  _____ _   _ _____ ____
echo  ^|  ___^| ^| ^| ^| \ ^| ^|  _ \     / \  / ___^|^| ____^| \ ^| ^|_   _/ ___^|
echo  ^| ^|_  ^| ^| ^| ^|  \^| ^| ^| ^| ^|   / _ \^| ^|  _ ^|  _  ^|  \^| ^| ^| ^| \___ \
echo  ^|  _^| ^| ^|_^| ^| ^|\  ^| ^|_^| ^|  / ___ \ ^|_^| ^|^| ^|___^| ^|\  ^| ^| ^|  ___) ^|
echo  ^|_^|    \___/^|_^| \_^|____/  /_/   \_\____^|^|_____^|_^| \_^| ^|_^| ^|____/
echo.
echo  Fund Analysis Agents - One-click Setup (Windows)
echo  ========================================
echo.

set "PROJECT_DIR=%~dp0.."
set "ENV_FILE=%PROJECT_DIR%\.env"
set "TOTAL_STEPS=6"
set "ERRORS=0"

:: ============================================
:: Step 1: Check prerequisites
:: ============================================
echo [1/%TOTAL_STEPS%] Checking prerequisites...

:: Docker
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker not found. Please install Docker Desktop:
    echo         https://docs.docker.com/desktop/install/windows-install/
    set /a ERRORS+=1
) else (
    for /f "tokens=3" %%v in ('docker --version 2^>nul') do echo [INFO]  Docker: %%v
)

:: Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java not found. Please install JDK 21:
    echo         https://adoptium.net/
    set /a ERRORS+=1
) else (
    for /f "tokens=3 delims= " %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        echo [INFO]  Java: %%~v
    )
)

:: Maven
mvn --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Maven not found. Please install:
    echo         https://maven.apache.org/install.html
    set /a ERRORS+=1
) else (
    echo [INFO]  Maven: found
)

:: Node.js (optional)
node --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARN]  Node.js not found. Frontend build will be skipped.
) else (
    for /f %%v in ('node --version') do echo [INFO]  Node.js: %%v
)

if %ERRORS% gtr 0 (
    echo.
    echo [ERROR] Missing required dependencies. Please install them and retry.
    pause
    exit /b 1
)
echo [INFO]  All prerequisites satisfied.

:: ============================================
:: Step 2: Validate .env
:: ============================================
echo.
echo [2/%TOTAL_STEPS%] Validating configuration...

if not exist "%ENV_FILE%" (
    if exist "%PROJECT_DIR%\.env.example" (
        echo [WARN]  .env not found. Creating from .env.example...
        copy "%PROJECT_DIR%\.env.example" "%ENV_FILE%" >nul
        echo.
        echo [ERROR] Please edit .env file and fill in at least one LLM API Key:
        echo         %ENV_FILE%
        echo.
        echo   Required (at least one):
        echo     DASHSCOPE_API_KEY  - Tongyi Qianwen (recommended)
        echo     OPENAI_API_KEY     - OpenAI
        echo     DEEPSEEK_API_KEY   - DeepSeek
        echo.
        pause
        exit /b 1
    )
)

:: Load .env
for /f "usebackq tokens=1,* delims==" %%a in ("%ENV_FILE%") do (
    set "line=%%a"
    if not "!line:~0,1!"=="#" (
        if not "%%b"=="" set "%%a=%%b"
    )
)

:: Check LLM keys
set "HAS_LLM=0"
if defined DASHSCOPE_API_KEY if not "%DASHSCOPE_API_KEY%"=="" (
    set "HAS_LLM=1"
    echo [INFO]  LLM: DashScope configured
)
if defined OPENAI_API_KEY if not "%OPENAI_API_KEY%"=="" (
    set "HAS_LLM=1"
    echo [INFO]  LLM: OpenAI configured
)
if defined DEEPSEEK_API_KEY if not "%DEEPSEEK_API_KEY%"=="" (
    set "HAS_LLM=1"
    echo [INFO]  LLM: DeepSeek configured
)

if "%HAS_LLM%"=="0" (
    echo.
    echo [ERROR] No LLM API Key configured!
    echo [ERROR] Please edit .env and set at least one of:
    echo         DASHSCOPE_API_KEY, OPENAI_API_KEY, or DEEPSEEK_API_KEY
    pause
    exit /b 1
)
echo [INFO]  Configuration validated.

:: ============================================
:: Step 3: Start infrastructure
:: ============================================
echo.
echo [3/%TOTAL_STEPS%] Starting infrastructure (MySQL + Redis)...
cd /d "%PROJECT_DIR%"
docker compose up -d mysql redis
if %errorlevel% neq 0 (
    echo [ERROR] Failed to start infrastructure. Is Docker Desktop running?
    pause
    exit /b 1
)

echo [INFO]  Waiting for MySQL to be ready...
set "RETRIES=30"
:wait_mysql
docker compose exec -T mysql mysqladmin ping -h localhost --silent >nul 2>&1
if %errorlevel% neq 0 (
    set /a RETRIES-=1
    if %RETRIES% leq 0 (
        echo [ERROR] MySQL failed to start. Check: docker compose logs mysql
        pause
        exit /b 1
    )
    timeout /t 2 /nobreak >nul
    goto wait_mysql
)
echo [INFO]  Infrastructure is ready.

:: ============================================
:: Step 4: Build backend
:: ============================================
echo.
echo [4/%TOTAL_STEPS%] Building backend (Maven)...
cd /d "%PROJECT_DIR%"
call mvn clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo [ERROR] Backend build failed.
    pause
    exit /b 1
)
echo [INFO]  Backend build successful.

:: ============================================
:: Step 5: Build frontend (optional)
:: ============================================
echo.
echo [5/%TOTAL_STEPS%] Building frontend...
set "WEBAPP_DIR=%PROJECT_DIR%\fund-administration\src\main\webapp"
if not exist "%WEBAPP_DIR%" (
    echo [WARN]  Frontend project not found, skipping.
    goto start_app
)
node --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARN]  Node.js not installed, skipping frontend build.
    goto start_app
)
cd /d "%WEBAPP_DIR%"
if not exist "node_modules" (
    echo [INFO]  Installing frontend dependencies...
    call npm install --silent
)
call npm run build --silent
echo [INFO]  Frontend build successful.

:: ============================================
:: Step 6: Start application
:: ============================================
:start_app
echo.
echo [6/%TOTAL_STEPS%] Starting application...
cd /d "%PROJECT_DIR%"

:: Find JAR file
set "JAR_FILE="
for /r "%PROJECT_DIR%" %%f in (fund-application*.jar) do (
    echo %%f | findstr /i "target" >nul && set "JAR_FILE=%%f"
)

if not defined JAR_FILE (
    echo [ERROR] Application JAR not found. Build may have failed.
    pause
    exit /b 1
)

echo.
echo ========================================
echo  Setup complete!
echo.
if defined SERVER_PORT (
    echo   Application: http://localhost:%SERVER_PORT%
    echo   API Docs:    http://localhost:%SERVER_PORT%/swagger-ui.html
) else (
    echo   Application: http://localhost:8080
    echo   API Docs:    http://localhost:8080/swagger-ui.html
)
echo.
echo   Press Ctrl+C to stop.
echo ========================================
echo.

java -jar "%JAR_FILE%"
pause
