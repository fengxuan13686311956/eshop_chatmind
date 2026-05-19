# eshop_chatmind — 电商智能客服 Agent

基于 **Spring AI** 框架构建的智能 AI Agent 系统，支持自主决策、工具调用和知识库检索（RAG）。分层架构 + Agent 核心服务，将 AI 能力抽象为可组合、可扩展的系统模块。

---

## 目录

- [项目简介](#项目简介)
- [改进记录](#改进记录)
- [Quick Start](#quick-start)

---

## 项目简介

### 项目架构

```
eshop_chatmind/
├── agent/                  # Agent 核心引擎
│   ├── tools/              # 工具集（FIXED / OPTIONAL）
│   ├── eshop_chatmind.java       # Agent 运行时（Think-Execute 循环）
│   └── eshop_chatmindFactory.java # Agent 工厂（依赖组装）
├── config/                 # 多模型、异步、CORS 等配置
├── controller/             # REST API 层
├── converter/              # DTO ↔ Entity 转换
├── mapper/                 # MyBatis 数据访问
├── model/                  # entity / dto / vo / request / response
├── service/                # 业务服务（RAG、同步、邮件、文档解析等）
├── event/                  # SSE 事件推送
└── typehandler/            # pgvector 类型处理器
```

### Agent 引擎

采用 **Think-Execute 循环**（ReAct 模式）：

```
用户提问 → Think（决策）→ Execute（执行工具）→ Think → ... → 完成
```

- 循环最多 20 步，超限自动终止
- 基于 Spring AI `MessageWindowChatMemory`，超出滑动窗口的历史消息进行总结后持久化
- 工具调用和 AI 回复通过 SSE 实时推送前端

### 工具系统

| 类型 | 说明 | 工具 |
|------|------|------|
| **FIXED** | 所有 Agent 强制拥有 | KnowledgeTool、TerminateTool、DirectAnswerTool |
| **OPTIONAL** | 按 Agent 配置选择性启用 | DataBaseTools、EmailTools、FileSystemTools |

- **KnowledgeTool**：知识库检索
- **DataBaseTools**：数据库 SELECT 查询
- **EmailTools**：异步邮件发送
- **FileSystemTools**：文件读写、目录管理（内置路径穿越防护）
- **TerminateTool**：终止 Agent 循环
- **DirectAnswerTool**：无需工具的纯对话场景

### 知识库与 RAG

文档上传后自动解析 Markdown（flexmark）→ 提取标题/章节 → 对标题做 Embedding（Ollama bge-m3）→ 存入 PostgreSQL + pgvector。

### 多模型支持

通过 `ChatClientRegistry` + `MultiChatClientConfig` 支持 DeepSeek-Chat、GLM-4.6 热切换，每个 Agent 可指定不同模型。

### 前端

`ui/` 目录，React + TypeScript + Vite + Ant Design + Tailwind CSS。支持知识库管理、Agent 对话（SSE 实时流式）、工具配置。

---

## 改进记录

### 改进一：数据库查询两步式 SQL 生成

**问题**：Agent 直接生成 SQL 时可能凭空捏造林名，导致查询失败。

**方案**：

**方案一**：参考Mars-SQL工作，将自然语言转 SQL 的任务分解为两步：

1. 先调用 `getTableSchema` 查询 `information_schema.columns`，获取真实表结构和列名
2. 结合用户自然语言提取查询条件和目标列
3. 最后调用 `databaseQuery` 生成并执行 SQL

**涉及文件**：

| 文件 | 变更 |
|------|------|
| [DataBaseTools.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/agent/tools/DataBaseTools.java) | 新增 `getTableSchema` 方法；更新 `databaseQuery` 的 Tool description |

**方案二**：加入了专门的数据库说明文档a992ede0-ce57-455c-8867-674c33e6eae6.md,包含对每个表格、表格列的解释说明。

---

### 改进二：知识库自动同步（基于内容 Hash）

**问题**：文档更新后需手动重新上传才能更新向量库，容易遗漏。

**方案**：基于文件内容 SHA-256 哈希自动检测变更并同步向量库：

| 检测结果 | 操作 |
|----------|------|
| **新增**（无 Hash 记录） | 解析 Markdown → 分块 → Embedding → 写入向量库 |
| **修改**（Hash 不一致） | 删除旧分块 → 重新解析 → 重新 Embedding → 写入 |
| **删除**（文件不存在） | 清理向量分块 + 文档记录 |
| **未变更**（Hash 一致） | 跳过 |

- 文档上传时自动计算并保存 SHA-256 哈希到 metadata
- 提供 `POST /api/knowledge-bases/{id}/sync` 端点手动触发同步

**涉及文件**：

| 文件 | 变更 |
|------|------|
| [KnowledgeBaseSyncService.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/KnowledgeBaseSyncService.java) | **新增** — 同步服务接口 + SyncResult record |
| [KnowledgeBaseSyncServiceImpl.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/impl/KnowledgeBaseSyncServiceImpl.java) | **新增** — Hash 比对、文档处理、分块管理 |
| [DocumentDTO.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/model/dto/DocumentDTO.java) | MetaData 新增 `contentHash` 字段 |
| [DocumentFacadeServiceImpl.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/impl/DocumentFacadeServiceImpl.java) | 上传时自动计算并保存文件 Hash |
| [ChunkBgeM3Mapper.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/mapper/ChunkBgeM3Mapper.java) | 新增 `deleteByDocId`、`deleteByKbId`、`selectByKbId` |
| [ChunkBgeM3Mapper.xml](eshop_chatmind/src/main/resources/mapper/ChunkBgeM3Mapper.xml) | 对应 SQL |
| [KnowledgeBaseController.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/controller/KnowledgeBaseController.java) | 新增 `POST /api/knowledge-bases/{id}/sync` 端点 |

---

### 改进三：RAG 混合检索（向量 + BM25 + jieba 分词）

**问题**：纯向量语义检索可能遗漏精确关键词匹配，对专有名词/编号/代码等查询效果不佳。

**方案**：在向量检索基础上加入 BM25 关键词检索，使用 jieba 分词器处理中文文本，通过 RRF（Reciprocal Rank Fusion）融合排序。

```
查询 → jieba分词 → BM25关键词检索 (top5)  ─┐
     → bge-m3 Embedding → pgvector语义检索 (top5) ─┤→ RRF融合 → top5 结果
```

BM25 参数：k1=1.5, b=0.75，IDF 基于知识库内全量 chunk 计算。两边各取 top5，按 RRF 分数重排后返回 top5。

**涉及文件**：

| 文件 | 变更 |
|------|------|
| [pom.xml](eshop_chatmind/pom.xml) | 新增 `jieba-analysis:1.0.2` 依赖 |
| [KeywordSearchService.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/KeywordSearchService.java) | **新增** — 关键词检索接口 |
| [KeywordSearchServiceImpl.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/impl/KeywordSearchServiceImpl.java) | **新增** — BM25 + jieba 实现 |
| [RagService.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/RagService.java) | 新增 `hybridSearch` 方法 |
| [RagServiceImpl.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/service/impl/RagServiceImpl.java) | RRF 融合向量检索 + BM25 检索结果 |
| [KnowledgeTools.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/agent/tools/KnowledgeTools.java) | 改用 `hybridSearch` 替代 `similaritySearch` |
| [ChunkBgeM3Mapper.java](eshop_chatmind/src/main/java/com/fx/eshop_chatmind/mapper/ChunkBgeM3Mapper.java) | 新增 `selectByKbId` 方法 |
| [ChunkBgeM3Mapper.xml](eshop_chatmind/src/main/resources/mapper/ChunkBgeM3Mapper.xml) | 对应 SQL |

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

- Java 21+ / Maven 3.8+
- PostgreSQL 14+（需安装 pgvector 扩展）
- Ollama（本地部署 bge-m3 embedding 模型）
- Node.js 18+（前端）

### 1. 初始化数据库

```
\i /absolute/path/to/eshop_chatmind.sql
\i /absolute/path/to/eshop.sql
\i /absolute/path/to/eshop_data.sql
```

### 2. 配置 application.yaml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/your_database
    username: your_db_user
    password: your_db_password
  ai:
    deepseek:
      api-key: your_deepseek_api_key
```

### 3. 启动

```bash
# Ollama
ollama pull bge-m3 && ollama serve

# 后端
cd eshop_chatmind && ./mvnw spring-boot:run

# 前端
cd ui && npm install && npm run dev
```

后端 `http://localhost:8080`，前端 `http://localhost:5173`。

### 4. 使用流程

1. 创建知识库 → 上传 Markdown 文档 → 调用 `/api/knowledge-bases/{id}/sync` 同步
2. 创建 Agent → 配置模型、系统提示词、可选工具、可访问知识库
3. 选择 Agent → 开始对话
