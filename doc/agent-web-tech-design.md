# ② Agent Web 应用技术设计

## 1. 文档说明

### 1.1 目标

本文档定义 `flow-mind` 第二块产物——② Agent Web 应用的技术方案、模块边界、状态机、数据模型、接口、Pi Agent 集成方式、代码生成质量闭环、安全约束和验收方案。

Agent 面向业务人员，在开发期完成以下主线：

1. 通过多轮对话采集业务办理需求。
2. 将自然语言沉淀为可编辑、可确认的结构化需求。
3. 在用户确认后调用①流程平台创建、校验、发布并激活流程定义。
4. 基于已确认的用户自定义流程和表单需求，生成对应的 Spring Boot 业务发起适配代码和 Vue3 录入页面。
5. 自动完成静态边界检查、编译、单元/组件测试、独立 Agent 审核和最多三轮修复。
6. 在用户预览、编辑并确认代码后，将文件安全写入③基础底座工作区。

Agent 是开发期生产工具，不参与业务运行期。Agent 生成模块的运行时职责到调用 `ProcessRuntimeService.startAndSubmit()` 成功返回为止：创建流程实例、完成发起人 `apply` 节点并推进至下一用户节点。此后的待办、审批详情、审批动作、流程轨迹和完整流转全部由③基础底座与①流程平台承担。

### 1.2 设计依据

