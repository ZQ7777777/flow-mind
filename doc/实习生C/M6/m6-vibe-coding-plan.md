# 实习生 C：M6 管理修复、超时治理与告警闭环 Vibe Coding 计划

## 产物与边界

本文件作为实习生 C 在 M6 阶段的专项编码计划。M6-C 基于当前 `develop@2518c43`，在并行汇聚核心推进已经完成的前提下，补齐管理员查询、管理员修复治理、并行任务组诊断、节点级超时策略、自动催办、超时自动跳转/终止、回调失败告警、动作异常告警和跨线回归。

M6-C 的“管理员修复”按 `doc/rebuild-functional-requirements-optimized.md` 理解为一套运维闭环，不只是 `jumpToNode`：

- 管理员能查询流程定义、实例、活动任务、历史任务、审计、回调、提醒和告警。
- 管理员能对异常流程执行任意节点跳转和强制办结。
- 终止后的流程能通过管理员跳转恢复流转。
- 修复动作必须清理受影响活动任务和并行任务组，避免孤立分支。
- 关键管理动作和流程状态变更必须写审计和回调。
- 超时、回调失败和动作异常必须有告警或重试治理。

M6-C 不实现以下内容：

- 不重写 `RuntimeNodeAdvancer` 已完成的并行拆分、并行汇聚和任务组 CAS 逻辑。
- 不实现 A 线条件分支和或签运行逻辑。
- 不实现 B 线会签创建、会签审批和一票驳回运行逻辑。
- 不新增第二套 `ProcessRuntimeService`、`AdminProcessService`、`TaskQueryService` 或 `ProcessMonitorService`。
- 不重做流程定义 CRUD、发布、停用、归档、灰度；流程定义查询复用现有定义服务。
- 不开放嵌套并行网关；当前代码明确拒绝嵌套并行。
- 不接入真实短信、邮件、企业微信或 HTTP 投递；消息和回调继续走 SPI 或 Mock。
- 不实现完整工作日历、节假日、复杂升级链路；只实现 M6 可验收的最小策略 JSON。

每个微任务完成后执行对应测试，并做一次只读自审。新增代码必须沿用现有 API、Repository、幂等、事务、审计、回调和 Starter 装配模式，不得为了 M6 快速闭环复制已有服务。

## 基线优先级

发生描述差异时按以下优先级执行：

1. `doc/rebuild-functional-requirements-optimized.md`。
2. `doc/流程平台设计与接口文档_v4.md`。
3. `doc/流程平台技术路线_v5.2.md`。
4. 当前代码中的公共 DTO、Request、Service、SPI、DDL。
5. `doc/实习生C/M6/执行计划.md`。
6. `doc/实习生C/实习生C开发文档.md`。
7. `doc/实习生B/m6-coding-plan.md`，仅用于会签边界和联调约束。

当前代码中已经存在并必须优先复用：

- `AdminProcessService`
- `DefaultAdminProcessService`
- `ProcessRuntimeService`
- `DefaultProcessRuntimeService`
- `ProcessMonitorService`
- `DefaultProcessMonitorService`
- `TaskQueryService`
- `DefaultTaskQueryService`
- `RuntimeNodeAdvancer`
- `InstanceTaskCancellationService`
- `TaskGroupRepository`
- `ActiveTaskRepository`
- `ProcessInstanceRepository`
- `ProcessHistoryTaskRepository`
- `ProcessCallbackLogRepository`
- `ProcessAuditLogRepository`
- `ReminderRecordRepository`
- `AlertRecordRepository`
- `RuntimeOperationExecutor`
- `RuntimeTransactionExecutor`
- `RuntimeRequestValidator`
- `AuditLogWriter`
- `MessagePublisher`
- `WorkflowCallbackHandler`
- `PlatformAutoConfiguration`
- `PlatformStandaloneConfiguration`

## 目标结构

目标结构优先沿用现有包。新增组件只承载 M6 治理能力，不改变公共 Service 边界。

