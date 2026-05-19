# BlloseAgent4J

基于 LangChain4j + MCP 协议的 AI 学术论文研究助手，支持多源元数据聚合、PDF 智能下载、SSE 流式对话，前端 Vue 3 + Naive UI 构建。

## 技术亮点

### ReAct 循环 + 思考链可视化

DeepSeek 模型的 thinking 模式被完整呈现：LLM 的推理过程、工具调用决策、最终回复通过 SSE 事件流分离推送，前端以可折叠面板实时渲染。

| SSE 事件 | 用途 |
|----------|------|
| `thinking` | LLM 思考链片段 |
| `tool` | MCP 工具调用信号（实时反馈调用哪个工具） |
| `message` | 最终回复 Token 流 |
| `done` | 对话完成信号 |

### MCP 多工具自动编排

3 个 MCP 客户端（paper-metadata、paper-download、minimax）统一注入 `McpToolProvider`，LLM 根据用户意图自动选择并组合调用。论文查不到时自动 fallback 到 MiniMax 联网搜索。

### 多源元数据合并策略

`paper-metadata-mcp` 同时查询 Crossref、Semantic Scholar、OpenAlex、Unpaywall 四个学术 API，按优先级（Crossref > Semantic Scholar > OpenAlex > Unpaywall）合并结果——后续源只填充前面缺失的字段，引用数取最大值，作者去重合并。

### PDF 多级联发现

`paper-download-mcp` 的 PDF URL 发现采用级联策略：arXiv 纯正则匹配（无 API 依赖）→ Unpaywall → Semantic Scholar，首个命中即返回。

### TTL 缓存防限流

元数据查询结果带 TTL 缓存（默认 1 小时），批量查询友好，避免重复请求触发 API 速率限制。

### 双层 API 设计

保留无工具的基础对话路径（`ChatController` / `ChatService`）作为 baseline，同时提供完整的 Agent 路径（`ChatV1Controller` / `ChatV1Service` / `PaperService`）集成全量 MCP 工具。

## 技术栈

| 层 | 技术 |
|----|------|
| **LLM 框架** | LangChain4j 1.15.0 + OpenAI 兼容协议 |
| **MCP 协议** | LangChain4j MCP 1.15.0-beta25 (Stdio 传输) |
| **后端** | Spring Boot 3.3.5 / Java 17 / Maven |
| **数据库** | SQLite + JdbcTemplate (无需 ORM) |
| **认证** | BCrypt + 基于 Session Token 的内存会话 |
| **前端** | Vue 3.5 (Composition API) / Vite 6 / Naive UI 2.41 |
| **状态管理** | Pinia 2.3 + Vue Router 4.5 |
| **Markdown** | marked 18 (含预处理修复) |
| **Python MCP** | mcp>=1.0.0 / httpx / pydantic |
| **外部 API** | Crossref / Semantic Scholar / OpenAlex / Unpaywall / arXiv / MiniMax |

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.6+
- Node.js 18+
- Python 3.11+ (MCP 服务)
- uv / uvx (MiniMax MCP 客户端)

### 1. 配置

```bash
cp src/main/resources/application-example.yml src/main/resources/application.yml
```

编辑 `application.yml`，填入 API Key：

```yaml
langchain4j:
  openai:
    api-key: <your-deepseek-api-key>

app:
  mcp:
    minimax:
      env:
        MINIMAX_API_KEY: <your-minimax-api-key>
```

### 2. 搭建 MCP 服务

```bash
# paper-metadata-mcp
cd mcp/paper-metadata-mcp
python -m venv .venv
source .venv/Scripts/activate  # Windows
pip install -e .
cd ../..

# paper-download-mcp
cd mcp/paper-download-mcp
python -m venv .venv
source .venv/Scripts/activate  # Windows
pip install -e .
cd ../..
```

### 3. 启动

```bash
# 后端 (端口 8080)
mvn spring-boot:run

# 前端 (端口 5173)
cd frontend && npm install && npm run dev
```

浏览器访问 `http://localhost:5173`，注册后即可使用。

### 4. 调试模式

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=debug"
```

启用 OkHttp 网络层 TRACE 日志和 LangChain4j 完整内部日志。

## 项目结构

```
├── frontend/                      # Vue 3 前端
│   └── src/
│       ├── views/ChatView.vue     # 主聊天界面 (SSE 流 + Markdown)
│       ├── views/LoginView.vue    # 登录/注册
│       ├── stores/auth.js         # Pinia 认证状态
│       ├── components/            # SideMenu / TopBar
│       └── api/index.js           # HTTP 客户端封装
│
├── src/main/java/com/bllose/agent/
│   ├── config/
│   │   └── LangChain4jConfig.java # LLM + MCP + AI Service 装配
│   ├── controller/
│   │   ├── ChatV1Controller.java  # SSE 流式 + 工具调用
│   │   └── AuthController.java    # 注册/登录
│   ├── service/
│   │   ├── ChatV1Service.java     # 带工具的流式 Agent
│   │   └── PaperAssistant.java    # LangChain4j @AiService 接口
│   ├── debug/
│   │   └── OpenAiDebugListener.java # 请求/响应边界日志
│   └── auth/
│       ├── AuthService.java       # BCrypt + Session 管理
│       └── SessionManager.java    # ConcurrentHashMap 会话存储
│
├── mcp/
│   ├── paper-metadata-mcp/        # 7 工具 + 资源 (四源元数据聚合)
│   │   ├── server.py
│   │   ├── models.py              # 合并 + TTL 缓存
│   │   └── providers/             # crossref / semantic_scholar / openalex / unpaywall
│   └── paper-download-mcp/        # 3 工具 (PDF 发现 + 下载)
│       ├── server.py
│       └── providers/             # arxiv / unpaywall / semantic_scholar
│
└── pom.xml                        # Maven 构建
```

## 关键依赖

```xml
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-open-ai</artifactId>
  <version>1.15.0</version>
</dependency>
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-mcp</artifactId>
  <version>1.15.0-beta25</version>
</dependency>
```

## License

MIT
