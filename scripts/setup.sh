#!/usr/bin/env bash
# ============================================
# Fund Analysis Agents - One-click Setup Script
# Supports macOS and Linux
# ============================================
set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"

print_banner() {
    echo -e "${BLUE}"
    echo "  _____ _   _ _   _ ____       _    ____  _____ _   _ _____ ____  "
    echo " |  ___| | | | \ | |  _ \     / \  / ___|| ____| \ | |_   _/ ___| "
    echo " | |_  | | | |  \| | | | |   / _ \| |  _ |  _| |  \| | | | \___ \ "
    echo " |  _| | |_| | |\  | |_| |  / ___ \ |_| || |___| |\  | | |  ___) |"
    echo " |_|    \___/|_| \_|____/  /_/   \_\____||_____|_| \_| |_| |____/ "
    echo -e "${NC}"
    echo -e "${GREEN}Fund Analysis Agents - One-click Setup${NC}"
    echo "========================================"
    echo ""
}

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "\n${BLUE}[$1/$TOTAL_STEPS]${NC} $2"; }

TOTAL_STEPS=6

# ============================================
# Step 1: Check prerequisites
# ============================================
check_prerequisites() {
    log_step 1 "Checking prerequisites..."
    local missing=0

    # Docker
    if command -v docker &>/dev/null; then
        local docker_ver=$(docker --version | grep -oE '[0-9]+\.[0-9]+' | head -1)
        log_info "Docker: $docker_ver"
    else
        log_error "Docker not found. Please install: https://docs.docker.com/get-docker/"
        missing=1
    fi

    # Docker Compose
    if docker compose version &>/dev/null; then
        local compose_ver=$(docker compose version --short 2>/dev/null || echo "v2+")
        log_info "Docker Compose: $compose_ver"
    elif command -v docker-compose &>/dev/null; then
        log_info "Docker Compose (legacy): $(docker-compose --version | grep -oE '[0-9]+\.[0-9]+' | head -1)"
    else
        log_error "Docker Compose not found. Please install Docker Desktop or docker-compose."
        missing=1
    fi

    # Java
    if command -v java &>/dev/null; then
        local java_ver=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+"' | tr -d '"')
        if [ "$java_ver" -ge 21 ] 2>/dev/null; then
            log_info "Java: $java_ver"
        else
            log_error "Java 21+ required, found: $java_ver"
            missing=1
        fi
    else
        log_error "Java not found. Please install JDK 21: https://adoptium.net/"
        missing=1
    fi

    # Maven
    if command -v mvn &>/dev/null; then
        local mvn_ver=$(mvn --version | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
        log_info "Maven: $mvn_ver"
    else
        log_error "Maven not found. Please install: https://maven.apache.org/install.html"
        missing=1
    fi

    # Node.js (optional)
    if command -v node &>/dev/null; then
        log_info "Node.js: $(node --version)"
    else
        log_warn "Node.js not found. Frontend build will be skipped."
        log_warn "Install from: https://nodejs.org/ (required for frontend development)"
    fi

    if [ $missing -eq 1 ]; then
        echo ""
        log_error "Missing required dependencies. Please install them and retry."
        exit 1
    fi
    log_info "All prerequisites satisfied."
}

# ============================================
# Step 2: Validate .env configuration
# ============================================
validate_env() {
    log_step 2 "Validating configuration..."

    if [ ! -f "$ENV_FILE" ]; then
        if [ -f "$PROJECT_DIR/.env.example" ]; then
            log_warn ".env file not found. Creating from .env.example..."
            cp "$PROJECT_DIR/.env.example" "$ENV_FILE"
            echo ""
            log_error "Please edit .env file and fill in at least one LLM API Key:"
            log_error "  $ENV_FILE"
            echo ""
            echo "  Required (at least one):"
            echo "    DASHSCOPE_API_KEY  - Tongyi Qianwen (recommended)"
            echo "    OPENAI_API_KEY     - OpenAI"
            echo "    DEEPSEEK_API_KEY   - DeepSeek"
            echo ""
            exit 1
        else
            log_error ".env.example not found. Please check project integrity."
            exit 1
        fi
    fi

    # Source .env
    set -a
    source "$ENV_FILE"
    set +a

    # Validate at least one LLM key
    local has_llm=0
    [ -n "$DASHSCOPE_API_KEY" ] && has_llm=1 && log_info "LLM: DashScope (Tongyi Qianwen) configured"
    [ -n "$OPENAI_API_KEY" ]    && has_llm=1 && log_info "LLM: OpenAI configured"
    [ -n "$DEEPSEEK_API_KEY" ]  && has_llm=1 && log_info "LLM: DeepSeek configured"

    if [ $has_llm -eq 0 ]; then
        echo ""
        log_error "No LLM API Key configured!"
        log_error "Please edit .env and set at least one of:"
        log_error "  DASHSCOPE_API_KEY, OPENAI_API_KEY, or DEEPSEEK_API_KEY"
        exit 1
    fi

    # Optional checks
    [ -n "$SMTP_USERNAME" ]    && log_info "Notification: Email configured" || log_warn "Email not configured (optional)"
    [ -n "$BARK_DEVICE_KEY" ]  && log_info "Notification: Bark configured"  || log_warn "Bark not configured (optional)"
    [ -n "$TUSHARE_API_TOKEN" ] && log_info "DataSource: Tushare configured" || log_warn "Tushare not configured (optional)"

    log_info "Configuration validated."
}

# ============================================
# Step 3: Start infrastructure (MySQL + Redis)
# ============================================
start_infrastructure() {
    log_step 3 "Starting infrastructure (MySQL + Redis)..."
    cd "$PROJECT_DIR"

    # Check if containers already running
    if docker compose ps --status running 2>/dev/null | grep -q "mysql"; then
        log_info "MySQL already running."
    else
        docker compose up -d mysql redis
        log_info "Waiting for MySQL to be ready..."
        local retries=30
        while [ $retries -gt 0 ]; do
            if docker compose exec -T mysql mysqladmin ping -h localhost --silent 2>/dev/null; then
                break
            fi
            retries=$((retries - 1))
            sleep 2
        done
        if [ $retries -eq 0 ]; then
            log_error "MySQL failed to start. Check: docker compose logs mysql"
            exit 1
        fi
    fi
    log_info "Infrastructure is ready."
}

# ============================================
# Step 4: Build backend
# ============================================
build_backend() {
    log_step 4 "Building backend (Maven)..."
    cd "$PROJECT_DIR"
    mvn clean package -DskipTests -q
    log_info "Backend build successful."
}

# ============================================
# Step 5: Build frontend (optional)
# ============================================
build_frontend() {
    log_step 5 "Building frontend..."
    local webapp_dir="$PROJECT_DIR/fund-administration/src/main/webapp"

    if [ ! -d "$webapp_dir" ]; then
        log_warn "Frontend project not found at $webapp_dir, skipping."
        return
    fi

    if ! command -v node &>/dev/null; then
        log_warn "Node.js not installed, skipping frontend build."
        return
    fi

    cd "$webapp_dir"
    if [ ! -d "node_modules" ]; then
        log_info "Installing frontend dependencies..."
        npm install --silent
    fi
    npm run build --silent
    log_info "Frontend build successful."
}

# ============================================
# Step 6: Start application
# ============================================
start_application() {
    log_step 6 "Starting application..."
    cd "$PROJECT_DIR"

    local jar_file=$(find . -name "fund-application*.jar" -path "*/target/*" | head -1)
    if [ -z "$jar_file" ]; then
        log_error "Application JAR not found. Build may have failed."
        exit 1
    fi

    log_info "Starting Fund Analysis Agents..."
    echo ""
    echo "========================================"
    echo -e "${GREEN}Setup complete!${NC}"
    echo ""
    echo "  Application: http://localhost:${SERVER_PORT:-8080}"
    echo "  API Docs:    http://localhost:${SERVER_PORT:-8080}/swagger-ui.html"
    echo ""
    echo "  Press Ctrl+C to stop."
    echo "========================================"
    echo ""

    java -jar "$jar_file"
}

# ============================================
# Main
# ============================================
main() {
    print_banner
    check_prerequisites
    validate_env
    start_infrastructure
    build_backend
    build_frontend
    start_application
}

main "$@"
