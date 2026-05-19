#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# BlloseAgent4J — Ubuntu 一键环境搭建 & 启动脚本
# ============================================================

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
    log_info "正在停止服务..."
    [ -n "${BACKEND_PID:-}" ] && kill "$BACKEND_PID" 2>/dev/null || true
    [ -n "${FRONTEND_PID:-}" ] && kill "$FRONTEND_PID" 2>/dev/null || true
    log_info "已退出。"
}
trap cleanup EXIT INT TERM

# --------------- 1. 系统环境检测 & 自动安装 ---------------
log_info "========== 检测系统环境 =========="

# Java 17+
if command -v java &>/dev/null && java -version 2>&1 | grep -qE 'version "(1[789]|[2-9][0-9])'; then
    log_info "Java $(java -version 2>&1 | head -1) ✓"
else
    log_warn "Java 17+ 未找到，正在安装..."
    sudo apt update -qq && sudo apt install -y -qq openjdk-17-jdk
    log_info "Java 安装完成"
fi

# Maven
if command -v mvn &>/dev/null; then
    log_info "Maven $(mvn --version 2>&1 | head -1) ✓"
else
    log_warn "Maven 未找到，正在安装..."
    sudo apt install -y -qq maven
    log_info "Maven 安装完成"
fi

# Node.js 18+
if command -v node &>/dev/null; then
    NODE_VER=$(node --version | sed 's/v//' | cut -d. -f1)
    if [ "$NODE_VER" -ge 18 ]; then
        log_info "Node.js $(node --version) ✓"
    else
        log_warn "Node.js 版本过低，需要 18+"
    fi
else
    log_warn "Node.js 未找到，正在通过 NodeSource 安装..."
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt install -y -qq nodejs
    log_info "Node.js $(node --version) 安装完成"
fi

# npm (通常随 Node.js 安装)
if ! command -v npm &>/dev/null; then
    log_warn "npm 未找到，正在安装..."
    sudo apt install -y -qq npm
fi

# Python 3.11+
PYTHON_BIN=""
for py in python3.13 python3.12 python3.11 python3; do
    if command -v "$py" &>/dev/null; then
        PY_VER=$("$py" --version 2>&1 | sed 's/.* \([0-9]*\)\.\([0-9]*\).*/\1\2/')
        if [ "${PY_VER:-0}" -ge 311 ]; then
            PYTHON_BIN="$py"
            break
        fi
    fi
done

if [ -z "$PYTHON_BIN" ]; then
    log_warn "Python 3.11+ 未找到，正在安装..."
    sudo apt update -qq && sudo apt install -y -qq python3 python3-venv python3-pip
    PYTHON_BIN="python3"
    PY_VER=$("$PYTHON_BIN" --version 2>&1 | sed 's/.* \([0-9]*\)\.\([0-9]*\).*/\1\2/')
    if [ "${PY_VER:-0}" -lt 311 ]; then
        log_error "Ubuntu 默认 Python < 3.11，需要手动安装。请使用 deadsnakes PPA:"
        echo "  sudo add-apt-repository ppa:deadsnakes/ppa -y"
        echo "  sudo apt install python3.12 python3.12-venv python3.12-dev -y"
        exit 1
    fi
fi
log_info "Python $($PYTHON_BIN --version) ✓"

# uv / uvx (MiniMax MCP)
if ! command -v uvx &>/dev/null; then
    log_warn "uv/uvx 未找到，正在安装..."
    curl -LsSf https://astral.sh/uv/install.sh | sh
    export PATH="$HOME/.local/bin:$PATH"
    if ! command -v uvx &>/dev/null; then
        log_warn "uvx 安装后未找到，尝试用 pip 安装..."
        pip install uv --break-system-packages 2>/dev/null || pip install uv
    fi
fi
log_info "uvx $(uvx --version 2>&1 || echo '✓') ✓"

# --------------- 2. 配置文件 ---------------
log_info "========== 配置文件检查 =========="
CONFIG_FILE="$PROJECT_ROOT/src/main/resources/application.yml"
CONFIG_EXAMPLE="$PROJECT_ROOT/src/main/resources/application-example.yml"

