# eshop_chatmind — AI 智能体助手

eshop_chatmind 是一个基于 **Spring AI** 框架构建的智能 AI Agent 系统，实现了自主决策、工具调用和知识库检索（RAG）等核心能力。系统通过分层架构 + Agent 核心服务，将 AI 能力（模型、RAG、工具）抽象成可组合、可扩展的系统模块。

---

## 目录

- [项目架构](#项目架构)
- [Agent 引擎设计](#agent-引擎设计)
- [工具系统](#工具系统)
- [知识库与 RAG 检索](#知识库与-rag-检索)
- [多模型支持](#多模型支持)
- [改进记录](#改进记录)
- [Quick Start](#quick-start)

---

## 项目架构

```
eshop_chatmind/
├── agent/                  # Agent 核心引擎
│   ├── tools/              # 工具集（FIXED / OPTIONAL）
│   │   ├── KnowledgeTools  # 知识库混合检索
│   │   ├── DataBaseTools   # 数据库查询（两步SQL生成）
│   │   ├── EmailTools      # 异步邮件发送
│   │   ├── FileSystemTools # 文件系统操作
│   │   ├── TerminateTool   # 终止任务
│   │   └── DirectAnswerTool# 直接回答
│   ├── eshop_chatmind.java       # Agent 运行时（Think-Execute 循环）
│   ├── eshop_chatmindFactory.java # Agent 工厂（依赖组装）
│   └── AgentState.java    # Agent 状态机
├── config/                 # 配置（多模型、异步、CORS）
├── controller/             # REST API 层
├── converter/              # DTO ↔ Entity 转换
├── mapper/                 # MyBatis Mapper（数据访问）
├── model/                  # 数据模型（entity/dto/vo/request/response）
├── service/                # 业务服务层
│   ├── RagService          # RAG 检索（向量 + BM25 混合检索）
│   ├── KeywordSearchService # BM25 + jieba 关键词检索
│   ├── KnowledgeBaseSyncService # 知识库自动同步
│   ├── MarkdownParserService    # Markdown 解析分块
│   └── impl/               # 服务实现
├── event/                  # SSE 事件推送
└── typehandler/            # pgvector 类型处理器
```

### 分层设计

| 层 | 职责 |
|----|------|
| **Controller** | REST API，接收请求并委托给 FacadeService |
| **FacadeService** | 业务编排层，协调 Converter + Mapper |
| **Mapper** | MyBatis 数据访问，操作 PostgreSQL |
| **Agent Engine** | Agent 生命周期管理：Think → Execute 循环 |
| **Tool System** | 可插拔工具集，Spring AI MethodToolCallback 自动发现 |

---

## Agent 引擎设计

### Think-Execute 循环（ReAct 模式）

```
用户提问 → Think（决策）→ Execute（执行工具）→ Think → ... → 完成
```

- **Think 阶段**：Agent 根据对话上下文和 thinkPrompt，决定是否调用工具、调用哪个工具
- **Execute 阶段**：通过 Spring AI ToolCallingManager 执行工具调用，将结果写回对话记忆
- 循环最多 20 步，超限自动终止
- 工具调用和 AI 回复通过 SSE 实时推送到前端

### 对话记忆管理

- 基于 Spring AI `MessageWindowChatMemory`，保留最近 N 条消息
- 每次对话从数据库恢复记忆，工具调用结果也持久化
- `SystemMessage` → `UserMessage` → `AssistantMessage` → `ToolResponseMessage` 循环

### Agent 状态机

```
IDLE → THINKING → EXECUTING → FINISHED / ERROR
```

---

## 工具系统

工具分为两种类型，通过 Spring 依赖注入自动收集：

| 类型 | 说明 | 示例 |
|------|------|------|
| **FIXED** | 所有 Agent 强制拥有 | KnowledgeTool、TerminateTool |
| **OPTIONAL** | 按 Agent 配置选择性启用 | DataBaseTools、EmailTools、FileSystemTools |

### 工具详解

#### KnowledgeTool（固定工具）—— 知识库混合检索

结合**向量语义检索**和 **BM25 关键词检索**，通过 RRF（Reciprocal Rank Fusion）融合排序，返回最相关的知识片段。

- 输入：知识库 ID + 查询文本
- 输出：融合排序后的 top5 内容片段

#### DataBaseTools（可选工具）—— 数据库查询

**两步式 SQL 生成**，防止 AI 凭空捏造列名：

1. **`getTableSchema`**：查询 `information_schema.columns`，返回所有表名、列名、列类型
2. **`databaseQuery`**：基于真实表结构生成并执行 SELECT 查询

仅允许 SELECT，自动拦截非只读语句。

#### EmailTools（可选工具）—— 异步邮件发送

通过 QQ 邮箱 SMTP 异步发送邮件，不阻塞 Agent 循环。

- 输入：收件人、主题、正文
- 支持邮箱格式校验，异步执行不阻塞

#### FileSystemTools（可选工具）—— 文件系统操作

提供文件读写、目录管理功能，内置路径遍历攻击防护。

- `readFile` / `writeFile` / `appendToFile` — 文件读写
- `listFiles` — 目录列表
- `createDirectory` / `deleteFile` — 目录/文件管理
- 所有路径限制在项目工作目录内，防止越权访问

#### TerminateTool（固定工具）—— 终止任务

Agent 判断任务完成后调用，跳出 Think-Execute 循环。

#### DirectAnswerTool（固定工具）—— 直接回答

适用于无需工具调用的纯对话场景。

---

## 知识库与 RAG 检索

### 文档处理流水线

```
上传 Markdown → 解析标题/章节 → 对标题做 Embedding（bge-m3）→ 存入 pgvector
```

- **MarkdownParserService**：基于 flexmark 解析，提取每个 `# 标题` 及其下属内容（含表格原始格式）
- **Embedding**：通过 Ollama 本地部署的 `bge-m3` 模型生成向量
- **存储**：PostgreSQL + pgvector 扩展，按知识库 ID 隔离

### 混合检索（Hybrid RAG）

检索流程结合两种互补策略：

| 策略 | 引擎 | 优势 |
|------|------|------|
| **语义检索** | pgvector cosine 距离 | 捕获语义相近但用词不同的内容 |
| **关键词检索** | BM25 + jieba 分词 | 精确命中关键词，适合专有名词查询 |

融合方式：**Reciprocal Rank Fusion (RRF)**，两边各取 top5，按 RRF 分数重排后返回 top5。

### 知识库自动同步（基于内容 Hash）

通过 `POST /api/knowledge-bases/{id}/sync` 触发，基于文件 SHA-256 检测变更：

| 检测结果 | 操作 |
|----------|------|
| **新增**（无 Hash 记录） | 解析 Markdown → 分块 → Embedding → 写入向量库 |
| **修改**（Hash 不一致） | 删除旧分块 → 重新解析 → 重新 Embedding → 写入 |
| **删除**（文件不存在） | 清理向量分块 + 文档记录 |
| **未变更**（Hash 一致） | 跳过 |

文档上传时自动计算并保存 SHA-256 哈希。

---

## 多模型支持

通过 `ChatClientRegistry` + `MultiChatClientConfig` 支持多模型热切换：

| 模型 | Bean 名称 | 提供商 |
|------|-----------|--------|
| DeepSeek-Chat | `deepseek-chat` | DeepSeek |
| GLM-4.6 | `glm-4.6` | 智谱 AI |

每个 Agent 在创建时指定模型，ChatClientRegistry 根据模型名路由到对应的 ChatClient。

---

## 前端

前端位于 `ui/` 目录，使用 React + TypeScript + Vite + Ant Design + Tailwind CSS 构建：

- **知识库管理**：创建、编辑、删除知识库；上传/管理 Markdown 文档
- **Agent 对话**：选择 Agent → 实时对话 → SSE 流式展示工具调用和 AI 回复
- **工具配置**：为 Agent 选择启用的可选工具

---

## 改进记录

### 改进一：数据库查询两步式 SQL 生成

**问题**：Agent 直接生成 SQL 时可能凭空捏造林名，导致查询失败。

**方案**：新增 `getTableSchema` 工具方法，Agent 的 SQL 生成流程变为：

1. 先调用 `getTableSchema` 查询 `information_schema.columns`，获取真实表结构和列名
2. 结合用户自然语言提取查询条件和目标列
3. 最后调用 `databaseQuery` 生成并执行 SQL

**涉及文件**：

| 文件 | 变更 |
|------|------|
| [DataBaseTools.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/agent/tools/DataBaseTools.java) | 新增 `getTableSchema` 方法；更新 `databaseQuery` 的 Tool description |

---

### 改进二：知识库自动同步（内容 Hash）

**问题**：文档更新后需要手动重新上传才能更新向量库，容易遗漏。

**方案**：基于文件内容 SHA-256 哈希检测变更，自动同步向量库：

- 文档上传时自动计算 SHA-256 哈希存入 metadata
- 提供 `POST /api/knowledge-bases/{id}/sync` 端点触发同步
- 根据 Hash 对比结果执行：新增分块 / 整体重建 / 清理删除

**涉及文件**：

| 文件 | 变更 |
|------|------|
| [KnowledgeBaseSyncService.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/KnowledgeBaseSyncService.java) | **新增** — 同步服务接口 + SyncResult record |
| [KnowledgeBaseSyncServiceImpl.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/impl/KnowledgeBaseSyncServiceImpl.java) | **新增** — 同步实现（Hash 比对、文档处理、分块管理） |
| [DocumentDTO.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/model/dto/DocumentDTO.java) | MetaData 新增 `contentHash` 字段 |
| [DocumentFacadeServiceImpl.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/impl/DocumentFacadeServiceImpl.java) | 上传时自动计算并保存文件 Hash |
| [ChunkBgeM3Mapper.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/mapper/ChunkBgeM3Mapper.java) | 新增 `deleteByDocId`、`deleteByKbId`、`selectByKbId` |
| [ChunkBgeM3Mapper.xml](eshop_chatmind/src/main/resources/mapper/ChunkBgeM3Mapper.xml) | 对应 SQL |
| [KnowledgeBaseController.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/controller/KnowledgeBaseController.java) | 新增 `POST /api/knowledge-bases/{id}/sync` 端点 |

---

### 改进三：RAG 混合检索（向量 + BM25 + jieba 分词）

**问题**：纯向量语义检索可能遗漏精确关键词匹配，对专有名词/编号/代码等查询效果不佳。

**方案**：在原有向量检索基础上加入 BM25 关键词检索，使用 jieba 分词器处理中文文本，通过 RRF 融合排序。

```
查询 → jieba分词 → BM25关键词检索 (top5)  ─┐
     → bge-m3 Embedding → pgvector语义检索 (top5) ─┤→ RRF融合 → top5 结果
```

**BM25 参数**：k1=1.5, b=0.75，IDF 基于知识库内全量 chunk 计算。

**涉及文件**：

| 文件 | 变更 |
|------|------|
| [pom.xml](eshop_chatmind/pom.xml) | 新增 `jieba-analysis:1.0.2` 依赖 |
| [KeywordSearchService.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/KeywordSearchService.java) | **新增** — 关键词检索接口 |
| [KeywordSearchServiceImpl.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/impl/KeywordSearchServiceImpl.java) | **新增** — BM25 + jieba 实现 |
| [RagService.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/RagService.java) | 新增 `hybridSearch` 方法 |
| [RagServiceImpl.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/impl/RagServiceImpl.java) | 实现向量+BM25的RRF融合检索 |
| [KnowledgeTools.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/agent/tools/KnowledgeTools.java) | 改用 `hybridSearch` 替代 `similaritySearch` |

---

### 改进四：强制 Agent 优先查询外部数据源

**问题**：Agent 可能依赖训练时的过时知识直接回答，而不是查询数据库或知识库获取最新数据。

**方案**：重写 Agent 的 `thinkPrompt`，在决策层强制要求：

1. 严禁在未查询外部数据源的情况下直接回答事实性问题
2. 涉及数据支撑的问题，必须先调用 `getTableSchema` → `databaseQuery` 或 `KnowledgeTool`
3. 只有纯闲聊和常识推理可以直接回答

**涉及文件**：

| 文件 | 变更 |
|------|------|
| [eshop_chatmind.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/agent/eshop_chatmind.java) | 重写 `thinkPrompt`，加入强制查询规则和推荐流程 |

---

## Quick Start

### 环境要求

- Java 21+
- Maven 3.8+
- PostgreSQL 14+（需安装 pgvector 扩展）
- Ollama（本地部署 bge-m3 embedding 模型）
- Node.js 18+（前端）

### 1. 初始化数据库

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_base (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id UUID REFERENCES knowledge_base(id),
    filename VARCHAR(255),
    filetype VARCHAR(50),
    size BIGINT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE chunk_bge_m3 (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kb_id UUID REFERENCES knowledge_base(id),
    doc_id UUID REFERENCES document(id),
    content TEXT,
    metadata JSONB,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

### 2. 配置

编辑 `src/main/resources/application.yaml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/eshop_chatmind
    username: your_db_user
    password: your_db_password
  ai:
    deepseek:
      api-key: your_deepseek_api_key
    zhipuai:
      api-key: your_zhipu_api_key
```

### 3. 启动 Ollama

```bash
ollama pull bge-m3
ollama serve
```

### 4. 启动后端

```bash
cd eshop_chatmind
./mvnw spring-boot:run
```

后端默认运行在 `http://localhost:8080`。

### 5. 启动前端

```bash
cd ui
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`。

### 6. 使用流程

1. 在「知识库」Tab 创建知识库，上传 Markdown 文档
2. 调用 `POST /api/knowledge-bases/{id}/sync` 或等待自动同步
3. 在「Agent」Tab 创建 Agent，配置模型、系统提示词、可选工具和可访问的知识库
4. 在「对话」Tab 选择 Agent，开始对话