```text
platform-core/src/main/java/com/flowmind/platform
├── api/
│   ├── dto/
│   │   └── TaskGroupViewDTO.java             # 可选：并行/会签任务组诊断视图
│   └── request/
│       └── 保持现有 JumpNodeRequest、ForceCompleteRequest、TimeoutScanRequest
├── core/
│   ├── query/
│   │   └── AdminQueryAssembler.java          # 管理端查询 DTO 映射
│   ├── runtime/
│   │   └── AdminPermissionGuard.java         # 管理员修复权限校验，可放 core.security
│   ├── monitor/
│   │   ├── TimeoutPolicy.java
│   │   ├── TimeoutPolicyReader.java
│   │   ├── ReminderPolicy.java
│   │   ├── ReminderPolicyReader.java
│   │   ├── TimeoutDueDateCalculator.java
│   │   ├── ReminderDeduplicationGuard.java
│   │   ├── TimeoutActionExecutor.java
│   │   └── ActionExceptionAlertWriter.java
│   └── callback/
│       ├── CallbackDispatchService.java
│       └── CallbackFailureAlertService.java
├── persistence/
│   └── repository/
│       ├── ProcessInstanceRepository.java    # 增加管理员分页查询
│       ├── ActiveTaskRepository.java         # 增加管理员活动任务分页查询
│       ├── ProcessHistoryTaskRepository.java # 增加管理员历史任务分页查询
│       ├── TaskGroupRepository.java          # 增加任务组诊断查询
│       ├── AlertRecordRepository.java        # 增加按事件/动作去重查询
│       └── ReminderRecordRepository.java     # 增加提醒窗口去重查询
└── web/
    ├── AdminQueryController.java
    └── ProcessMonitorController.java

platform-starter/src/main/java/com/flowmind/platform/starter
└── PlatformAutoConfiguration.java
```

推荐管理员修复数据流：

```text
AdminProcessService.jumpToNode / forceComplete
  -> AdminPermissionGuard 校验管理员
  -> RuntimeRequestValidator 校验可信当前用户
  -> RuntimeOperationExecutor 建立幂等租约
  -> InstanceTaskCancellationService 取消开放任务和活动任务组
  -> RuntimeNodeAdvancer 重新推进或结束实例
  -> AuditLogWriter 写 INSTANCE 审计
  -> CallbackService 写 PROCESS_JUMPED / PROCESS_COMPLETED 回调
  -> RuntimeOperationExecutor 保存首次结果
```

推荐超时治理数据流：

```text
ProcessMonitorService.scanTimeoutTasks
  -> ActiveTaskRepository 查询 dueAt 已过期开放任务
  -> TimeoutPolicyReader / ReminderPolicyReader 读取节点策略
  -> ReminderDeduplicationGuard 判断提醒窗口
  -> TimeoutActionExecutor 执行 REMIND / ALERT / JUMP / TERMINATE / FORCE_COMPLETE
  -> AuditLogWriter 写审计
  -> MessagePublisher 发送提醒或告警消息
  -> AlertRecordRepository / ReminderRecordRepository 落库并去重
```

推荐回调失败治理数据流：

```text
CallbackDispatchService.dispatchPending
  -> ProcessCallbackLogRepository.findPending
  -> WorkflowCallbackHandler.handle
  -> 成功 markSuccess
  -> 失败 markFailed
  -> CallbackFailureAlertService 写 CALLBACK_FAILED 告警
  -> queryAlerts / handleAlert 管理端处理
```

## 统一编码规则

- Java 8、Spring Boot 2.7.18。
- 新增公开 API 类型使用 JavaBean，不使用 `record`、Java 9+ API 或新的重复公共类型。
- 状态修改接口必须携带 `operationId`；任务级修改必须携带 `expectedTaskVersion`。
- 管理员修复和告警处理必须校验管理员权限；首期可使用配置化管理员用户集合或 Mock 规则，但要有清晰可替换边界。
- 活动任务和任务组修改必须使用 `task_status/group_status + lock_version` 条件更新。
- Repository 负责 SQL，Service/Coordinator 负责身份、权限、幂等、事务和 SPI 编排。
- 新增 SQL 必须隔离在 Repository 内；SQLite `json_each/json_extract/json_set` 不得扩散到 Service。
- 审计写入统一通过 `AuditLogWriter`，M6 新增代码不得继续调用 `ProcessDefinitionRepository.insertAuditLog`。
- 回调投递必须在主流程事务外执行；主流程只负责 outbox 落库。
- `dryRun=true` 的扫描不得写提醒、告警、审计、回调或幂等成功结果。
- 超时自动动作必须去重，相同任务和同一策略窗口不得重复跳转、终止、强制办结或发送提醒。
- 参数校验错误和权限拒绝默认不生成 `ACTION_EXCEPTION` 告警；只对可诊断的平台异常生成告警。
- Starter 只装配 core 真实实现，宿主 Bean 必须可覆盖。