- `doc/rebuild-functional-requirements-optimized.md`
- `doc/流程平台设计与接口文档_v4.md`
- 当前仓库中 `platform-core` 的 Controller、Service、DTO、枚举和校验实现
- [Pi SDK 官方文档](https://pi.dev/docs/latest/sdk)
- [Pi Session 文档](https://pi.dev/docs/latest/sessions)
- [Pi Session 文件格式](https://pi.dev/docs/latest/session-format)
- [Pi 上下文压缩文档](https://pi.dev/docs/latest/compaction)
- [Pi 安全文档](https://pi.dev/docs/latest/security)

### 1.3 导师评审后的设计调整

需求文档仍保留“Agent 不自动测试和自动修复生成代码”的早期边界。本设计根据导师对 Agent 技术工作量和生成质量保障的评审意见，增加编译、单元/组件测试、独立审核和限次修复闭环。

本次只修改 Agent Web 技术设计，不反向修改需求文档。实现② Agent Web 时，如需求文档与本文在“生成代码自动验证和修复”上冲突，以本文为准；①流程平台和③基础底座的产品范围仍以需求文档为准。

### 1.4 冻结结论

| 项目 | 结论 |
| --- | --- |
| Agent 前端 | Vue3 + TypeScript + Vite + Pinia + Element Plus + Monaco Editor |
| Agent 后端 | NestJS + TypeScript |
| Node.js | `>= 22.19.0` |
| Pi 集成 | `@earendil-works/pi-coding-agent@0.82.1`，使用 `AgentSession` |
| Pi 部署 | 与 NestJS 同进程，不复制 Pi 源码，不启动 RPC 子进程 |
| Agent 数据库 | SQLite，单机单实例 |
| 身份 | 本地 Mock 用户选择器，不实现正式登录 |
| 平台定义调用 | NestJS 通过①流程平台 REST API 创建流程定义 |
| 生成代码运行时调用 | 通过 `platform-starter` 同进程调用 `ProcessRuntimeService.startAndSubmit()` |
| 生成范围 | 用户已确认流程对应的业务发起页面、业务发起后端及其测试 |
| 后续流程 | 完全由③基础底座提供，不由 Agent 生成 |
| 目标工程契约 | `.flowmind/generation-target.json` |
| 自动质量闭环 | 静态检查、编译、单元/组件测试、独立审核、最多三轮修复 |
| 人工门禁 | 结构化需求确认、流程确认、代码确认 |
| 代码落地 | 只写指定工作区，不创建 Git 分支、提交或远端推送 |

Pi 仍是 `0.x` 版本，依赖必须精确锁定为 `0.82.1` 并提交 lockfile，不使用范围版本自动升级。

## 2. 范围和职责边界

### 2.1 本期包含

- 会话创建、按 ID 恢复、多轮对话和 SSE 流式输出。
- 信息完整性判断、主动追问、结构化需求提交、人工编辑和确认。
- 流程草稿创建、流程图保存、附件模板处理、发布前校验、发布和激活。
- 用户已确认流程对应的前后端业务发起代码及测试代码的 LLM 直接生成。
- 目标工程契约校验、文件暂存、代码树、内容和 diff 预览。
- 静态边界检查、编译、JUnit/Vitest 测试和 Vue 构建。
- 独立 Reviewer Agent 审核及 Generator 最多三轮自动修复。
- 人工质量覆盖、代码确认、安全写入、备份和失败恢复。
- 查询“由 Agent 创建的流程定义”和“由 Agent 生成的业务代码”两类清单。

### 2.2 本期不包含

- 正式登录、统一认证和 RBAC。
- Redis、消息队列、分布式锁、集群或多实例调度。
- Git 分支、commit、push 或 Pull Request。
- 通用流程设计器。
- Agent 自动启动①流程平台或③基础底座。
- Agent 自动执行真实流程集成测试或完整审批 E2E。
- 待办、已办、我发起、已阅、审批详情、流程轨迹和审批动作代码生成。
- `apply` 后下一用户节点及其后续流程编排代码生成。
- 通用会话历史列表、审计中心或统计报表。
- ③基础底座本身的实现。
- 生产级容器编排或面向不可信项目的执行沙箱。

### 2.3 Pi、Agent Web 与③基础底座职责

| 能力 | Pi SDK | Agent Web | ③基础底座 |
| --- | --- | --- | --- |
| LLM 调用、流式响应、agent loop | 负责 | 配置并映射事件 | 不负责 |
| 底层 function calling | 负责 | 定义少量受限工具 | 不负责 |
| Session JSONL | 负责 | 固定目录并保存定位信息 | 不负责 |
| 上下文压缩执行 | 提供钩子和持久化 | 定义 Flow Mind 摘要策略 | 不负责 |
| 需求完整性和人工门禁 | 不负责 | 负责 | 不负责 |
| 流程定义创建和激活 | 不直接执行 | 通过①REST确定性调用 | 不负责 |
| 用户自定义流程代码生成 | 推理和工具调用 | 编排、暂存和约束 | 提供目标契约 |
| 编译、测试、审核、修复 | 不直接执行 | 负责受控质量闭环 | 提供可编译目标工程 |
| 实例发起并完成 apply | 不负责 | 生成调用代码 | 运行生成代码并调用 Starter |
| 后续审批流转 | 不负责 | 不生成、不参与 | 负责 |

不建设通用 Function Calling 控制平面，不复制 Pi 的工具循环，也不新增通用工具调用审计表。所有业务边界由 NestJS 工作流、工具自身校验、目标契约和人工门禁共同保证。

## 3. 总体架构

```text
┌──────────────────── Vue3 Agent Web ────────────────────┐
│ 对话 │ 需求预览 │ 流程预览 │ 代码 Diff │ 质量报告 │ 清单 │
└──────────────────────────┬─────────────────────────────┘
                           │ REST + SSE
┌──────────────────────────▼─────────────────────────────┐
│ NestJS Agent Server                                    │
│ API / Identity / Workflow State Machine                │
│ Requirement / Process Orchestrator                     │
│ Pi Adapter + FlowMind Compaction Extension             │
│ Generation Orchestrator                                │
│ Static Validator / Verification Worker / Reviewer      │
│ Repair Coordinator / Staging / Safe Writer             │
└───────────────┬────────────────┬───────────────────────┘
                │                │
        ┌───────▼──────┐  ┌──────▼─────────────────────┐
        │ ①流程平台REST │  │ 一次性验证工作区             │
        └──────────────┘  │ 目标副本 + 暂存 overlay       │
                          │ Maven / npm / JUnit / Vitest │
                          └──────────────┬───────────────┘
                                         │ 人工确认后写入
                          ┌──────────────▼───────────────┐
                          │ ③基础底座工作区                │
                          │ 生成模块 → platform-starter   │
                          │ 后续流程由③通用能力承担        │
                          └──────────────────────────────┘
```

### 3.1 部署和数据目录

```text
agent-web/
├── frontend/
├── backend/
├── shared/
└── data/
    ├── agent.db
    ├── pi-sessions/
    ├── staging/{sessionId}/{generationId}/
    ├── verification/{generationId}/{runId}/
    ├── verification-logs/{generationId}/
    └── backups/{generationId}/{writeAttemptId}/
```

验证工作区是目标工程的一次性副本。编译、测试和审核期间不得写真实目标工作区；只有门禁三确认后，Safe Writer 才能写入。

## 4. 模块设计

### 4.1 后端模块

| 模块 | 职责 |
| --- | --- |
| `IdentityModule` | 校验 Mock 用户并生成可信 Agent 用户上下文 |
| `SessionModule` | 会话所有权、状态、乐观锁和恢复 |
| `PiModule` | 创建 AgentSession、注册受限工具、压缩扩展、事件映射 |
| `RequirementModule` | Schema、完整性、版本和门禁一 |
| `ProcessModule` | 平台 REST 映射、Saga、预览、发布激活和门禁二 |
| `GenerationModule` | 目标契约、生成任务、暂存工具和 ArtifactManifest |
| `ValidationModule` | 静态范围规则、路径规则和编译诊断归一化 |
| `VerificationModule` | 一次性工作区、受控命令、测试和日志 |
| `ReviewModule` | 独立只读 Reviewer Session 和结构化报告 |
| `RepairModule` | 诊断反馈、最多三轮修复和终止判定 |
| `ArtifactModule` | diff、人工编辑、质量覆盖、备份、写入和回滚 |
| `QueryModule` | 两类管理清单 |

### 4.2 前端页面

- 工作台：对话、结构化需求、流程预览、代码树、Monaco 和 diff。
- 质量面板：流水线阶段、修复轮次、命令状态、诊断、Reviewer 问题和风险覆盖。
- Agent 创建的流程定义清单。
- Agent 生成的业务代码清单。

质量面板不得只展示“成功/失败”，必须显示失败阶段、文件位置、摘要、是否属于硬门禁、修复轮次及人工覆盖记录。

## 5. 工作流状态机与门禁

### 5.1 状态

| 状态 | 含义 | 允许动作 |
| --- | --- | --- |
| `COLLECTING` | 对话采集需求 | 发消息 |
| `REQUIREMENT_REVIEW` | 结构化需求待确认 | 编辑、确认、退回 |
| `PROCESS_CREATING` | 创建流程草稿和校验 | 查看进度 |
| `PROCESS_REVIEW` | 流程预览待确认 | 确认、重试 |
| `PROCESS_PUBLISHING` | 发布和激活 | 查看进度 |
| `PROCESS_ACTIVE` | 流程已激活 | 启动生成 |
| `CODE_GENERATING` | 生成业务代码和测试 | 查看事件、取消 |
| `CODE_VERIFYING` | 静态检查、编译、测试、构建 | 查看质量进度 |
| `CODE_REVIEWING` | 独立 Reviewer 审核 | 查看审核进度 |
| `CODE_REPAIRING` | Generator 修复 | 查看轮次、取消 |
| `CODE_REVIEW` | 候选代码待人工确认 | 查看、编辑、重验、覆盖、写入 |
| `CODE_PIPELINE_FAILED` | 硬门禁或基础设施失败 | 编辑、重验、放弃 |
| `WRITING_ARTIFACTS` | 写入真实工作区 | 查看进度 |
| `ARTIFACT_WRITE_FAILED` | 写入或回滚异常 | 查看恢复、受控重试 |
| `COMPLETED` | 代码已写入 | 查询结果 |

### 5.2 代码质量子流程

```text
CODE_GENERATING
      │ report_generation_complete
      ▼
CODE_VERIFYING ──基础设施失败──► CODE_PIPELINE_FAILED
      │
      ├─硬编译失败──────────────► CODE_REPAIRING
      ├─测试失败────────────────► CODE_REVIEWING
      └─通过────────────────────► CODE_REVIEWING
                                      │
                                      ├─需修复且轮次<3 ─► CODE_REPAIRING
                                      │                      │
                                      │                      └─► CODE_VERIFYING
                                      └─通过/轮次耗尽 ─────► CODE_REVIEW
```

硬编译失败在三轮耗尽后进入 `CODE_PIPELINE_FAILED`，禁止写入。测试断言或 Reviewer 失败在三轮耗尽后可进入 `CODE_REVIEW`，但必须人工覆盖才能写入。

### 5.3 三阶段人工门禁

1. 门禁一：确认结构化需求后，后端才能创建流程草稿。
2. 门禁二：确认流程预览后，后端才能发布和激活流程。
3. 门禁三：确认代码集合、文件哈希和质量状态后，后端才能写真实工作区。

门禁三附加规则：

- Java 生产/测试代码编译失败、前端 typecheck 或 build 失败时绝对禁止写入。
- JUnit/Vitest 断言失败或 Reviewer 未通过时，owner 可填写不少于 10 个字符的原因进行风险覆盖。
- 覆盖只针对当前 `generationRevision`；任何文件编辑都会使覆盖失效并要求重新验证。

## 6. Pi Agent 集成与上下文压缩

### 6.1 集成方式

所有 Pi API 调用集中在 `PiAdapterService`。使用：

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

要求：

- 不复制或修改 Pi 源码。
- 不通过前端直接调用 LLM。
- 不启动 `pi --mode rpc` 子进程。
- `noTools: "builtin"`，只注册本项目受限工具。
- 不建设独立 Function Calling 控制平面。

### 6.2 Session

- Requirement Agent 使用持久化 Session。
- 每个代码生成任务使用独立 Generator Session。
- 每个审核任务使用独立 Reviewer Session，不继承 Generator 对话。
- Session 固定保存到 `data/pi-sessions`。
- SQLite 保存 Session ID、JSONL 路径和用途，不保存消息正文。
- Pi JSONL 是对话、工具调用、压缩条目和模型轨迹的权威来源。

### 6.3 Flow Mind 自定义压缩

通过受控 inline extension factory 注册 `session_before_compact`，不从目标工程发现 `.pi/extensions`。

默认策略：

- `reserveTokens = min(16384, max(8192, contextWindow × 20%))`。
- `keepRecentTokens = min(12000, max(4096, contextWindow × 15%))`。
- 超过 `contextWindow - reserveTokens` 时压缩。
- 可用 `PI_COMPACTION_MODEL` 指定摘要模型；缺省复用 `PI_MODEL`。
- 摘要最大 4096 tokens。

摘要必须包含：

1. 当前任务和当前工作流状态。
2. 已确认需求版本、摘要哈希和关键字段。
3. 平台流程定义 ID、编码、版本和激活状态。
4. 已生成/修改文件及 generation revision。
5. Java 8、Spring Boot 2.7、Vue3、Starter 和生成范围约束。
6. 最新编译、测试、审核和修复结果。
7. 未解决问题、人工决定和下一步。

摘要为空、超限或缺少必需章节时回退 Pi 默认压缩，不丢弃原始 Session。`agent_compaction_stat` 只保存条目 ID、原因、压缩前 token、摘要 token、耗时、状态和错误码。

### 6.4 受限工具

Requirement Agent：

- `submit_requirement_snapshot`

Generator/Repair Agent：

- `read_generation_contract_file`
- `read_staged_file`
- `list_staged_files`
- `write_staged_file`
- `delete_staged_file`
- `read_quality_report`
- `report_generation_complete`

Reviewer Agent：

- `read_staged_file`
- `read_staged_diff`
- `read_quality_report`
- `submit_code_review`

Reviewer 没有写文件工具；所有 Agent 都没有 Shell、Git、平台修改和真实目标工作区写入工具。

### 6.5 SSE 映射

除 Pi 消息和工具事件外，增加：

- `context.compacted`
- `generation.stage_changed`
- `verification.started`
- `verification.diagnostic`
- `verification.completed`
- `review.completed`
- `repair.round_started`
- `repair.round_completed`
- `workflow.state_changed`
- `error`

SSE 不承担历史恢复。重连时先发送 `workflow.snapshot` 和当前质量摘要，再发送实时事件。

## 7. 核心领域契约

### 7.1 BusinessRequirement

结构化需求至少包含：

- `businessName`、`businessCode`、`processCode`、`processName`。
- 业务目标和参与角色。
- 表单字段、控件、必填、校验和流程变量编码。
- 用户定义的附件字段、类型、必填规则和附件模板；流程不需要附件时允许为空。
- 开始、`apply`、用户定义的后续审批/处理节点和结束节点。
- 连线、审批人规则、条件和多人模式。

`apply` 必须是 `STARTER` 规则的首个用户任务，且只有一条出边指向下一用户节点，保证 `startAndSubmit()` 可以确定性完成发起。

### 7.2 GenerationTargetContract

③基础底座根目录必须提供 `.flowmind/generation-target.json`：

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
    "generatedTestDir": "src/test/java/com/flowmind/business/generated",
    "starter": {
      "groupId": "com.flowmind",
      "artifactId": "platform-starter",
      "version": "0.1.0-SNAPSHOT",
      "allowedApi": "ProcessRuntimeService#startAndSubmit(StartProcessRequest)"
    },
    "trustedUserContext": {
      "accessorType": "com.flowmind.business.security.CurrentBusinessUserProvider",
      "accessorMethod": "currentUser",
      "userIdProperty": "userId",
      "departmentIdProperty": "departmentId"
    },
    "verificationProfile": "maven-java8"
  },
  "frontend": {
    "rootDir": "frontend",
    "framework": "vue3",
    "generatedViewDir": "src/modules/generated",
    "generatedApiDir": "src/api/generated",
    "generatedTestDir": "src/modules/generated/__tests__",
    "routeRegistry": "src/router/generated-routes.ts",
    "verificationProfile": "vue3-npm"
  },
  "readableReferenceFiles": [
    "backend/pom.xml",
    "frontend/package.json",
    "frontend/src/router/generated-routes.ts"
  ],
  "allowedOutputPatterns": [
    "backend/src/main/java/com/flowmind/business/generated/**/*.java",
    "backend/src/test/java/com/flowmind/business/generated/**/*.java",
    "frontend/src/modules/generated/**/*",
    "frontend/src/api/generated/**/*",
    "frontend/src/router/generated-routes.ts"
  ],
  "protectedFiles": [
    { "path": "backend/pom.xml", "sha256": "..." },
    { "path": "frontend/package.json", "sha256": "..." }
  ]
}
```

该契约只解决生成代码集成问题：目录、包名、Starter API、可信用户、路由、可读文件、输出白名单和验证 profile。它不声明③提供的待办或审批能力，不参与流程运行，也不形成后续审批业务耦合。

`contractVersion` 不支持、构建文件哈希变化、Starter API 不匹配、可信用户接口缺失或输出路径越界时禁止生成。

### 7.3 ArtifactManifest

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
  validationStatus: "PENDING" | "VALID" | "INVALID";
  editedByUser: boolean;
}
```

本期不允许 Agent 删除真实目标文件。

### 7.4 质量报告

```ts
interface GenerationQualityReport {
  generationId: string;
  generationRevision: number;
  pipelineState: "VERIFYING" | "REVIEWING" | "REPAIRING" | "PASSED" | "FAILED";
  repairRound: number;
  maxRepairRounds: 3;
  staticValidation: QualityStageResult;
  backendCompile: QualityStageResult;
  backendTests: QualityStageResult;
  frontendTypecheck: QualityStageResult;
  frontendTests: QualityStageResult;
  frontendBuild: QualityStageResult;
  review: CodeReviewReport | null;
  hardGatePassed: boolean;
  overrideRequired: boolean;
  canWrite: boolean;
}
```

Reviewer 问题包含严重级别、类别、文件、行号、证据和修复建议。完整命令日志保存到文件系统，报告只保存摘要和日志路径。

## 8. SQLite 设计

### 8.1 原则

- SQLite 使用 WAL、busy timeout 和外键。
- 消息正文、代码正文和完整命令日志不进入 SQLite。
- 所有状态修改使用事务和 `row_version`。

### 8.2 主要表

`agent_session`：owner、target root、工作流状态、需求版本、Requirement Session、row version。

`agent_process_definition`：平台定义 ID、编码、版本、Saga 步骤、校验结果、发布激活状态和幂等号。

`agent_code_generation`：

- generation ID、session ID、需求和流程快照。
- Generator/Reviewer Pi Session ID 和文件路径。
- generation revision、pipeline state、repair round。
- ArtifactManifest、GenerationTargetContract 版本。
- 最新 verification run、review ID。
- hard gate、override required、override ID、can write。
- 写入幂等、状态、错误和时间。

`agent_verification_run`：run ID、generation revision、轮次、阶段、状态、开始/结束、退出码、诊断 JSON、日志目录和错误码。

`agent_code_review`：review ID、generation revision、Reviewer Session、结构化报告、状态和时间。

`agent_quality_override`：owner、generation revision、覆盖测试/审核项、原因和时间。

`agent_compaction_stat`：Pi Session、条目 ID、触发原因、token、耗时、状态和错误码。

不新增通用工具调用明细表。

## 9. Agent Web API

### 9.1 通用约定

- 所有请求携带可信 Mock 用户头。
- 修改接口使用 `If-Match` 或 generation revision。
- 确认、重试、覆盖和写入接口要求 `Idempotency-Key`。
- 错误体包含 `code`、`message`、`sessionId`、`generationId` 和可选 `details`。

### 9.2 工作流接口

保留会话、消息、SSE、需求、流程预览、流程确认、代码启动、文件读取/编辑、diff、重新生成和确认写入接口。

新增：

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/api/agent/sessions/{sessionId}/code-generations/{generationId}/quality-report` | 查询当前质量报告 |
| POST | `/api/agent/sessions/{sessionId}/code-generations/{generationId}/reverify` | 人工编辑后重新全量验证 |
| POST | `/api/agent/sessions/{sessionId}/code-generations/{generationId}/quality-override` | 覆盖测试或审核软失败 |

覆盖请求：

```json
{
  "generationRevision": 4,
  "reason": "测试环境中的前端计时器存在已知差异，已人工核查提交逻辑"
}
```

覆盖接口不得覆盖编译、typecheck、build、路径、文件哈希或安全规则失败。

### 9.3 两类管理清单

只提供：

- `GET /api/agent/created-process-definitions`
- `GET /api/agent/generated-code`

均按当前 owner 隔离。不提供通用会话列表、LLM 历史列表或审计中心。

## 10. ①流程平台集成

### 10.1 开发期定义创建

NestJS 通过①平台 REST API 完成附件模板处理、定义草稿创建、流程图保存、发布前校验、发布和激活。模型没有平台修改工具，所有调用由确定性 Saga 执行。

### 10.2 生成代码运行期发起

生成的 Spring Boot Service 只能注入③提供的可信用户访问接口和 Starter 的 `ProcessRuntimeService`，构造 `StartProcessRequest` 后调用：

```java
ProcessInstanceDTO result = processRuntimeService.startAndSubmit(request);
```

当前平台实现会在同一运行时事务中创建实例、写入变量和附件、完成 `STARTER` 类型的 `apply` 任务，并推进到唯一下一节点。

生成代码不能调用 `startProcess()` 后自行查询和提交任务，也不能调用 `submitTask()`、`approve()`、`reject()` 等后续动作。

### 10.3 身份和幂等

- `starterUserId`、`starterDeptId` 必须来自③可信用户上下文。
- 前端请求不得携带可生效的发起人身份字段。
- 生成接口要求 `Idempotency-Key`。
- `operationId` 使用业务编码、用户 ID 和幂等号形成稳定摘要；同一请求重试必须复用。
- 平台仍会通过自身 `CurrentUserProvider` 校验发起人身份一致性。

## 11. 用户自定义流程代码生成规范

### 11.1 生成依据与命名规则

- 代码生成只能使用用户已确认的结构化需求、已确认的流程快照和 `GenerationTargetContract`，不得把示例流程、历史会话中的未确认内容或“入金申请”默认字段带入结果。
- 每次生成绑定明确的 `requirementRevision`、`processCode` 和流程定义版本；任一输入发生变化后，旧的生成结果和质量报告失效，必须重新生成或修复并全量验证。
- 后端类名、前端模块名、路由和接口路径必须由 `businessCode` 派生，并经过合法标识符、保留字、大小写和路径冲突校验。显示名称使用 `businessName`，不得用显示名称直接拼接文件路径。
- 建议统一派生规则：`businessCode` 转 PascalCase 作为 Java 类名前缀，转 kebab-case 作为前端模块名和接口路径；实际规则以目标工程契约为准。
- 生成代码中的 `processCode` 必须固定为本次用户确认的流程编码，不能由前端传入或在运行时任意切换。
- 表单字段、校验、流程变量、附件和控件必须逐项来自确认需求；未定义附件时不得生成虚构的附件字段或上传控件。

### 11.2 必须生成

后端：

- 以派生业务类名前缀命名的 `{Business}Controller` 和 `{Business}Service`。
- 与用户定义表单匹配的提交请求 DTO、提交响应 DTO 和必要的嵌套 DTO。
- 字段类型、必填、长度、格式、枚举和跨字段规则等服务端校验。
- 表单字段到流程变量的映射，以及用户定义附件到 `attachments` 的映射。
- 以派生业务路径命名的 `POST /api/generated/{business-code}/submit` 发起接口。
- 固定流程编码、可信发起人上下文、幂等键和 `startAndSubmit()` 调用适配。
- Service 和 Controller 单元测试。

前端：

- 与 `businessName` 对应的业务发起录入页面。
- 用户定义的全部表单字段、控件、校验、选项和附件控件。
- 业务 API 客户端。
- 路由注册项。
- 页面和 API 组件测试。

响应至少返回实例 ID、实例状态和 `createdTasks` 中的下一节点任务摘要。页面只展示提交成功和下一处理节点提示，不实现审批详情。不同用户流程可以生成不同字段、类名、页面和接口路径，但运行时边界均止于发起并完成 `apply`。

### 11.3 禁止生成

- 审批通过、驳回、退回、撤回、转办、委托、加签、直送接口。
- 待办、已办、我发起、已阅、审批详情和流程轨迹。
- 业务详情聚合接口。
- 下一用户节点后的流程编排或监听逻辑。
- 实体、Mapper、Repository、DAO、业务表和数据库迁移。
- 前端对①平台的直接调用。
- 后端对①平台 REST API 的直接调用。
- 对通用审批能力的业务命名包装。
- Java 9+、Spring Boot 3 或 Jakarta 代码。
- 需求中未定义的业务字段、附件、审批角色、流程条件或默认业务规则。
- 允许客户端覆盖 `processCode`、发起人或发起部门的通用动态发起接口。

### 11.4 静态边界规则

- 生产代码只允许一次语义上的 `startAndSubmit()` 发起路径。
- 禁止调用其他 `ProcessRuntimeService` 动作方法。
- 禁止 `RestTemplate`、`WebClient`、HTTP 客户端指向平台路径。
- 禁止持久化框架注解、Mapper/Repository 命名和迁移目录。
- 生成字段编码必须与确认需求和流程变量一致。
- 生成文件名、类名、路由和接口路径必须与 `businessCode` 的派生结果一致且不存在冲突。
- 后端使用的 `processCode` 必须与生成任务绑定值一致，禁止从请求 DTO 读取。
- 附件映射必须与确认需求一致：有定义则完整映射，无定义则不得虚构。
- 测试不得通过删除断言、全局 skip、空测试或 mock 被测 Service 来伪造通过。

## 12. 编译、测试、审核和修复

### 12.1 一次性验证工作区

每个 verification run：

1. 校验目标契约和受保护文件哈希。
2. 复制目标工程必要内容到 `data/verification/{generationId}/{runId}`。
3. 将暂存文件 overlay 到副本。
4. 执行固定 profile。
5. 保存日志、诊断和结果。
6. run 完成后按保留策略清理副本。

不在真实目标目录运行任何编译或测试命令。

### 12.2 受控 Worker

- 使用 Node `spawn` 参数数组且 `shell=false`。
- executable 由服务端 profile 决定，不接受模型或 manifest 自定义命令。
- 固定 cwd，删除无关环境变量，不传递 LLM 凭据。
- 单命令默认超时 5 分钟，输出上限 10 MiB。
- 超时或取消后终止进程树。
- 同一 generation 只允许一个 run。

这是可信本地开发执行边界，不是安全沙箱。目标工程、依赖和测试代码必须被视为可信；未来处理不可信仓库时必须改为容器或系统级隔离。

### 12.3 固定验证 profile

`maven-java8`：

1. `mvn -B -ntp -DskipTests test`：编译生产和测试代码，硬门禁。
2. `mvn -B -ntp test`：运行 JUnit，软门禁。

`vue3-npm`：

1. `npm run typecheck`：硬门禁。
2. `npm run test -- --run`：运行组件测试，软门禁。
3. `npm run build`：硬门禁。

依赖安装是目标工程准备工作，不由模型决定，也不在每轮修复中修改 lockfile。

### 12.4 单元和组件测试验收点

JUnit 必须 Mock `ProcessRuntimeService` 和可信用户接口，并断言：

- 只调用一次 `startAndSubmit()`。
- `processCode` 来自确认需求。
- 发起人和部门来自可信用户。
- 表单字段进入 `variables`。
- 用户定义了附件时，所有附件按确认的字段编码进入 `attachments`；未定义附件时不构造虚假附件。
- 相同幂等号产生相同 operation ID。
- 响应正确映射实例和下一任务。

前端测试必须 Mock 生成 API，并断言字段渲染、必填校验、附件载荷、幂等头和成功态。

### 12.5 独立 Reviewer

Reviewer 使用新 Session，只注入：

- 确认需求快照和流程快照。
- GenerationTargetContract。
- 生成 diff。
- 静态检查、编译和测试报告。

Reviewer 输出结构化问题，审核需求一致性、范围边界、Starter 用法、可信身份、幂等、变量/附件映射、测试有效性和可维护性。Reviewer 没有文件写工具。

### 12.6 最多三轮修复

- 第一次生成后的问题反馈计入 repair round 1。
- Generator 只能修改当前 ArtifactManifest 管理的暂存文件。
- 每次修改增加 generation revision，使旧报告和覆盖失效。
- 每轮后重新运行全部静态、编译、测试和审核步骤。
- Worker 启动失败、工具链缺失或磁盘异常不消耗轮次。
- 三轮后编译硬门禁失败进入 `CODE_PIPELINE_FAILED`。
- 三轮后仅测试或 Reviewer 失败进入 `CODE_REVIEW`，要求人工覆盖。

## 13. 文件暂存和安全写入

### 13.1 路径安全

每次读写必须：

- 拒绝绝对输出路径、UNC、盘符和 `..` 越界。
- 规范化路径后确认仍位于目标根。
- Windows 比较忽略大小写。
- 检查符号链接和 reparse point。
- 匹配 `allowedOutputPatterns`。
- 拒绝 `.git`、`.env`、凭据、Agent 数据目录和非生成区域。

### 13.2 Diff 和人工编辑

- 新文件为 `ADD`，已有文件为 `MODIFY`。
- 记录 `baseSha256` 和 `stagedSha256`。
- 人工编辑增加 generation revision，并清除旧验证、审核和覆盖结论。
- 目标文件哈希变化时标记 `STALE`，禁止直接写入。

### 13.3 确认写入

写入前依次校验 owner、状态、幂等号、generation revision、文件集合、暂存哈希、目标基线、质量硬门禁和必要覆盖记录。

写入采用旧文件备份、同目录临时文件和单文件原子 rename。多文件中途失败时按清单逆序恢复；恢复不完整则进入 `ARTIFACT_WRITE_FAILED`，禁止自动重试。

## 14. 身份、安全和配置

### 14.1 身份

Agent Web 默认只监听 `127.0.0.1`。Mock 请求头可被伪造，因此不得部署到共享网络。用户只能访问自己创建的会话、流程、生成任务和文件。

生成代码的运行期身份不使用 Agent Mock 头，而使用 GenerationTargetContract 指定的③可信用户访问接口。

### 14.2 Pi 安全

- API Key 只在服务端。
- `cwd` 指向受控任务目录。
- 禁用 built-in tools。
- 不加载目标工程扩展、技能或动态资源。
- 不记录 API Key、完整 prompt 或完整代码到应用日志。

### 14.3 主要配置

| 配置 | 说明 |
| --- | --- |
| `AGENT_BIND_HOST` | 默认 `127.0.0.1` |
| `AGENT_PORT` | Agent Web 端口 |
| `AGENT_DATA_DIR` | SQLite、Session、暂存、验证和备份根目录 |
| `AGENT_ALLOWED_TARGET_ROOTS` | 允许的目标工作区根目录 |
| `FLOW_PLATFORM_BASE_URL` | ①平台地址 |
| `PI_MODEL` | 主模型 `provider/model` |
| `PI_COMPACTION_MODEL` | 可选摘要模型 |
| `PI_THINKING_LEVEL` | 默认 `medium` |
| `AGENT_MAX_REPAIR_ROUNDS` | 固定上限 3，配置只能降低 |
| `AGENT_VERIFICATION_TIMEOUT_SECONDS` | 默认 300 |
| `AGENT_VERIFICATION_OUTPUT_LIMIT_BYTES` | 默认 10 MiB |

## 15. 并发、幂等和错误处理

- 单会话命令串行；同一 generation 只允许一个 Pi run、verification run、review 或 write attempt。
- SQLite `row_version` 防止多标签页覆盖。
- 确认、重试、覆盖和写入复用持久化幂等号。
- 平台定义 Saga 对支持幂等的步骤使用稳定 operation ID。
- 生成业务接口的 operation ID 由用户、业务编码和 `Idempotency-Key` 稳定派生。

错误分类：

| 分类 | 行为 |
| --- | --- |
| 需求或流程校验失败 | 停留评审态，展示字段问题 |
| Pi/模型失败 | 保存阶段失败并允许重试 |
| 静态范围违规 | 进入修复；耗尽后硬失败 |
| 编译/typecheck/build 失败 | 硬门禁，不允许覆盖 |
| JUnit/Vitest 失败 | 修复；耗尽后可人工覆盖 |
| Reviewer 未通过 | 修复；耗尽后可人工覆盖 |
| Worker 基础设施失败 | 不计修复轮次，允许重试 |
| 文件冲突 | 拒绝写入，要求重新预览 |
| 部分写入 | 回滚并保留备份 |

## 16. 可观测性

结构化日志包含 `requestId`、`sessionId`、`generationId`、`verificationRunId`、`reviewId`、`repairRound` 和 `operationId`。

记录：

- Pi run 和压缩起止、token 与耗时。
- 工作流状态迁移。
- 生成文件数和 revision。
- 每个验证命令的起止、退出码和诊断数。
- Reviewer 问题数量和严重级别。
- 修复轮次、最终质量状态和人工覆盖。

不提供通用 Web 审计中心；质量详情通过 generation 资源查询。

## 17. 测试设计

### 17.1 Agent Web 单元测试

- 需求 Schema、完整性和 `apply` 首节点规则。
- 状态机、三门禁、修复轮次和质量覆盖。
- GenerationTargetContract Schema、版本、受保护哈希和路径规则。
- 静态禁止规则及 `startAndSubmit()` 唯一调用规则。
- Pi 压缩摘要校验、回退和统计。
- Maven/npm 诊断解析。

### 17.2 集成测试

- Pi fake model 验证 Session、压缩事件和恢复。
- 平台 Mock HTTP 验证定义创建、保存、校验、发布、激活及重试。
- 验证工作区复制和 overlay 不修改真实目标。
- Worker 固定命令、超时、取消、输出截断和进程回收。
- 独立 Reviewer 无法写文件。
- 修复后全量重验，基础设施错误不消耗轮次。
- 备份、写入、故障注入和回滚。

### 17.3 质量闭环场景

1. 正确代码首轮通过。
2. Java 编译错误在三轮内修复。
3. TypeScript 类型错误在三轮内修复。
4. JUnit 断言失败修复；耗尽后允许带原因覆盖。
5. Reviewer 发现生成了审批接口，修复后删除越界代码。
6. 编译失败即使提交覆盖原因仍禁止写入。
7. 人工编辑使旧报告和覆盖失效。

### 17.4 用户自定义流程联合验收

Agent 内部验收终点：

- 选择至少两个字段、附件和后续节点配置不同的用户自定义流程，分别生成对应的前后端代码和测试。
- 编译、JUnit、typecheck、Vitest 和 build 完成。
- Reviewer 确认只使用 `startAndSubmit()` 且没有后续审批能力。
- 人工确认后写入③工作区。

Agent 外部联合验收：

1. 启动①平台和③基础底座。
2. 分别调用各流程根据 `businessCode` 生成的发起接口。
3. 验证实例已按绑定的 `processCode` 创建，用户定义的变量和附件（如有）已正确保存，且不同流程之间不存在字段或编码串用。
4. 验证 `apply` 历史任务已完成，下一用户任务已创建。
5. 使用③通用待办和审批动作按照各自用户定义的后续节点继续处理直至办结。

第5步不属于 Agent 生成代码，也不由 Agent 自动验证。

## 18. 实施顺序

### M0-M2：保持现有主线

- 工程骨架、会话、需求确认、平台定义创建、流程预览、发布和激活。

### M3：代码生成与目标契约

- GenerationTargetContract。
- Generator Session、受限暂存工具、代码树和 diff。
- 用户自定义流程的动态命名、表单/变量/附件映射和测试生成。
- Flow Mind 自定义上下文压缩。

### M4：质量闭环

- 静态范围检查。
- 一次性验证工作区和 Worker。
- Maven/npm 编译、测试、构建和诊断。
- 独立 Reviewer Session。
- 最多三轮修复状态机。

### M5：人工确认和落仓

- 质量面板、人工覆盖、重验。
- 文件冲突、备份、写入和回滚。
- 两类管理清单。

### M6：外部联合验收

- 生成代码写入③。
- 真实调用 `startAndSubmit()`。
- 验证 apply 完成并到达下一用户节点。
- 后续流程使用③基础底座完成。

## 19. 前置条件和风险

### 19.1 前置条件

- ①平台独立服务及流程定义 REST API 可用。
- `platform-starter` 暴露 `ProcessRuntimeService.startAndSubmit()`。
- ③提供 `.flowmind/generation-target.json`、可信用户接口、路由注册点和稳定构建脚本。
- 目标工作区位于允许根目录。
- Maven、Java 8、Node.js 和 npm 可用，目标依赖已准备。
- Pi 模型和 Provider 凭据可用。

### 19.2 风险

| 风险 | 应对 |
| --- | --- |
| Pi `0.x` 变化 | 精确锁版本、Adapter 隔离、压缩扩展契约测试 |
| 摘要丢失关键事实 | 固定摘要结构、校验、失败回退、保留原 JSONL |
| LLM 生成越界能力 | 固定范围 prompt、静态规则、Reviewer 和人工门禁 |
| 自动修复循环或退化 | 最多三轮、每轮全量验证、禁止削弱测试 |
| 测试代码执行风险 | 仅可信本地工程；不宣称沙箱；未来使用容器隔离 |
| ③契约漂移 | 版本化 GenerationTargetContract 和受保护文件哈希 |
| 目标文件外部变化 | base SHA-256 再校验 |
| 多文件部分写入 | 备份、写入清单和逆序回滚 |
| 需求文档边界不一致 | 本文显式记录导师评审调整和实现优先级 |

## 20. 最终边界总结

1. Pi SDK 负责 agent loop、底层 function calling、Session 和事件；项目不建设通用 Function Calling 控制平面。
2. Agent Web 自研工作量集中在业务状态机、上下文压缩策略、代码边界、验证 Worker、独立审核和限次修复。
3. GenerationTargetContract 只描述代码集成、安全落仓和验证，不描述③后续流程能力。
4. Agent 根据用户已确认的流程和表单需求生成对应的业务发起页面、发起后端和测试，不限定具体业务类型。
5. 生成后端只调用 `ProcessRuntimeService.startAndSubmit()`，返回后职责结束。
6. `apply` 后的下一用户节点及后续审批完全由③基础底座承担。
7. Agent 自动运行静态检查、编译、单元/组件测试和 Reviewer 审核，但不启动平台做真实 E2E。
8. 最多自动修复三轮；编译硬失败不可覆盖，测试和审核软失败可由 owner 留痕覆盖。
9. 人工确认前不写真实目标工作区，写入时执行哈希、备份和回滚。
10. 本次设计调整只体现在本文，不修改需求文档。