if [ ! -f "$CONFIG_FILE" ]; then
    log_info "创建 application.yml (从 example 模板)..."
    cp "$CONFIG_EXAMPLE" "$CONFIG_FILE"

    # 修正 Linux 下的 Python 虚拟环境路径
    sed -i 's|mcp/paper-metadata-mcp/.venv/Scripts/python.exe|mcp/paper-metadata-mcp/.venv/bin/python|g' "$CONFIG_FILE"
    sed -i 's|mcp/paper-download-mcp/.venv/Scripts/python.exe|mcp/paper-download-mcp/.venv/bin/python|g' "$CONFIG_FILE"

    log_warn "请编辑 ${CONFIG_FILE} 并填入 API Key:"
    echo "  langchain4j.openai.api-key        → DeepSeek API Key"
    echo "  app.mcp.minimax.env.MINIMAX_API_KEY → MiniMax API Key"
    echo ""
    read -rp "是否现在输入 API Key? (y/n): " ANSWER
    if [ "$ANSWER" = "y" ] || [ "$ANSWER" = "Y" ]; then
        read -rp "DeepSeek API Key: " DS_KEY
        read -rp "MiniMax API Key: " MM_KEY
        if [ -n "$DS_KEY" ]; then
            sed -i "s|<your-deepseek-api-key>|$DS_KEY|" "$CONFIG_FILE"
        fi
        if [ -n "$MM_KEY" ]; then
            sed -i "s|<your-minimax-api-key>|$MM_KEY|" "$CONFIG_FILE"
        fi
        log_info "API Key 已写入配置"
    fi
else
    log_info "application.yml 已存在 ✓"

    # 检查是否还是 Windows 路径（从 Windows 克隆的项目）
    if grep -q 'Scripts/python.exe' "$CONFIG_FILE" 2>/dev/null; then
        log_warn "检测到 Windows 路径，正在修正为 Linux 路径..."
        sed -i 's|mcp/paper-metadata-mcp/.venv/Scripts/python.exe|mcp/paper-metadata-mcp/.venv/bin/python|g' "$CONFIG_FILE"
        sed -i 's|mcp/paper-download-mcp/.venv/Scripts/python.exe|mcp/paper-download-mcp/.venv/bin/python|g' "$CONFIG_FILE"
        log_info "路径修正完成"
    fi
fi

# --------------- 3. Python MCP 虚拟环境 ---------------
log_info "========== 搭建 Python MCP 服务 =========="

setup_python_venv() {
    local dir="$1"
    local name="$2"
    local full_path="$PROJECT_ROOT/$dir"

    if [ -d "$full_path/.venv" ]; then
        log_info "[$name] 虚拟环境已存在 ✓"
    else
        log_info "[$name] 创建虚拟环境..."
        "$PYTHON_BIN" -m venv "$full_path/.venv"
        source "$full_path/.venv/bin/activate"
        pip install --upgrade pip -q
        pip install -e "$full_path" -q
        deactivate
        log_info "[$name] 安装完成 ✓"
    fi
}

setup_python_venv "mcp/paper-metadata-mcp" "paper-metadata-mcp"
setup_python_venv "mcp/paper-download-mcp" "paper-download-mcp"

# --------------- 4. 前端依赖 ---------------
log_info "========== 前端依赖 =========="
cd "$PROJECT_ROOT/frontend"
if [ -d "node_modules" ]; then
    log_info "node_modules 已存在 ✓"
else
    log_info "安装前端 npm 依赖..."
    npm install
    log_info "前端依赖安装完成 ✓"
fi

# --------------- 5. 数据库目录 ---------------
mkdir -p "$PROJECT_ROOT/data" "$PROJECT_ROOT/downloads"

# --------------- 6. 启动服务 ---------------
log_info "========== 启动服务 =========="

# 后端 (Spring Boot)
log_info "启动后端 (Spring Boot, 端口 8080)..."
cd "$PROJECT_ROOT"
mvn spring-boot:run -q &
BACKEND_PID=$!
log_info "后端 PID: $BACKEND_PID"

# 等待后端就绪
log_info "等待后端启动..."
for i in $(seq 1 60); do
    if curl -s http://localhost:8080/api/auth/login >/dev/null 2>&1; then
        log_info "后端就绪 ✓"
        break
    fi
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
        log_error "后端进程已退出，请检查日志"
        exit 1
    fi
    sleep 2
done

if ! curl -s http://localhost:8080/api/auth/login >/dev/null 2>&1; then
    log_warn "后端未在 120 秒内就绪，可能仍在启动中..."
fi

# 前端 (Vite)
log_info "启动前端 (Vite, 端口 5173)..."
cd "$PROJECT_ROOT/frontend"
npm run dev &
FRONTEND_PID=$!
log_info "前端 PID: $FRONTEND_PID"

sleep 3

# --------------- 7. 完成 ---------------
echo ""
echo "=============================================="
echo -e "  ${GREEN}BlloseAgent4J 启动成功!${NC}"
echo "=============================================="
echo ""
echo "  前端:  http://localhost:5173"
echo "  后端:  http://localhost:8080"
echo ""
echo "  进程 PID:"
echo "    后端 (Spring Boot): $BACKEND_PID"
echo "    前端 (Vite):        $FRONTEND_PID"
echo ""
echo "  按 Ctrl+C 停止所有服务"
echo "=============================================="

wait