## 微任务与编码提示词

### C6.1：对齐 M6 当前基线和跨线边界

目标：

- 核对 M6 执行计划、需求文档和当前代码。
- 确认并行汇聚核心逻辑已经完成，不重复开发。
- 明确管理员查询、权限、审计、任务组诊断、超时策略、回调失败告警和动作异常告警缺口。
- 记录当前 Maven 测试基线。

提示词：

“请只读审查 C 线 M6 执行计划和当前代码。确认 `RuntimeNodeAdvancer` 并行拆分/汇聚、`TaskGroupRepository.markBranchArrived`、`DefaultAdminProcessService.jumpToNode/forceComplete`、`DefaultProcessMonitorService.scanTimeoutTasks`、`DefaultCallbackService`、管理员查询方法和告警 Repository 的实现状态。列出 M6 必须补齐的 DTO、Repository、Service、Controller、Starter 装配和测试缺口。不得重写并行汇聚，不得实现 A 线条件/或签或 B 线会签。”

测试与完成条件：

- 形成 M6 缺口清单。
- 明确每个缺口对应的现有表和现有类。
- 明确并行汇聚已完成、嵌套并行不支持。
- 执行并记录当前核心测试结果。

建议提交：`docs: align C M6 baseline gaps`

### C6.2：补齐管理员运行数据分页查询

目标：

- 实现 `DefaultAdminProcessService.queryInstances`、`queryActiveTasks`、`queryHistoryTasks`。
- 扩展 `AdminQueryController`，暴露管理员实例、活动任务、历史任务查询入口。
- 流程定义查询复用现有定义查询接口；需要统一管理端入口时只做薄转发。

提示词：

“请实现 C 线 M6 管理员运行数据查询。补齐 `DefaultAdminProcessService.queryInstances`、`queryActiveTasks`、`queryHistoryTasks`，在对应 Repository 中实现分页、过滤、稳定排序和 count。扩展 `AdminQueryController` 暴露 REST 入口。查询结果必须返回并行和会签相关的 `taskGroupId`、`branchKey`、任务状态、认领人、办理人和时间字段。不得内存分页，不得复制流程定义查询 SQL。”

实现要点：

- `ProcessInstanceRepository` 增加管理员实例分页，支持流程编码、状态、标题、业务键、发起人、当前节点、发起时间过滤。
- `ActiveTaskRepository` 增加管理员活动任务分页，支持实例、流程、节点、任务状态、办理人、任务组、分支、到期时间过滤。
- `ProcessHistoryTaskRepository` 增加管理员历史任务分页，支持实例、办理人、动作、节点、任务组、分支、完成时间过滤。
- `AdminQueryController` 只做参数绑定和 Service 调用。
- 分页统一使用 `PageResult<T>` 和 `PageQueryNormalizer`。

测试与完成条件：

- 管理端实例、活动任务、历史任务分页结果和总数一致。
- 支持按当前节点和任务状态过滤运行中实例/任务。
- 并行任务返回 `taskGroupId/branchKey`。
- 会签任务返回共享 `taskGroupId`。
- REST 参数绑定正确，Controller 不依赖 Repository。

建议提交：`feat: add admin runtime queries`

### C6.3：实现管理员权限 Guard 与修复审计统一

目标：

- 增加管理员权限边界。
- `jumpToNode`、`forceComplete` 改用 `AuditLogWriter`。
- 验证终止实例可通过管理员跳转恢复流转。
- 验证并行流程修复不会留下孤立任务组。

提示词：

“请收口 C 线 M6 管理员修复。新增 `AdminPermissionGuard` 或等价组件，只允许管理员执行 `jumpToNode`、`forceComplete` 和告警处理。将 `DefaultAdminProcessService.writeAudit` 改为使用 `AuditLogWriter`，不得继续调用 `ProcessDefinitionRepository.insertAuditLog`。补测试覆盖非管理员拒绝、终止实例管理员跳转恢复、并行流程跳转/强制办结时取消开放任务和活动任务组、幂等重放不重复写历史审计回调。”

