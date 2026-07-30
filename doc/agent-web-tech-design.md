# ② Agent Web 应用技术设计

## 1. 文档说明

### 1.1 目标

本文档定义 `flow-mind` 第二块产物——② Agent Web 应用的技术方案、模块边界、状态机、数据模型、接口、Pi Agent 集成方式、安全约束和验收方案。

② Agent 面向业务人员，在开发期完成以下主线：

1. 通过多轮对话采集业务办理需求。
2. 将自然语言沉淀为可编辑、可确认的结构化需求。
3. 在用户确认后调用①流程平台创建流程定义草稿并完成发布前校验。
4. 在用户确认流程预览后发布、激活流程定义。
5. 基于已确认需求和③基础底座契约，由 LLM 直接生成 Spring Boot + Vue3 业务模块。
6. 在用户预览、编辑并确认代码后，将文件写入指定工作区。

Agent 是开发期生产工具。流程定义和业务代码完成落地后，Agent 不参与业务运行期；运行期由③基础底座和 Agent 生成的业务模块共同承载，并通过平台 Starter 调用①流程平台。待办、已办、我发起、已阅、审批详情和审批动作属于③基础底座的通用能力，不由 Agent 按业务重复生成。

### 1.2 设计依据

- `doc/rebuild-functional-requirements-optimized.md`
- `doc/流程平台设计与接口文档_v4.md`
- 当前仓库中 `platform-core` 的实际 Controller、DTO、枚举和校验实现
- [前期 ChatGPT 讨论](https://chatgpt.com/share/6a69a38f-9180-83ec-8942-b987328c8bba)，仅作为思路参考，不作为接口事实来源
- [Pi SDK 官方文档](https://pi.dev/docs/latest/sdk)
- [Pi Session 文档](https://pi.dev/docs/latest/sessions)
- [Pi Session 文件格式](https://pi.dev/docs/latest/session-format)
- [Pi 安全文档](https://pi.dev/docs/latest/security)

### 1.3 已冻结结论

| 项目 | 结论 |
| --- | --- |
| Agent 前端 | Vue3 + TypeScript + Vite + Pinia + Element Plus + Monaco Editor |
| Agent 后端 | NestJS + TypeScript |
| Node.js | `>= 22.19.0` |
| Pi 集成 | 直接依赖 `@earendil-works/pi-coding-agent@0.82.1`，使用 `AgentSession` |
| Pi 部署方式 | 与 NestJS 同进程，不复制 Pi 源码，不启动 RPC 子进程 |
| Agent 数据库 | SQLite，单机单实例 |
| 身份 | 本地 Mock 用户选择器，不实现正式登录 |
| 平台调用 | NestJS 通过流程平台 REST API 调用，不使用 Java Starter |
| 代码目标 | 依赖版本化的③基础底座契约，只生成业务模块 |
| 人工门禁 | 结构化需求确认、流程确认、代码确认三阶段 |
| 代码落地 | 只写指定工作区，不创建 Git 分支、提交或远端推送 |
| 自动验证边界 | 不自动编译、测试或修复生成代码 |

Pi `0.82.1` 的官方包要求 Node.js `>= 22.19.0`。Pi 当前仍是 `0.x` 版本，本项目必须精确锁定依赖并提交 lockfile，不使用 `^0.82.1` 自动升级。

## 2. 范围和职责边界

### 2.1 本期包含

- Agent 会话创建与按 ID 恢复。
- 自然语言多轮对话和 SSE 流式输出。
- 信息完整性判断、主动追问和结构化需求提交。
- 结构化需求预览、人工编辑和确认。
- 流程定义草稿创建、流程图保存、发布前校验、预览、发布和激活。
- 基于 LLM 的 Spring Boot + Vue3 业务模块直接代码生成。
- 代码树、内容、差异预览及人工编辑。
- 经确认的文件安全写入指定工作区。
- 查询“由 Agent 创建的流程定义”清单。
- 查询“由 Agent 生成的业务代码”清单。
- 全链路错误展示、幂等重试和当前工作流恢复。

### 2.2 本期不包含

- 正式用户登录、统一认证和 RBAC。
- Redis、消息队列、分布式锁、集群或多实例调度。
- Git 分支、commit、push、Pull Request。
- 通用流程设计器。
- Agent 自动运行生成代码、自动编译、自动测试和自动修复。
- 通用会话历史列表、LLM 调用历史列表、审计中心或统计报表。
- Agent 数据库中的对话消息副本和代码正文。
- ③基础底座本身的实现。
- 生产级容器编排和远程执行沙箱。

### 2.3 Pi 与业务编排的职责

| 能力 | Pi | Agent Web 业务层 |
| --- | --- | --- |
| LLM 调用和流式响应 | 负责 | 配置模型并转发事件 |
| Agent loop | 负责 | 不重复实现 |
| Tool calling | 负责执行机制 | 定义工具、参数、授权和边界 |
| 消息上下文和压缩 | 负责 | 保存 Pi Session 定位信息 |
| 业务信息完整性标准 | 不负责 | 负责定义 |
| 人工确认门禁 | 不负责 | 负责 |
| 流程生成状态机 | 不负责 | 负责 |
| 平台状态修改 | 不直接执行 | 负责确定性调用 |
| 代码暂存和最终写入授权 | 不负责 | 负责 |
| 两类管理清单 | 不负责 | 负责 |

核心原则是：**Pi 决定如何完成一次受限的推理任务，NestJS 决定当前业务阶段允许发生什么。**

## 3. 总体架构

```text
┌─────────────────────────────────────────────────────────────────────┐
│                         Browser / Vue3                              │
│  对话区 │ 需求编辑 │ 流程预览 │ 代码树/Monaco/Diff │ 两类管理清单 │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ HTTP + SSE
┌──────────────────────────────▼──────────────────────────────────────┐
│                    NestJS Agent Server                              │
│                                                                     │
│ API / Identity / Session                                            │
│          │                                                          │
│          ▼                                                          │
│ Agent Workflow State Machine                                        │
│   ├─ Requirement Orchestrator ───────────────┐                      │
│   ├─ Process Provisioning Service            │                      │
│   ├─ Code Generation Orchestrator ───────┐   │                      │
│   └─ Artifact Publish Service            │   │                      │
│                                          │   │                      │
│ Pi Adapter                               │   │                      │
│   ├─ Requirement AgentSession ◄──────────┘   │                      │
│   └─ Code Generation AgentSession ◄─────────┘                      │
│                                                                     │
│ Platform Client │ SQLite Repositories │ Staging/Backup File Store   │
└─────────┬──────────────────┬──────────────────┬─────────────────────┘
          │ REST             │                  │ allowlisted paths
          ▼                  ▼                  ▼
┌──────────────────┐  ┌─────────────┐  ┌─────────────────────────────┐
│ ①流程平台         │  │ Agent SQLite│  │ ③基础底座所在目标工作区      │
│ Spring Boot      │  │ 最小业务状态 │  │ Spring Boot + Vue3          │
└──────────────────┘  └─────────────┘  └─────────────────────────────┘
```

### 3.1 部署结构

第一阶段只支持单机单实例：

```text
agent-web/
├── frontend/                  # Vue3
├── backend/                   # NestJS + Pi SDK
└── data/
    ├── agent.db               # Agent SQLite
    ├── pi-sessions/           # Pi JSONL Session
    ├── staging/               # 生成代码暂存区
    └── backups/               # 最终写入前的旧文件快照
```

前端生产构建产物由 NestJS 静态托管，浏览器只访问同源地址。默认监听 `127.0.0.1`，避免 Mock 身份模式被误用为远程认证方案。

## 4. 工程与模块设计

### 4.1 建议目录

```text
agent-web/
├── package.json
├── package-lock.json
├── frontend/
│   └── src/
│       ├── api/
│       ├── components/
│       ├── stores/
│       ├── views/
│       │   ├── AgentWorkspaceView.vue
│       │   ├── CreatedDefinitionsView.vue
│       │   └── GeneratedCodeView.vue
│       └── types/
└── backend/
    └── src/
        ├── app/
        ├── identity/
        ├── session/
        ├── requirement/
        ├── workflow/
        ├── pi/
        │   ├── pi-adapter.service.ts
        │   ├── requirement-agent.factory.ts
        │   ├── code-agent.factory.ts
        │   ├── prompts/
        │   └── tools/
        ├── platform/
        ├── generation/
        ├── artifact/
        ├── persistence/
        ├── management/
        └── common/
```

### 4.2 后端模块职责

| 模块 | 职责 |
| --- | --- |
| `IdentityModule` | 解析并校验 Mock 用户头，生成当前用户上下文 |
| `SessionModule` | 创建会话、按 ID 加载、校验 owner、维护乐观锁版本 |
| `RequirementModule` | 结构化需求 Schema、完整性校验、人工修改与版本 |
| `WorkflowModule` | 状态机、门禁、阶段迁移、失败恢复和重试 |
| `PiModule` | 创建/恢复 AgentSession、注册受限工具、订阅 Pi 事件 |
| `PlatformModule` | 平台 REST 客户端、DTO 映射、幂等操作和错误翻译 |
| `GenerationModule` | 创建生成任务、构造上下文、控制暂存目录 |
| `ArtifactModule` | 文件清单、哈希、diff、编辑、最终写入和回滚 |
| `PersistenceModule` | SQLite 迁移、事务和三张核心表 Repository |
| `ManagementModule` | 仅提供流程定义清单与生成代码清单 |

### 4.3 前端页面

`AgentWorkspaceView` 使用一个工作台完成主线，不把三个确认阶段拆成彼此孤立的系统：

- 左侧：对话消息和输入框。
- 顶部：当前业务名称、Mock 用户、状态步骤条。
- 右侧标签：
  - 需求预览：结构化表单编辑、缺失项提示和确认按钮。
  - 流程预览：节点、连线、表单字段、附件和平台校验问题。
  - 代码预览：文件树、Monaco 编辑器、原文件/生成文件 diff。
- 底部：当前阶段允许的唯一主操作。

另提供两个管理页：

- `CreatedDefinitionsView`：Agent 创建的流程定义分页清单。
- `GeneratedCodeView`：Agent 生成的业务代码分页清单。

不提供通用会话历史页。

## 5. 核心状态机与三阶段门禁

### 5.1 状态定义

主状态：

| 状态 | 含义 | 用户可执行动作 |
| --- | --- | --- |
| `COLLECTING` | 正在多轮采集需求 | 发送消息 |
| `REQUIREMENT_REVIEW` | 已生成结构化需求，等待人工确认 | 编辑、确认、退回对话 |
| `PROCESS_REVIEW` | 平台草稿已创建且校验完成，等待确认 | 查看、修正、确认激活 |
| `PROCESS_ACTIVE` | 流程已发布激活 | 启动代码生成 |
| `CODE_GENERATING` | 代码生成进行中 | 查看流式进度、取消 |
| `CODE_REVIEW` | 代码已暂存，等待人工确认 | 查看、编辑、确认写入、重新生成 |
| `COMPLETED` | 已写入目标工作区 | 查看结果 |

瞬时与失败状态：

- `PROCESS_PROVISIONING`
- `PROCESS_PROVISION_FAILED`
- `PROCESS_ACTIVATING`
- `PROCESS_ACTIVATION_FAILED`
- `CODE_GENERATION_FAILED`
- `ARTIFACT_WRITING`
- `ARTIFACT_WRITE_FAILED`
- `CANCELLED`

### 5.2 状态迁移

```text
COLLECTING
   │ submit_requirement_snapshot
   ▼
REQUIREMENT_REVIEW
   │ 门禁一：确认结构化需求
   ▼
PROCESS_PROVISIONING
   ├─失败──► PROCESS_PROVISION_FAILED ──重试──┐
   ▼                                         │
PROCESS_REVIEW ◄──────────────────────────────┘
   │ 门禁二：确认流程
   ▼
PROCESS_ACTIVATING
   ├─失败──► PROCESS_ACTIVATION_FAILED ──重试
   ▼
PROCESS_ACTIVE
   │ 启动生成
   ▼
CODE_GENERATING
   ├─失败──► CODE_GENERATION_FAILED ──重试
   ▼
CODE_REVIEW
   │ 门禁三：确认精确文件清单和哈希
   ▼
ARTIFACT_WRITING
   ├─失败──► ARTIFACT_WRITE_FAILED ──恢复/重试
   ▼
COMPLETED
```

### 5.3 门禁规则

#### 门禁一：结构化需求确认

- 只允许 owner 确认。
- `BusinessRequirement` 必须通过 Schema 和完整性校验。
- 确认请求必须携带当前 `requirementRevision`，过期版本返回 `409`。
- 确认后该版本不可原地修改；修正产生新版本。
- 确认成功后由后端确定性创建平台草稿，模型无平台修改工具。

#### 门禁二：流程确认

- 草稿必须存在，且平台发布前校验 `valid=true`。
- 前端必须展示节点、连线、表单字段、附件模板和平台定义 ID。
- 确认后由后端依次发布、激活。
- 发布成功但激活失败时，不重复发布，只重试激活。
- 流程未进入 `PROCESS_ACTIVE` 前不得启动代码生成。

#### 门禁三：代码确认

- 展示所有新增/修改文件的 diff。
- 确认请求逐项携带相对路径和当前暂存内容 SHA-256。
- 请求中的文件集合必须与服务端待确认清单完全一致。
- 目标文件自预览后被外部修改时拒绝覆盖。
- 只有门禁三通过后才能写入目标工作区。

### 5.4 修订规则

- `REQUIREMENT_REVIEW` 阶段修正：创建新需求版本，仍停留在需求确认阶段。
- `PROCESS_REVIEW` 阶段修正：
  - 仅节点、连线、表单或附件变化时，更新当前草稿并重新校验。
  - 流程编码、名称等不可更新字段发生变化时，创建替代草稿；旧草稿标记为 `SUPERSEDED`，经用户确认后清理。
- 流程激活后的修正：调用平台复制定义能力创建下一版本草稿，返回 `PROCESS_REVIEW`，不修改已激活版本。
- `CODE_REVIEW` 阶段重新生成：创建新的 `agent_code_generation` 记录和新暂存目录，旧记录标记为 `SUPERSEDED`。

## 6. Pi Agent 集成设计

### 6.1 集成选择

Node.js/TypeScript 应用直接使用 Pi SDK：

```json
{
  "dependencies": {
    "@earendil-works/pi-coding-agent": "0.82.1"
  },
  "engines": {
    "node": ">=22.19.0"
  }
}
```

不采用以下方式：

- 不把 Pi 源码复制进项目。
- 不修改 Pi 内部包源码。
- 不通过前端直接调用 LLM。
- 不为 NestJS 再启动 `pi --mode rpc` 子进程。
- 不在浏览器中使用 Pi Web UI 保存 API Key 或运行 Agent。

所有 Pi API 调用集中封装在 `PiAdapterService`，业务模块不得直接依赖 Pi 类型。这样可隔离 Pi `0.x` 版本升级带来的 API 变化。

### 6.2 Session 持久化

- Requirement Agent 使用持久化 Pi Session。
- 每个代码生成任务使用独立的持久化 Pi Session，避免将长对话和大量源代码混入同一上下文。
- Session 目录固定为 `data/pi-sessions`，不使用用户主目录默认位置。
- SQLite 只保存 Pi Session ID 和 JSONL 文件绝对路径，不保存消息副本。
- 恢复时使用 `SessionManager.open(path, sessionDir)`。
- Pi JSONL 是模型上下文和工具调用轨迹的权威来源；`BusinessRequirement` 仍以 SQLite 中的确认版本为业务权威来源。

示意：

```ts
const sessionManager = SessionManager.create(cwd, piSessionDir);
const loader = new DefaultResourceLoader({
  cwd,
  agentDir,
  systemPromptOverride: () => requirementSystemPrompt,
});
await loader.reload();

const { session } = await createAgentSession({
  cwd,
  model,
  modelRuntime,
  sessionManager,
  resourceLoader: loader,
  noTools: "builtin",
  customTools: [submitRequirementSnapshotTool],
});
```

### 6.3 Requirement Agent

职责：

- 识别业务名称、业务编码、办理目标和参与角色。
- 收集表单字段、控件、校验、材料、审批层级、审批人规则、条件和多人模式。
- 信息不完整时只追问，不提交最终结构。
- 信息完整时调用 `submit_requirement_snapshot`。

唯一业务工具：

```ts
submit_requirement_snapshot({
  requirement: BusinessRequirement,
  missingItems: string[],
  ambiguities: string[],
  readyForReview: boolean
})
```

工具只做：

1. JSON Schema 校验。
2. 领域完整性校验。
3. 保存候选需求版本。
4. 将会话状态切换到 `REQUIREMENT_REVIEW`。

工具不得创建流程或写文件。

### 6.4 Code Generation Agent

每次生成使用新 AgentSession，并注入：

- 已确认 `BusinessRequirement`。
- 平台已激活流程定义详情快照。
- `TargetProjectManifest`。
- 允许读取的基础底座参考文件清单。
- 平台 Starter 公开接口约束，以及③基础底座已提供的通用查询、审批详情和审批动作契约。
- 禁止生成实体、Mapper、业务表和数据库迁移的硬约束。
- 禁止生成待办、已办、我发起、已阅、审批详情和审批动作等通用能力的硬约束。
- 必须生成的业务文件清单和完成标准。

自定义工具：

| 工具 | 作用 | 限制 |
| --- | --- | --- |
| `read_target_contract_file` | 读取基础底座契约允许的参考文件 | 只能读取 manifest 明确列出的相对路径 |
| `write_staged_file` | 写入一个生成文件 | 只能写当前任务暂存根目录 |
| `list_staged_files` | 查看本任务已生成文件 | 不能查看其他任务 |
| `delete_staged_file` | 删除本任务尚未确认的暂存文件 | 只能删除暂存文件 |
| `report_generation_complete` | 提交生成完成状态和文件清单 | 文件清单必须与暂存目录一致 |

代码 Agent 不拥有以下工具：

- Shell 或命令执行。
- 通用文件系统读写。
- Git。
- 流程平台修改。
- 目标工作区写入。

### 6.5 流式事件映射

NestJS 订阅 Pi `AgentSession` 事件，并转换为稳定的 Agent Web SSE 事件，不把 Pi 内部事件结构直接暴露给前端。

| Web 事件 | Pi 来源或业务来源 |
| --- | --- |
| `assistant.delta` | `message_update/text_delta` |
| `assistant.completed` | `message_end` |
| `agent.started` | `agent_start` |
| `agent.completed` | `agent_end` |
| `tool.started` | `tool_execution_start` |
| `tool.completed` | `tool_execution_end` |
| `workflow.state_changed` | Agent 业务状态机 |
| `requirement.ready` | 需求工具成功 |
| `process.validation_completed` | 平台调用完成 |
| `generation.file_changed` | 暂存文件工具成功 |
| `error` | Pi 或业务错误翻译 |

同一会话只允许一个 Pi run 处于活动状态。新的用户消息在当前 run 未结束时进入后端 FIFO 队列，run 空闲后按顺序提交；不允许两个 prompt 并发修改同一 Pi Session。

SSE 事件不另建数据库事件表。客户端每次连接或重连时，服务端先发送 `workflow.snapshot`，其中包含当前状态、row version、活动任务和可执行动作，再发送连接建立后的实时事件；客户端不能依靠重放全部历史 SSE 来恢复页面。

## 7. 核心领域契约

### 7.1 BusinessRequirement

```ts
interface BusinessRequirement {
  schemaVersion: "1.0";
  businessCode: string;
  businessName: string;
  systemCode: string;
  goal: string;
  participants: Participant[];
  formFields: FormFieldRequirement[];
  attachments: AttachmentRequirement[];
  nodes: ProcessNodeRequirement[];
  edges: ProcessEdgeRequirement[];
  businessRules: BusinessRule[];
}

interface Participant {
  roleCode: string;
  roleName: string;
  responsibility: string;
}

interface FormFieldRequirement {
  fieldCode: string;
  fieldName: string;
  fieldType: "string" | "number" | "date" | "boolean" | "select";
  controlType: "input" | "textarea" | "number" | "datePicker" | "select";
  required: boolean;
  validation: Record<string, unknown>;
  defaultValue?: string;
  options?: Array<{ label: string; value: string }>;
  sortOrder: number;
}

interface AttachmentRequirement {
  attachmentCode: string;
  attachmentName: string;
  description?: string;
  allowedExtensions: string[];
  maxSizeBytes: number;
  required: boolean;
  minCount: number;
  maxCount: number;
  applicableNodeCodes: string[];
  sortOrder: number;
}

interface ProcessNodeRequirement {
  nodeCode: string;
  nodeName: string;
  nodeType:
    | "START"
    | "USER_TASK"
    | "EXCLUSIVE_GATEWAY"
    | "PARALLEL_SPLIT_GATEWAY"
    | "PARALLEL_JOIN_GATEWAY"
    | "END";
  pairedGatewayCode?: string;
  approverRule?: {
    type:
      | "USER"
      | "STARTER"
      | "DEPARTMENT"
      | "ROLE"
      | "ROLE_IN_DEPARTMENT"
      | "APPROVER_EXPRESSION";
    config: Record<string, unknown>;
  };
  multiInstanceMode?: "SINGLE" | "OR_SIGN" | "COUNTERSIGN";
  positionX: number;
  positionY: number;
  sortOrder: number;
}

interface ProcessEdgeRequirement {
  edgeCode: string;
  sourceNodeCode: string;
  targetNodeCode: string;
  conditionExpression?: string;
  defaultEdge: boolean;
  sortOrder: number;
}

interface BusinessRule {
  ruleCode: string;
  description: string;
  expression?: string;
}
```

领域完整性校验至少包括：

- 业务编码、名称、目标和系统编码非空。
- 至少存在一个参与角色。
- 表单字段编码唯一，且能映射到平台支持的字段与控件类型。
- 附件编码唯一，必填附件 `minCount >= 1`。
- 恰好一个 `START`，至少一个 `END`。
- 连线引用存在的节点。
- 用户任务必须提供审批人规则和多人模式。
- 并行拆分/汇聚网关必须成对。
- 排他网关的条件线和默认线符合平台约束。
- 所有节点可从开始节点到达，并存在有限结束路径。

### 7.2 RequirementRevision

```ts
interface RequirementRevision {
  sessionId: string;
  revision: number;
  requirement: BusinessRequirement;
  missingItems: string[];
  ambiguities: string[];
  readyForReview: boolean;
  source: "AGENT" | "USER_EDIT";
  confirmedBy?: string;
  confirmedAt?: string;
  createdAt: string;
}
```

SQLite 只在 `agent_session` 保存当前有效结构化需求 JSON 和版本，历史版本不建设独立可查询表。Pi Session 保留对话过程；为保证后续修订不会破坏追溯，流程和代码记录各自保存其实际消费的确认需求快照。这些快照不提供单独的历史列表接口。

### 7.3 TargetProjectManifest

③基础底座必须在根目录提供版本化 manifest，例如 `.flowmind/target-manifest.json`：

M0-M2 创建会话时 `targetRoot` 可省略；即使传入也只校验并保存规范化绝对路径，不读取 manifest。
`AGENT_ALLOWED_TARGET_ROOTS` 白名单、reparse point 和 manifest 契约的强校验在 M3 启动代码生成时执行。

```json
{
  "contractVersion": "1.0",
  "projectId": "flowmind-business-base",
  "backend": {
    "rootDir": "backend",
    "javaVersion": "8",
    "springBootVersion": "2.7.18",
    "basePackage": "com.flowmind.business",
    "generatedSourceDir": "src/main/java/com/flowmind/business/generated",
    "generatedResourceDir": "src/main/resources/generated",
    "starter": {
      "groupId": "com.flowmind",
      "artifactId": "platform-starter",
      "version": "0.1.0-SNAPSHOT"
    }
  },
  "frontend": {
    "rootDir": "frontend",
    "framework": "vue3",
    "generatedViewDir": "src/modules/generated",
    "generatedApiDir": "src/api/generated",
    "routeRegistry": "src/router/generated-routes.ts"
  },
  "providedCapabilities": [
    "TODO_QUERY",
    "DONE_QUERY",
    "INITIATED_QUERY",
    "READ_QUERY",
    "APPROVAL_DETAIL",
    "TASK_ACTION"
  ],
  "readableReferenceFiles": [
    "backend/pom.xml",
    "frontend/package.json",
    "frontend/src/router/generated-routes.ts"
  ],
  "allowedOutputPatterns": [
    "backend/src/main/java/com/flowmind/business/generated/**/*.java",
    "backend/src/main/resources/generated/**/*",
    "frontend/src/modules/generated/**/*",
    "frontend/src/api/generated/**/*",
    "frontend/src/router/generated-routes.ts"
  ]
}
```

规则：

- `contractVersion` 不受支持时禁止生成。
- 输出路径必须匹配 `allowedOutputPatterns`。
- 可读取文件必须同时属于目标根目录并出现在 `readableReferenceFiles`。
- manifest 不允许通过符号链接或 Windows reparse point 跳出目标根。
- `providedCapabilities` 声明③基础底座已经提供的运行期通用能力；首期至少包含待办、已办、我发起、已阅、审批详情和审批动作。
- Agent 不能生成或修改上述通用能力，也不能生成按业务命名但仅转调平台审批能力的重复包装接口。
- 生成前预检基础底座的 `pom.xml` 已引入平台 Starter、通用业务后端依赖，通用业务后端已提供审批动作，前端已提供业务路由注册点和审批详情嵌入契约；缺失时停止生成并报告契约不兼容，本期不由模型补造通用能力或任意改写构建描述文件。

### 7.4 ArtifactManifest

```ts
interface ArtifactManifest {
  generationId: string;
  targetRoot: string;
  contractVersion: string;
  revision: number;
  files: ArtifactFile[];
}

interface ArtifactFile {
  relativePath: string;
  changeType: "ADD" | "MODIFY";
  stagedSha256: string;
  baseSha256?: string;
  sizeBytes: number;
  mediaType: "text/plain";
  validationStatus: "PENDING" | "VALID" | "INVALID";
  validationMessages: string[];
  editedByUser: boolean;
}
```

本期不支持由 Agent 删除目标文件，避免一次生成误删基础底座文件。

## 8. SQLite 设计

### 8.1 使用原则

- SQLite 只服务单机单实例。
- 启用 WAL、foreign keys 和 busy timeout。
- 所有状态迁移在事务内执行。
- 使用 `row_version` 乐观锁防止浏览器多标签页覆盖。
- JSON 字段写入前必须通过应用 Schema 校验。
- 不在 SQLite 保存对话消息、工具调用明细或代码正文。
- 不建设通用审计表、消息表、模型调用表和历史版本表。

### 8.2 agent_session

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT PK | Agent 会话 ID |
| `owner_user_id` | TEXT | Mock 用户 ID |
| `owner_user_name` | TEXT | Mock 用户名称 |
| `target_root` | TEXT NULL | 可选目标工作区绝对路径；M3 前不执行 manifest 强校验 |
| `state` | TEXT | 当前工作流状态 |
| `row_version` | INTEGER | 乐观锁版本 |
| `requirement_revision` | INTEGER | 当前需求版本 |
| `requirement_json` | TEXT | 当前结构化需求 |
| `requirement_missing_items_json` | TEXT | 当前版本缺失项 JSON 数组 |
| `requirement_ambiguities_json` | TEXT | 当前版本歧义项 JSON 数组 |
| `requirement_ready_for_review` | INTEGER | 当前版本是否可进入门禁一 |
| `requirement_source` | TEXT NULL | `AGENT/USER_EDIT` |
| `requirement_confirmed_at` | TEXT NULL | 门禁一确认时间 |
| `requirement_confirm_key` | TEXT NULL | 门禁一的 Agent Web 幂等号 |
| `requirement_confirm_hash` | TEXT NULL | 门禁一请求摘要 |
| `requirement_confirm_result_json` | TEXT NULL | 门禁一首次受理结果摘要 |
| `pi_session_id` | TEXT | Requirement Pi Session ID |
| `pi_session_file` | TEXT | Requirement Pi JSONL 路径 |
| `last_error_code` | TEXT NULL | 最近失败码 |
| `last_error_message` | TEXT NULL | 可展示的最近失败信息 |
| `created_at` | TEXT | 创建时间 |
| `updated_at` | TEXT | 更新时间 |

索引：

- `idx_agent_session_owner_updated(owner_user_id, updated_at)`

该索引仅用于按已知会话 ID校验 owner 和内部维护，不开放通用会话列表接口。

### 8.3 agent_process_definition

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT PK | 本地记录 ID |
| `session_id` | TEXT FK | Agent 会话 ID |
| `requirement_revision` | INTEGER | 消费的需求版本 |
| `platform_definition_id` | TEXT | 平台流程定义 ID |
| `process_code` | TEXT | 流程编码 |
| `process_name` | TEXT | 流程名称 |
| `definition_version` | INTEGER NULL | 平台定义版本 |
| `status` | TEXT | `DRAFT/VALIDATED/PUBLISHED/ACTIVE/FAILED/SUPERSEDED` |
| `saga_step` | TEXT | 最近完成的流程平台落地步骤 |
| `requirement_snapshot_json` | TEXT | 本流程定义消费的确认需求快照 |
| `validation_json` | TEXT NULL | 平台校验结果 |
| `platform_snapshot_json` | TEXT NULL | 最近一次平台定义详情快照 |
| `create_operation_id` | TEXT | 创建/复制幂等号 |
| `save_operation_id` | TEXT | 保存流程图幂等号 |
| `publish_operation_id` | TEXT | 发布幂等号 |
| `activate_operation_id` | TEXT | 激活幂等号 |
| `process_confirm_key` | TEXT NULL | 门禁二的 Agent Web 幂等号 |
| `process_confirm_hash` | TEXT NULL | 门禁二请求摘要 |
| `process_confirm_result_json` | TEXT NULL | 门禁二首次受理结果摘要 |
| `retry_key` | TEXT NULL | 当前失败步骤重试幂等号 |
| `retry_hash` | TEXT NULL | 当前失败步骤重试摘要 |
| `retry_result_json` | TEXT NULL | 当前失败步骤首次重试结果摘要 |
| `last_error_code` | TEXT NULL | 最近平台错误码 |
| `last_error_message` | TEXT NULL | 最近平台错误信息 |
| `created_by` | TEXT | 创建用户 |
| `created_at` | TEXT | 创建时间 |
| `activated_at` | TEXT NULL | 激活时间 |
| `updated_at` | TEXT | 更新时间 |

约束与索引：

- `UNIQUE(platform_definition_id)`
- `UNIQUE(create_operation_id)`
- `idx_agent_definition_owner_created(created_by, created_at)`
- `idx_agent_definition_process(process_code, definition_version)`

### 8.4 agent_code_generation

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | TEXT PK | 生成任务 ID |
| `session_id` | TEXT FK | Agent 会话 ID |
| `process_definition_record_id` | TEXT FK | 对应的流程定义记录 |
| `requirement_revision` | INTEGER | 消费的需求版本 |
| `requirement_snapshot_json` | TEXT | 本次代码生成消费的确认需求快照 |
| `business_code` | TEXT | 业务编码 |
| `business_name` | TEXT | 业务名称 |
| `status` | TEXT | `GENERATING/REVIEW/WRITING/COMPLETED/FAILED/SUPERSEDED` |
| `target_root` | TEXT | 目标工作区规范化绝对路径 |
| `target_contract_version` | TEXT | 基础底座契约版本 |
| `staging_dir` | TEXT | 本任务暂存目录 |
| `backup_dir` | TEXT NULL | 最终写入备份目录 |
| `artifact_manifest_json` | TEXT | 文件元数据，不含正文 |
| `pi_session_id` | TEXT | Code Pi Session ID |
| `pi_session_file` | TEXT | Code Pi JSONL 路径 |
| `generation_revision` | INTEGER | 代码预览乐观锁版本 |
| `created_by` | TEXT | 创建生成任务的用户 |
| `confirmed_by` | TEXT NULL | 门禁三确认人 |
| `confirmed_at` | TEXT NULL | 门禁三确认时间 |
| `write_confirm_key` | TEXT NULL | 门禁三的 Agent Web 幂等号 |
| `write_confirm_hash` | TEXT NULL | 门禁三请求摘要 |
| `written_at` | TEXT NULL | 完成写入时间 |
| `last_error_code` | TEXT NULL | 最近错误码 |
| `last_error_message` | TEXT NULL | 最近错误信息 |
| `created_at` | TEXT | 创建时间 |
| `updated_at` | TEXT | 更新时间 |

索引：

- `idx_agent_generation_owner_created(created_by, created_at)`
- `idx_agent_generation_business(business_code, created_at)`
- `idx_agent_generation_session(session_id, created_at)`

### 8.5 数据保留

- Pi JSONL、暂存目录和备份目录是 Agent 的本地运行数据。
- `COMPLETED` 后暂存文件默认保留 30 天，备份默认保留 30 天，清理由本地维护命令触发。
- 清理只删除已过期且不处于活动状态的目录，不删除 SQLite 清单记录。
- 不提供 Web 删除接口。

## 9. Agent Web API

### 9.1 通用约定

请求头：

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `X-Agent-User-Id` | 是 | Mock 用户 ID |
| `X-Agent-User-Name` | 否 | Mock 用户名称 |
| `If-Match` | 修改接口按需 | 当前 `rowVersion` 或生成版本 |
| `Idempotency-Key` | 确认/重试接口是 | Agent Web 操作幂等号 |

统一错误：

```json
{
  "code": "AGENT_STATE_CONFLICT",
  "message": "current state does not allow this operation",
  "sessionId": "ags_xxx",
  "requestId": "req_xxx",
  "details": {}
}
```

HTTP 语义：

- `400`：Schema 或业务校验失败。
- `403`：用户不是会话 owner。
- `404`：按 ID 查询的资源不存在。
- `409`：状态、版本、幂等号或文件哈希冲突。
- `422`：平台发布前校验未通过。
- `502`：流程平台或 LLM Provider 调用失败。
- `503`：模型、平台或本地存储未就绪。

### 9.2 工作流接口

| 方法 | 路径 | 允许状态 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/agent/sessions` | - | 创建会话 |
| GET | `/api/agent/sessions/{sessionId}` | 任意 | 按已知 ID 查询当前工作流快照 |
| POST | `/api/agent/sessions/{sessionId}/messages` | `COLLECTING` | 提交用户消息 |
| GET | `/api/agent/sessions/{sessionId}/events` | 任意 | SSE 事件流 |
| GET | `/api/agent/sessions/{sessionId}/requirement` | `REQUIREMENT_REVIEW+` | 查询当前结构化需求 |
| PUT | `/api/agent/sessions/{sessionId}/requirement` | `REQUIREMENT_REVIEW` | 人工编辑需求并增加版本 |
| POST | `/api/agent/sessions/{sessionId}/requirement/confirm` | `REQUIREMENT_REVIEW` | 门禁一并异步创建流程草稿 |
| POST | `/api/agent/sessions/{sessionId}/requirement/reopen` | `REQUIREMENT_REVIEW` | 退回对话采集 |
| GET | `/api/agent/sessions/{sessionId}/process-preview` | `PROCESS_REVIEW+` | 查询流程预览和校验结果 |
| POST | `/api/agent/sessions/{sessionId}/process/confirm` | `PROCESS_REVIEW` | 门禁二，发布并激活 |
| POST | `/api/agent/sessions/{sessionId}/process/retry` | 流程失败态 | 重试失败步骤 |
| POST | `/api/agent/sessions/{sessionId}/code-generations` | `PROCESS_ACTIVE` | 启动代码生成 |
| GET | `/api/agent/sessions/{sessionId}/code-generations/{generationId}` | 已知 ID | 查询生成状态和 manifest |
| GET | `/api/agent/sessions/{sessionId}/code-generations/{generationId}/files/{*path}` | `CODE_REVIEW+` | 读取一个暂存文件 |
| PUT | `/api/agent/sessions/{sessionId}/code-generations/{generationId}/files/{*path}` | `CODE_REVIEW` | 人工编辑一个暂存文件 |
| GET | `/api/agent/sessions/{sessionId}/code-generations/{generationId}/diff/{*path}` | `CODE_REVIEW+` | 查看目标文件差异 |
| POST | `/api/agent/sessions/{sessionId}/code-generations/{generationId}/regenerate` | `CODE_REVIEW` | 创建新生成任务 |
| POST | `/api/agent/sessions/{sessionId}/code-generations/{generationId}/confirm-write` | `CODE_REVIEW` | 门禁三并写入工作区 |
| POST | `/api/agent/sessions/{sessionId}/code-generations/{generationId}/retry-write` | `ARTIFACT_WRITE_FAILED` | 在重新校验哈希后重试 |

`POST /sessions` 示例：

```json
{
  "targetRoot": "E:\\business-workspace\\flowmind-business-base"
}
```

`targetRoot` 在 M0-M2 为可选字段；空请求体 `{}` 同样允许创建会话。

响应：

```json
{
  "sessionId": "ags_01...",
  "state": "COLLECTING",
  "rowVersion": 0,
  "eventsUrl": "/api/agent/sessions/ags_01.../events"
}
```

`confirm-write` 示例：

```json
{
  "generationRevision": 3,
  "files": [
    {
      "relativePath": "backend/src/main/java/com/flowmind/business/generated/entry/EntryApplicationController.java",
      "sha256": "..."
    },
    {
      "relativePath": "frontend/src/modules/generated/entry/EntryApplicationApply.vue",
      "sha256": "..."
    }
  ]
}
```

### 9.3 仅有的两类管理清单

#### Agent 创建的流程定义

```http
GET /api/agent/created-process-definitions?page=1&pageSize=20&processCode=&status=
```

返回字段：

- 本地记录 ID
- Agent 会话 ID
- 平台流程定义 ID
- 流程编码、名称、版本
- 状态
- 创建人、创建时间、激活时间
- 最近失败码和失败信息

只返回 `created_by` 等于当前 `X-Agent-User-Id` 的记录。

#### Agent 生成的业务代码

```http
GET /api/agent/generated-code?page=1&pageSize=20&businessCode=&status=
```

返回字段：

- 生成任务 ID
- Agent 会话 ID
- 平台流程定义 ID
- 业务编码、名称
- 状态
- 目标工作区
- 基础底座契约版本
- 文件数量
- 确认人、确认时间和写入时间
- 最近失败码和失败信息

只返回当前用户拥有的会话所产生的记录。清单不返回代码正文。

禁止增加以下管理接口：

- `/api/agent/sessions` 的 GET 列表。
- 通用 Agent 操作日志列表。
- LLM 调用或 token 明细列表。
- 所有生成文件全文检索。

## 10. 流程平台集成

### 10.1 调用方式

NestJS 不能使用 Java Starter，因此开发期通过①平台独立服务 REST API 建流程。③业务代码仍必须通过 `platform-starter` 同进程调用平台，不能照搬 Agent 的 HTTP 集成方式。

Agent 调用当前源码中的实际接口：

| 步骤 | 方法 | 当前实际路径 |
| --- | --- | --- |
| 创建定义草稿 | POST | `/api/platform/definitions` |
| 保存流程图 | PUT | `/api/platform/definitions/{definitionId}/graph` |
| 发布前校验 | GET | `/api/platform/definitions/{definitionId}/publish-validation` |
| 发布 | POST | `/api/platform/definitions/publish` |
| 激活 | POST | `/api/platform/definitions/activate` |
| 复制为新草稿 | POST | `/api/platform/definitions/{definitionId}/copy` |
| 查询定义详情 | GET | `/api/platform/definitions/{definitionId}` |
| 删除未激活草稿 | DELETE | `/api/platform/definitions` |

以当前 Controller 实现为准，不使用旧设计草案中不同的路径形式。

### 10.2 身份传递

Agent 收到：

```text
X-Agent-User-Id: user_sales
X-Agent-User-Name: Sales User
```

调用平台时转换为：

```text
X-Flow-User-Id: user_sales
X-Flow-User-Name: Sales User
```

对于包含 `operatorUserId` 的流程定义请求，还在请求体中写入相同用户 ID；附件模板接口只使用 `X-Flow-User-Id` 请求头。部门信息来自 Agent 本地 Mock 用户配置，可选传递：

- `X-Flow-Dept-Id`
- `X-Flow-Dept-Name`

### 10.3 平台 DTO 映射

`BusinessRequirement` 映射为当前平台请求：

- `CreateProcessDefinitionRequest`
  - `processCode`
  - `processName`
  - `systemCode`
  - `remark`
  - `operatorUserId`
  - `operationId`
- `SaveProcessGraphRequest`
  - `nodes`
  - `edges`
  - `formFields`
  - `attachmentConfigs`
  - `operatorUserId`
  - `operationId`
- `DefinitionOperationRequest`
  - `definitionId`
  - `operatorUserId`
  - `operationId`

节点和审批枚举必须使用平台当前值：

- 节点：`START`、`USER_TASK`、`EXCLUSIVE_GATEWAY`、`PARALLEL_SPLIT_GATEWAY`、`PARALLEL_JOIN_GATEWAY`、`END`
- 审批人：`USER`、`STARTER`、`DEPARTMENT`、`ROLE`、`ROLE_IN_DEPARTMENT`、`APPROVER_EXPRESSION`
- 多人模式：`SINGLE`、`OR_SIGN`、`COUNTERSIGN`

### 10.4 附件模板接口审查与调用规则

`SaveProcessGraphRequest.attachmentConfigs` 只能引用已经存在的 `attachmentTemplateId`。当前
`ProcessAttachmentTemplateController` 已提供以下实际 REST API：

| 方法 | 当前实际路径 | Controller 行为 | Agent 是否使用 |
| --- | --- | --- | --- |
| POST | `/api/platform/attachment-templates` | 创建同一 `attachmentCode` 的下一模板版本，返回新版本 DTO | 是 |
| GET | `/api/platform/attachment-templates` | 按 `attachmentCode`、`templateStatus`、`templateVersion` 可选过滤，按编码升序、版本降序返回列表 | 是 |
| GET | `/api/platform/attachment-templates/{attachmentTemplateId}` | 按模板版本 ID 查询详情 | 可选，用于按 ID 复核 |
| PUT | `/api/platform/attachment-templates/{attachmentTemplateId}` | 原地更新允许修改的版本，也可通过 `templateStatus=DISABLED` 禁用 | 否 |

审查结论：

- 当前 `POST + GET` 已满足首期 Agent 创建、查询和复用附件模板版本的最小需求，不再把附件模板 REST API 作为待补充的前置能力。
- 列表接口当前返回 `List`，不支持分页。Agent 必须同时传入 `attachmentCode` 和 `templateStatus=ENABLED` 做窄查询；首期无需为 Agent 单独增加分页接口。
- Agent 不负责模板治理，不调用更新或禁用接口。详情查询和模板更新继续作为平台管理能力存在。
- 附件模板创建是流程草稿落地过程中的一个普通步骤，当前接口不使用 `operationId`；其失败由流程落地状态机统一处理，不建设附件模板专用幂等和恢复机制。

创建请求体使用 `ProcessAttachmentTemplateDTO` 中的模板字段，操作人通过 `X-Flow-User-Id` 请求头传递：

```json
{
  "attachmentCode": "bankReceipt",
  "attachmentName": "银行回单",
  "description": "入金申请银行回单",
  "allowedExtensions": ["pdf", "jpg", "png"],
  "maxSizeBytes": 10485760
}
```

Agent 的处理规则：

1. 调用 `GET /api/platform/attachment-templates?attachmentCode={code}&templateStatus=ENABLED` 查询启用模板；返回结果已按版本降序排列。
2. 若名称、标准化后的扩展名集合和大小完全匹配，复用最高版本。
3. 若不存在匹配版本，调用 `POST /api/platform/attachment-templates` 创建下一版本。
4. 将返回的模板 ID 写入 `attachmentConfigs`。
5. 创建或查询失败时，本次流程草稿落地失败；用户重试流程落地时重新从第 1 步查询，不单独恢复附件模板步骤。
6. 必填、数量和适用节点属于流程定义附件配置，不属于全局模板。

### 10.5 草稿创建事务边界

流程平台的多个 REST 调用无法与 Agent SQLite 组成分布式事务，因此使用 Saga 式步骤记录：

1. SQLite 创建 `PROCESS_PROVISIONING` 记录，并为支持幂等号的流程定义修改操作生成稳定 operation ID。
2. 创建或复制流程定义草稿。
3. 创建/复用附件模板。
4. 保存流程图。
5. 执行发布前校验。
6. 更新本地记录为 `VALIDATED`，会话进入 `PROCESS_REVIEW`。

失败处理：

- 已创建草稿但保存失败：保留草稿和 definition ID，修正后用同一 `save_operation_id` 语义重试。
- 附件模板查询或创建失败：本次流程落地失败；重试流程落地时重新执行附件模板查询和复用逻辑。
- 校验失败：不是网络失败，保存 `validation_json` 并进入 `PROCESS_REVIEW`，前端展示问题但禁用确认。
- 同号重试：复用原 `operationId`，不得生成新幂等号。
- 同号请求内容变化：本地直接拒绝，避免平台返回 operation ID 冲突。

门禁二：

1. 使用 `publish_operation_id` 发布。
2. 发布成功后使用 `activate_operation_id` 激活。
3. 激活失败只重试激活，不回滚已发布定义。
4. 激活成功后保存平台返回快照并进入 `PROCESS_ACTIVE`。

## 11. 代码生成规范

### 11.1 必须生成

每个具体业务至少生成：

后端：

- 业务 Controller。
- 业务 Service。
- 请求/响应 DTO。
- 业务校验。
- 业务发起、业务详情等业务特定场景所需的平台 Starter 调用适配。
- 必要配置片段或已有配置的受控修改。
- 发起接口，例如 `POST /api/entry-application/submit`。
- 详情接口，例如 `GET /api/entry-application/{instanceId}`。

审批通过、驳回、退回、转办等任务动作与具体业务名称无关，由③基础底座的通用业务后端统一提供 REST 接口并通过平台 Starter 转调①平台。Agent 不生成 `POST /api/entry-application/{taskId}/approve` 这类按业务命名、内部仅转调平台审批能力的接口。只有发起、业务详情组装和确有业务差异的校验或联动属于 Agent 生成的业务后端；业务差异通过基础底座预定义的扩展契约接入，不能以重复实现通用审批端点的方式接入。

前端：

- 业务录入页。
- 业务 API 客户端。
- 业务路由模块或路由注册项。
- 表单字段和附件上传控件。

源文件内容由 LLM 直接生成，不使用按业务字段机械替换的代码模板。基础底座 manifest、强约束 prompt 和受限工具用于提供上下文与边界，不改变“LLM 直接生成”的需求口径。

### 11.2 禁止生成

- JPA/MyBatis 实体。
- Mapper、Repository 或业务 DAO。
- 业务建表 SQL、Flyway/Liquibase 脚本。
- 独立业务数据库配置。
- 自行实现的流程流转引擎。
- 前端直接调用①平台的代码。
- 通用待办、已办、我发起、已阅、审批详情框架或审批动作 REST 接口。
- 按业务名称包装但仅转调通用审批能力的 Controller、Service 或前端 API，例如 `/api/entry-application/{taskId}/approve`。
- Java 9+ API、`record`、`var` 或 Spring Boot 3/Jakarta 代码。

### 11.3 Spring Boot 约束

- Java 8。
- Spring Boot 2.7.18。
- Starter 坐标：

```xml
<dependency>
    <groupId>com.flowmind</groupId>
    <artifactId>platform-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

- 业务表单值全部写入流程变量。
- Agent 生成的业务 Service 仅在业务发起、详情组装等业务特定场景通过 Starter 暴露的 Service 同进程调用。
- 待办、已办、我发起、已阅、审批详情和审批动作由③通用业务后端通过 Starter 统一转调平台，不在业务模块重复生成。
- 若审批前后确需业务校验或联动，必须使用③基础底座预先定义的扩展点；扩展点不存在时应报告基础底座契约不兼容，而不是由模型自行发明审批接口。

### 11.4 Vue3 约束

- 业务前端只调用③业务后端。
- 业务录入页调用 Agent 生成的业务后端；通用待办和审批详情页调用③通用业务后端，业务前端不得新增按业务划分的审批动作客户端。
- 字段编码必须与平台流程表单字段编码一致。
- 控件与 `controlType` 一致。
- 附件控件与附件模板配置一致。
- 业务页面作为路由模块在构建时打包，不做运行时远程模块加载。

### 11.5 本期校验

Agent 只做确定性静态规则：

- manifest 和相对路径校验。
- 必需文件清单校验。
- UTF-8 文本与单文件大小限制。
- Java 包名、类名和路由命名校验。
- 禁止实体、Mapper、数据库迁移和平台 HTTP 直调的关键字/AST 规则。
- 前端字段与结构化需求字段一致性。
- 文件哈希和文件冲突校验。

这些检查不能证明代码可编译。生成代码是否编译、启动和端到端通过，由验收环境执行，不由 Agent 自动执行或修复。

## 12. 文件暂存、预览和最终写入

### 12.1 目录

```text
data/staging/{sessionId}/{generationId}/
data/backups/{generationId}/{writeAttemptId}/
```

代码正文只存在暂存目录、备份目录和最终工作区，不进入 SQLite。

### 12.2 路径安全

每次读写前必须：

1. 拒绝绝对输出路径、UNC 路径和带盘符的相对字段。
2. 使用 `path.resolve(targetRoot, relativePath)` 得到候选路径。
3. 校验候选路径仍在规范化目标根下；Windows 比较忽略大小写。
4. 检查已存在的父目录没有符号链接或 reparse point 跳出根目录。
5. 校验路径匹配 manifest 的 `allowedOutputPatterns`。
6. 拒绝 `.git`、`.env`、凭据文件、Agent 数据目录和基础底座非生成区域。

### 12.3 Diff 基线

生成完成时：

- 新文件记为 `ADD`。
- 已有文件记为 `MODIFY`，记录 `baseSha256`。
- 前端展示统一 diff。
- 用户编辑暂存文件后重新计算 `stagedSha256`，增加 `generationRevision`。
- 每次打开 diff 时可重新读取目标文件；如果目标哈希变化，标记 `STALE`。

### 12.4 确认写入

写入步骤：

1. 校验会话 owner、状态和幂等号。
2. 校验请求的 generation revision。
3. 校验确认文件集合与 manifest 完全一致。
4. 逐个校验确认 SHA-256 与当前暂存内容一致。
5. 重新读取所有目标文件并验证 `baseSha256`。
6. 在备份目录保存所有待修改旧文件和写入清单。
7. 将新内容写入目标文件同目录的临时文件。
8. 使用原子 rename 替换单个目标文件。
9. 全部成功后更新 SQLite 为 `COMPLETED`。

多文件无法形成文件系统级原子事务。若中途失败：

- 根据写入清单逆序恢复已修改文件。
- 删除本次已经写入的新文件。
- 保留暂存和备份目录。
- 会话进入 `ARTIFACT_WRITE_FAILED`。
- 前端展示恢复结果；恢复不完整时禁止自动重试。

## 13. 身份、安全和配置

### 13.1 Mock 身份

本地配置提供允许的演示用户：

```yaml
agent:
  mockUsers:
    - userId: user_sales
      userName: Sales User
      departmentId: dept_sales
      departmentName: Sales Department
```

`X-Agent-User-Id` 必须属于允许列表。用户只能访问自己创建的会话及其流程、生成任务和文件。

Mock 请求头可以被伪造，因此：

- 默认只监听 `127.0.0.1`。
- 不允许以此配置部署到共享网络。
- 后续接入正式认证时只替换 `CurrentAgentUserProvider`，领域服务继续使用可信用户上下文。

### 13.2 LLM 凭据和模型

- 浏览器不得接触 LLM API Key。
- 使用 Pi `ModelRuntime` 在服务端解析 Provider 凭据。
- `PI_MODEL` 必填，格式为 `provider/model`。
- `PI_THINKING_LEVEL` 默认 `medium`。
- Provider 凭据通过服务端环境变量或专用 Pi auth 文件提供。
- 启动时校验指定模型存在且认证可用；否则 readiness 失败，Agent 不接受新会话。
- 日志不得打印 API Key、OAuth token、完整 system prompt 或完整代码内容。

### 13.3 Pi 安全边界

Pi 官方明确说明其没有内置沙箱，工具以 Pi 进程权限运行。因此本项目不能把“项目信任”误认为执行隔离：

- `noTools: "builtin"` 禁用内置工具。
- 只注册项目自定义受限工具。
- 工具内部再次执行 owner、状态和路径检查，不能只依赖模型提示词。
- Pi 的 `cwd` 指向任务专用受控目录，不直接指向目标工作区。
- 不加载目标工程中的 `.pi/extensions`、项目技能或不受信任的动态扩展。
- 使用受控 `ResourceLoader`，只注入本项目固定 prompt 和必要参考。

### 13.4 主要配置

| 配置 | 说明 |
| --- | --- |
| `AGENT_BIND_HOST` | 默认 `127.0.0.1` |
| `AGENT_PORT` | Agent Web 端口 |
| `AGENT_DATA_DIR` | SQLite、Pi Session、暂存和备份根目录 |
| `AGENT_ALLOWED_TARGET_ROOTS` | 可选目标工作区根目录白名单 |
| `FLOW_PLATFORM_BASE_URL` | ①平台独立服务地址 |
| `PI_MODEL` | `provider/model` |
| `PI_THINKING_LEVEL` | 默认 `medium` |
| Provider API Key | Pi 支持的服务端凭据环境变量 |
| `AGENT_STAGING_RETENTION_DAYS` | 默认 30 |
| `AGENT_BACKUP_RETENTION_DAYS` | 默认 30 |

## 14. 并发、幂等和错误处理

### 14.1 并发

- 单会话使用进程内 mutex 串行执行业务命令。
- 数据库仍使用 `row_version`，防止多标签页和异步回调覆盖。
- 同一生成任务一次只能有一个写入 attempt。
- SQLite `SQLITE_BUSY` 在 busy timeout 内重试，超时后返回 `503`。

### 14.2 Agent Web 幂等

确认、重试和写入接口要求 `Idempotency-Key`：

- 同一 key、同一请求摘要：返回首次结果。
- 同一 key、不同请求摘要：返回 `409 AGENT_IDEMPOTENCY_CONFLICT`。
- 不新增通用幂等表；当前阶段的 key、请求摘要和结果摘要保存在对应 session、definition 或 generation 记录的阶段字段中。

### 14.3 平台幂等

- 对支持幂等号的流程定义创建、保存、发布和激活等状态修改步骤，分别使用独立 `operationId`。
- operation ID 在第一次调用前写入 SQLite；网络超时后状态未知时使用同一 operation ID 重试，禁止因为点击重试而生成新号。
- 附件模板接口不使用 `operationId`，失败时由流程落地状态机按 10.4 从查询步骤重新执行。

### 14.4 错误分类

| 分类 | 示例 | 行为 |
| --- | --- | --- |
| 用户输入错误 | 字段缺失、节点引用错误 | 保持当前评审状态，展示字段问题 |
| 状态冲突 | 重复确认、过期 revision | `409`，刷新当前快照 |
| Pi/模型失败 | 认证、限流、响应中断 | 保存失败状态，允许同阶段重试 |
| 平台校验失败 | `ValidationResult.valid=false` | 展示 issues，不允许激活 |
| 平台调用未知结果 | 网络超时 | 支持幂等号的流程操作用同 operation ID 重试；附件模板失败则重新执行流程落地 |
| 文件冲突 | 目标哈希变化 | 拒绝写入，要求重新预览 |
| 写入失败 | 权限、磁盘空间、部分写入 | 回滚并保留备份 |

## 15. 可观测性

SQLite 不建设可查询审计中心。可观测性采用结构化应用日志和 Pi Session：

- 每条日志包含 `requestId`、`sessionId`、`generationId`、`platformDefinitionId`、`operationId`。
- 记录状态迁移起止、外部调用耗时、Pi run 起止、文件数量和错误码。
- 不记录 API Key、完整用户输入、完整代码或附件内容。
- Pi 自身消息与工具调用保留在 JSONL Session。
- 提供：
  - `GET /health/live`
  - `GET /health/ready`
- readiness 检查 SQLite、数据目录、目标模型配置和平台基础连通性。

不提供 Web 日志查询接口。

## 16. 测试设计

“不做 Agent 自动测试生成代码”不等于不测试 Agent Web 自身。Agent Web 必须包含单元、集成和端到端测试；只是不能让 Agent 自动执行并修复它生成的③代码。

### 16.1 单元测试

- `BusinessRequirement` Schema 与完整性规则。
- 平台 DTO 映射和枚举映射。
- 状态机合法/非法迁移。
- 三阶段确认 owner、revision 和状态校验。
- operation ID 持久化及重试复用。
- 目标路径规范化、Windows 大小写、`..`、绝对路径、UNC、符号链接/reparse point。
- Artifact 文件集合与哈希校验。
- 禁止生成实体、Mapper、数据库迁移、平台直调和按业务重复包装审批动作的规则。
- 平台错误和 Pi 错误翻译。

### 16.2 集成测试

- SQLite 迁移、事务、WAL 和乐观锁。
- Pi Adapter 使用 fake model 和 fake tools 验证事件转换、Session 创建与恢复。
- 平台客户端使用 Mock HTTP Server 验证：
  - 创建、保存、校验、发布、激活顺序。
  - 附件模板复用与新版本创建。
  - 流程定义修改请求超时后使用相同 operation ID。
  - 附件模板调用失败后流程落地失败，重试时重新执行查询和复用逻辑。
  - 发布成功、激活失败后的恢复。
- 暂存文件编辑、diff、备份、写入和故障注入回滚。
- SSE 连接、状态快照和断线重连。

### 16.3 API 测试

- 会话按 ID访问和用户隔离。
- 非 owner 对所有子资源返回 `403`。
- 过期 `If-Match` 返回 `409`。
- 三个确认接口重复调用幂等。
- 未通过平台校验时流程确认返回 `422`。
- 未确认代码不能写工作区。
- 两类清单分页、过滤、owner 隔离和返回字段。
- 不存在通用 GET 会话列表。

### 16.4 入金申请端到端验收

1. 选择 `user_sales` 创建 Agent 会话。
2. 输入“我要做一个入金申请流程”。
3. Agent 对角色、字段、材料和规则逐项追问。
4. 输出包含申请人姓名、入金金额、入金账号、银行回单、经理审批和财务确认的结构化需求。
5. 人工编辑并完成门禁一。
6. Agent 后端创建/复用银行回单附件模板，创建流程草稿、保存图并完成发布前校验。
7. 前端展示流程预览，人工完成门禁二，平台发布并激活。
8. 启动代码生成，生成只包含业务发起、业务详情等特定能力的 Spring Boot 业务适配层和 Vue3 入金申请页面。
9. 验证没有实体、Mapper、业务表、通用待办页面或 `/api/entry-application/{taskId}/approve` 一类重复审批接口。
10. 人工查看 diff、编辑代码并完成门禁三。
11. 文件写入③基础底座工作区，不创建 Git commit。
12. “Agent 创建的流程定义”清单可查询该流程。
13. “Agent 生成的业务代码”清单可查询该生成记录。
14. 在 Agent 外部执行③工程编译与完整发起→经理审批→财务确认→办结验收。

## 17. 实施顺序

### M0：契约与骨架

- 建立 Agent Web 前后端工程。
- 固定 Pi Adapter 边界和依赖版本。
- 定义领域类型、状态机、SQLite 三张表和 TargetProjectManifest。
- 提供 Mock 用户、健康检查和可选目标工作区绝对路径规范化；manifest 强校验延后至 M3。

### M1：对话与需求确认

- 接入 Requirement AgentSession。
- 实现受限需求工具、SSE、需求预览、编辑和门禁一。
- 实现 Pi Session JSONL 保存与按已知会话 ID恢复。

### M2：流程平台落地

- 对接并测试 `ProcessAttachmentTemplateController` 已提供的附件模板创建、条件查询和详情 API。
- 实现平台 REST Client、DTO 映射、幂等步骤和流程预览。
- 实现门禁二、发布激活和平台失败恢复。

### M3：代码生成与评审

- 实现 Code Generation AgentSession 和受限暂存工具。
- 实现 manifest、代码树、Monaco 编辑和 diff。
- 实现静态规则校验和重新生成。

### M4：安全写入与管理清单

- 实现门禁三、文件冲突检测、备份、写入和回滚。
- 实现且只实现两类管理清单。
- 完成入金申请 Agent 主线验收。

### M5：外部整体验收

- 在 Agent 外部编译生成的 Spring Boot + Vue3 工程。
- 启动③基础底座和①流程平台。
- 跑通入金申请完整业务流转。
- 记录人工修正点，作为后续 prompt 和契约优化输入；本期不实现自动修复。

## 18. 前置条件和风险

### 18.1 前置条件

- ①平台独立服务可启动，且当前流程定义 REST API 可用。
- ①平台启用 `ProcessAttachmentTemplateController`，可通过 REST 创建和查询附件模板版本。
- M3 启动代码生成前，③基础底座提供 `.flowmind/target-manifest.json`、通用业务后端审批动作能力和稳定的业务扩展点。
- 服务端配置可用的 Pi 模型和 Provider 凭据。
- M3 启动代码生成前，目标工作区位于 `AGENT_ALLOWED_TARGET_ROOTS` 白名单下；M0-M2 验收不依赖目标工程。

### 18.2 主要风险与应对

| 风险 | 应对 |
| --- | --- |
| Pi `0.x` API 变化 | 精确锁版本、Pi Adapter 隔离、升级契约测试 |
| LLM 需求解析错误 | 完整性规则 + 门禁一 + 可编辑结构化结果 |
| LLM 生成错误代码 | 强上下文 + 静态禁用规则 + 门禁三；外部人工编译验收 |
| 模型绕过门禁 | 模型没有平台和目标工作区修改工具 |
| Pi 无内置沙箱 | 禁用 built-in tools，受限自定义工具和专用 cwd |
| 平台多调用部分成功 | 流程操作使用稳定 operation ID 并记录 Saga 步骤状态；附件模板失败时重新执行流程落地 |
| 目标文件被外部修改 | base SHA-256 再校验，冲突时拒绝覆盖 |
| 多文件部分写入 | 全量备份、写入日志和逆序回滚 |
| SQLite 并发能力有限 | 明确单实例、会话 mutex、WAL 和乐观锁 |
| 基础底座契约漂移 | versioned manifest，不支持版本直接拒绝生成 |

## 19. 最终边界总结

1. Pi 是 Agent Runtime，不是 Web 后端，也不是业务状态机。
2. NestJS 是② Agent 的可信编排边界，控制确认、平台修改和文件写入。
3. Pi 源码不进入本项目，SDK 以精确版本依赖。
4. 模型只生成候选结构和暂存代码，不能激活流程或写目标工作区。
5. Agent 生成③业务模块，不生成③通用基础底座。
6. Agent 自身 SQLite 只保存恢复工作流和两类清单所需的最小元数据。
7. 对话上下文由 Pi JSONL 保存，代码正文由文件系统保存，两者不重复进入 SQLite。
8. 管理查询严格限定为 Agent 创建的流程定义和 Agent 生成的业务代码。
9. 生成代码最终只写工作区，不创建 Git 分支、commit 或 push。
10. 编译、运行、测试和自动修复生成代码不属于本期 Agent 能力。