实现要点：

- 当前权限 SPI 缺失时，首期可约定 `operatorUserId=admin` 或配置化管理员集合。
- 非管理员拒绝应发生在状态修改前，不写历史、审计、回调或幂等成功。
- `TERMINATED` 实例跳转先 `reopenForJump` 恢复为 `RUNNING`，再调用统一推进器。
- `COMPLETED/ARCHIVED` 等不允许恢复的终态必须明确拒绝。
- 并行流程跳转或强制办结必须取消全部开放任务和活动任务组。
- 修复动作审计 detail 至少包含 `targetNodeCode/comment/archivedTaskCount/schemaVersion`。

测试与完成条件：

- 非管理员跳转和强制办结均失败且无副作用。
- 管理员跳转到用户任务能创建目标任务。
- 管理员跳转到条件网关或并行拆分网关会继续调用统一推进器。
- 管理员不能跳转到 `START` 或 `PARALLEL_JOIN_GATEWAY`。
- 终止实例管理员跳转后恢复 `RUNNING`。
- 并行流程跳转/强制办结后无 ACTIVE 任务组残留。
- 审计通过 `AuditLogWriter` 写入。

建议提交：`feat: secure admin repair actions`

### C6.4：补齐并行任务组诊断视图

目标：

- 管理端可查看并行任务组和分支状态。
- 支持排查哪些分支已到达、哪些仍在运行、任务组是否已完成或取消。

提示词：

“请实现 C 线 M6 并行任务组诊断查询。扩展 `TaskGroupRepository` 支持按实例查询任务组，新增 `TaskGroupViewDTO` 或等价管理端视图，展示 `groupId/nodeCode/joinNodeCode/groupType/totalCount/completedCount/branchStates/groupStatus/lockVersion/parentGroupId/parentBranchKey`。解析 `branch_state_json` 为稳定 Map；非法 JSON 转换为平台错误，不泄漏数据库异常。不要改写 `RuntimeNodeAdvancer` 的并行汇聚逻辑。”

实现要点：

- 可先通过管理端单实例诊断接口暴露任务组视图。
- 并行组展示 `branchStates`；会签组保留计数和状态，分支状态为空 Map。
- 查询排序按 `created_at ASC, id ASC`。
- 任务组状态展示必须包含 `ACTIVE/COMPLETED/CANCELED`。
- 删除或终止实例后的任务组展示遵循现有删除/取消策略。

测试与完成条件：

- 并行分支创建后展示多个 `RUNNING` 分支。
- 部分分支完成后展示 `completedCount` 和 `ARRIVED` 分支。
- 全部分支完成后展示 `COMPLETED`。
- 管理员跳转或强制办结后展示 `CANCELED`。
- 非法 `branch_state_json` 返回稳定平台错误。

建议提交：`feat: expose task group diagnostics`

### C6.5：实现节点级超时策略和任务 dueAt 生成

目标：

- 解析节点 `timeout_config`。
- 创建用户任务时根据节点策略自动写入 `dueAt`。
- 让超时扫描不再依赖测试或外部手工填充 `due_at`。

提示词：

“请实现 C 线 M6 节点级超时策略。新增 `TimeoutPolicy`、`TimeoutPolicyReader`、`TimeoutDueDateCalculator`，从用户任务节点 `timeoutConfig` 解析最小策略：`enabled`、`durationMinutes`、`severity`、`action`、`targetNodeCode`。在 `RuntimeNodeAdvancer.createActiveTask` 创建用户任务时，根据策略计算并写入 `dueAt`。非法 JSON、负数时长、未知 action/severity 返回稳定错误；未配置或禁用时保持 `dueAt=null`。”

实现要点：

- 首期 `action` 只支持 `ALERT/REMIND/JUMP/TERMINATE/FORCE_COMPLETE`。
- `JUMP` 必须要求 `targetNodeCode`。
- `severity` 映射 `LOW/MEDIUM/HIGH`，默认 `MEDIUM`。
- 只对 `USER_TASK` 生成 `dueAt`。
- 不在策略 Reader 中调用外部 SPI。
- 策略非法应尽量在定义发布校验阶段发现；运行时也要失败关闭。

测试与完成条件：

- `durationMinutes=30` 时新任务 `dueAt=createdAt+30min`。
- 禁用或未配置时 `dueAt=null`。
- 非法 JSON、负数时长、未知 action/severity 抛稳定错误。
- 会签和并行分支中的用户任务同样能生成 `dueAt`。
- `scanTimeoutTasks` 能发现运行时自动生成的超时任务。

建议提交：`feat: derive task due date from timeout policy`

### C6.6：实现自动提醒与超时自动动作

目标：

- 解析节点 `reminder_config`。
- 超时扫描按策略生成提醒、告警、自动跳转、自动终止或强制办结。
- 所有自动动作必须幂等去重。

提示词：

“请升级 C 线 M6 `scanTimeoutTasks`。新增 `ReminderPolicy`、`ReminderPolicyReader`、`ReminderDeduplicationGuard`、`TimeoutActionExecutor`。扫描 `due_at <= scanAt` 的 `ACTIVE/CLAIMED` 任务时，`dryRun=true` 只返回任务；`dryRun=false` 根据 `reminderConfig` 生成 `AUTO/TIMEOUT` 提醒并调用 `MessagePublisher`，根据 `timeoutConfig.action` 生成告警、自动跳转、自动终止或强制办结。自动动作必须使用系统操作人、写审计和回调，并保证重复扫描无副作用。”

实现要点：

- 提醒策略最小字段：`enabled`、`intervalMinutes`、`maxCount`、`targetRule`、`messageTemplate`。
- 提醒目标优先办理人，其次候选人。
- 相同任务、提醒类型和窗口不得重复发送。
- `action=ALERT` 生成 `TASK_TIMEOUT` 告警。
- `action=JUMP` 复用管理员跳转能力，不允许跳转到 `START/PARALLEL_JOIN_GATEWAY`。
- `action=TERMINATE/FORCE_COMPLETE` 复用现有清理逻辑，取消开放任务和活动任务组。
- 自动动作幂等号建议固定为 `timeout:{taskId}:{action}:{window}`。
- 消息发送失败只更新提醒状态，不回滚已提交记录。

测试与完成条件：

- dry-run 不写任何提醒、告警、审计、回调或幂等成功。
- 启用提醒策略时生成提醒并发送 Mock 消息。
- 重复扫描同一窗口不重复提醒。
- 超过最大提醒次数后可升级为 `TASK_TIMEOUT` 告警。
- `JUMP` 自动创建目标节点任务，原任务取消归档。
- `TERMINATE/FORCE_COMPLETE` 自动结束实例并清理开放任务。
- 自动动作重复扫描不重复执行。

建议提交：`feat: apply timeout handling policy`

### C6.7：实现回调投递和失败告警

目标：

- 实现 PENDING 回调投递入口。
- 回调失败写 `CALLBACK_FAILED` 告警。
- 回调异常不得阻断主流程。

提示词：

“请实现 C 线 M6 回调失败治理。新增 `CallbackDispatchService.dispatchPending(limit)` 读取 `ProcessCallbackLogRepository.findPending`，调用 `WorkflowCallbackHandler.handle`。成功时 `markSuccess`；失败时 `markFailed` 并通过 `CallbackFailureAlertService` 写 `CALLBACK_FAILED` 告警。失败告警按 `eventId` 去重，detail 包含 `eventId/operationId/eventType/actionType/retryCount/errorSummary`。不要把外部回调调用放进主流程事务。”

实现要点：

- `dispatchPending` 可手动调用或供后续调度器调用，不要求本期引入真实定时任务。
- `WorkflowCallbackHandler` 缺失时应给出明确错误或保留 PENDING，不静默成功。
- 告警没有任务上下文时允许 `taskId=null`。
- `AlertRecordRepository` 需要增加按 `alertType + detail.eventId` 或等价字段的去重查询。
- 告警写入失败不能改变回调日志已经失败的事实，需要记录边界。

测试与完成条件：

- PENDING 回调处理成功后变为 `SUCCESS`。
- 回调处理器抛异常后变为 `FAILED`，`retry_count` 增加。
- 失败后生成 `CALLBACK_FAILED` 告警。
- 同一事件重复失败不重复打开告警。
- 告警可查询、可处理，并写 `ALERT_HANDLE` 审计。

建议提交：`feat: alert on callback dispatch failure`

### C6.8：实现动作异常告警

目标：

- 对可诊断的平台动作异常生成 `ACTION_EXCEPTION` 告警。
- 保留错误码和上下文，帮助管理员排查。

提示词：

“请实现 C 线 M6 动作异常告警。新增 `ActionExceptionAlertWriter`，对运行时或管理员动作中的状态异常、任务组重试耗尽、SPI 异常、消息/回调外部异常等可诊断平台异常写 `ACTION_EXCEPTION` 告警。参数缺失、权限拒绝、普通业务校验失败不写告警。告警 detail 包含 `operationId/actionType/instanceId/taskId/errorCode/errorSummary/operatorId`，并按 `operationId + actionType` 去重。”

实现要点：

- 告警写入应在独立、可恢复的边界内完成，不污染业务主事务。
- 主事务失败时不得留下成功审计或成功回调。
- 并发冲突是否告警要谨慎：普通任务版本冲突不告警，任务组重试耗尽可告警。
- 告警摘要截断，避免把堆栈或敏感数据写入 `detailJson`。

测试与完成条件：

- 任务组重试耗尽生成 `ACTION_EXCEPTION`。
- 管理员修复任务组取消并发冲突生成 `ACTION_EXCEPTION`。
- 参数缺失、权限拒绝不生成告警。
- 同一 `operationId + actionType` 重复异常不重复打开告警。
- 告警处理写 `ALERT_HANDLE` 审计。

建议提交：`feat: record actionable runtime exception alerts`

### C6.9：REST、Starter 与 Standalone 装配收口

目标：

- 暴露 M6 新增查询、诊断、回调投递和监控入口。
- Starter 和独立应用装配同一套 M6 core 实现。
- 宿主可覆盖权限、消息、回调等关键 SPI。

提示词：

“请补齐 C 线 M6 REST 和 Starter 装配。REST 暴露管理员实例查询、活动任务查询、历史任务查询、任务组诊断查询、超时扫描、告警查询和处理、可选的回调投递调试入口。Controller 只做参数绑定和 Service 调用。`PlatformAutoConfiguration` 装配 M6 新增 Reader、Guard、Executor、Dispatcher 和 Repository 增量，并使用 `@ConditionalOnMissingBean` 保持宿主覆盖能力。独立应用和 Starter 必须复用同一套 core Bean。”

实现要点：

- 路径以 v4 文档和现有 Controller 风格为准。
- 回调投递调试入口若暴露，必须在管理端命名空间下，并受管理员权限保护。
- `flow-mind.platform.enabled=false` 时不装配平台 Bean。
- `flow-mind.platform.mock.enabled=false` 时不装配本地 Mock SPI。
- `AdminPermissionGuard`、`MessagePublisher`、`WorkflowCallbackHandler` 均允许宿主覆盖。

测试与完成条件：

- REST 参数绑定和错误响应稳定。
- Controller 不直接依赖 Repository。
- 默认 Starter 上下文能注入 M6 新增 Bean。
- 宿主自定义 Bean 可覆盖默认实现。
- 独立应用和 Starter 测试均通过。

建议提交：`feat: wire M6 admin and monitor beans`

### C6.10：M6 跨线联调与回归固定

目标：

- 验证 A/B/C 组合能力。
- 固化并行、条件、会签、管理员修复、超时治理、回调失败和告警处理的端到端验收。

提示词：

“请做 C 线 M6 跨阶段回归。基于已有流程运行时，验证并行汇聚、条件后并行、并行分支内会签、管理员跳转、强制办结、终止后恢复、节点级超时、自动提醒、自动跳转、自动终止、回调失败告警和告警处理。重点断言并行汇聚只推进一次，管理员修复不会留下孤立任务组，查询和审计可追溯，重复扫描和幂等重放没有副作用。”

测试场景：

- 并行分支全部完成后只创建一次汇聚后任务。
- 并行分支内会签完成后恢复外层 `taskGroupId/branchKey` 并参与汇聚。
- 条件分支命中后进入并行，查询展示多个当前节点。
- 并行运行中管理员跳转，原任务和任务组取消，只保留目标路径。
- 并行运行中强制办结，实例完成且无开放任务组。
- 终止实例后管理员跳转恢复 `RUNNING`。
- 超时自动提醒和告警可查询、可处理。
- 超时自动跳转/自动终止重复扫描无副作用。
- 回调失败生成告警，处理告警写审计。

测试与完成条件：

- M2 并行、M5 监控、M6 管理员修复和回调失败测试全部通过。
- 根级 `mvn -q test` 通过。
- 记录无法执行的测试和原因。

建议提交：`test: add C M6 cross stage regression`

### C6.11：文档收口和阶段记录

目标：

- 更新 M6 技术实现说明、完成记录和测试结果。
- 记录实际新增类、接口、策略 JSON、REST 路径、装配规则和已知限制。

提示词：

“请根据实际代码交付更新 C 线 M6 文档。记录管理员查询、管理员权限 Guard、管理员跳转/强制办结审计统一、终止后恢复、任务组诊断、节点级超时策略、自动提醒、自动跳转/终止、回调失败告警、动作异常告警、REST 路径、Starter 装配和测试结果。明确并行汇聚核心逻辑已在前置代码完成，C 线本期只做治理、查询、告警和回归收口。”

测试与完成条件：

- 文档中的类名、方法名、路径、配置项与代码一致。
- 记录 Maven 测试命令和结果。
- 明确未实现能力和后续阶段归属。
- 阶段完成记录可作为交接材料。

建议提交：`docs: record C M6 delivery`

## 推荐执行顺序

1. C6.1 对齐 M6 当前基线和跨线边界。
2. C6.2 补齐管理员运行数据分页查询。
3. C6.3 实现管理员权限 Guard 与修复审计统一。
4. C6.4 补齐并行任务组诊断视图。
5. C6.5 实现节点级超时策略和任务 `dueAt` 生成。
6. C6.6 实现自动提醒与超时自动动作。
7. C6.7 实现回调投递和失败告警。
8. C6.8 实现动作异常告警。
9. C6.9 REST、Starter 与 Standalone 装配收口。
10. C6.10 M6 跨线联调与回归固定。
11. C6.11 文档收口和阶段记录。

## 最小验收清单

- 并行汇聚核心测试保持通过，重复分支到达不重复计数，最后分支只推进一次。
- 管理端可分页查询实例、活动任务、历史任务、审计日志、回调日志、提醒和告警。
- 管理端能查看任务组计数、分支状态、父组上下文和终态。
- 非管理员不能执行跳转、强制办结、告警处理或回调投递调试。
- 管理员跳转和强制办结在并行流程中不会留下孤立活动任务组。
- 终止实例可通过管理员跳转恢复流转。
- 管理员修复动作统一通过 `AuditLogWriter` 写审计。
- 用户任务可根据节点 `timeout_config` 自动生成 `dueAt`。
- 超时扫描支持 dry-run、自动提醒、告警升级、自动跳转、自动终止和重复扫描去重。
- 自动跳转和自动终止写审计、回调和幂等结果。
- 回调投递失败会生成 `CALLBACK_FAILED` 告警。
- 可诊断的平台动作异常会生成 `ACTION_EXCEPTION` 告警。
- 参数校验错误和权限拒绝不会刷告警。
- 告警可处理或忽略，并写 `ALERT_HANDLE` 审计。
- Starter 和独立 REST 应用均能使用 M6 新增能力。
- M6 跨线回归覆盖条件、并行、会签、管理员修复、超时治理、回调失败和告警处理。

## 建议验证命令

```powershell
mvn -q -pl platform/platform-core -am -Dtest=RuntimeNodeAdvancerTest,M2GatewayWorkflowIntegrationTest,M2RuntimeConcurrencyIntegrationTest test
mvn -q -pl platform/platform-core -am -Dtest=DefaultAdminProcessServiceTest,DefaultProcessMonitorServiceTest test
mvn -q -pl platform/platform-core -am test
mvn -q -pl platform/platform-starter -am -DfailIfNoTests=false test
mvn -q test
```

