# 实习生 C：M2 幂等、轨迹与回调 Vibe Coding 计划

## 产物与边界

本文件作为实习生 C 在 M2 阶段的专项编码计划。M2 只实现运行期外围能力：操作幂等、运行状态校验、审批意见与流程轨迹、回调日志 Outbox，以及与 B 线串行运行时闭环联调。

M2 不实现以下内容：

- B 线主状态机：实例启动、任务创建、节点推进、流程办结决策。
- A 线定义管理：流程定义 CRUD、发布、激活、节点审批人规则保存。
- 审计日志写入、审计查询和审计告警；该能力留到 M5。
- 实例级附件保存、下载、删除、必填校验和权限；该能力留到 M4。
- 真实 HTTP 回调投递、真实消息推送和真实文件存储。

每个微任务完成后执行对应测试，并做一次只读自审。不得创建重复 DTO、重复枚举或第二套 Service 契约。

## 基线优先级

发生描述差异时按以下优先级执行：

1. 当前代码中的已冻结公共 DTO、Request、Service、SPI、DDL。
2. doc/rebuild-functional-requirements-optimized.md
3. `doc/流程平台设计与接口文档_v4.md`。
4. `doc/流程平台技术路线_v5.2.md`。
5. `doc/实习生C/M2/执行计划.md`。
5. `doc/实习生C/实习生C开发文档.md`。

当前代码中已经存在并必须复用：

- `OperationIdempotencyService`
- `OperationIdempotencyDecision`
- `OperationIdempotencyDecisionType`
- `ProcessOperationRecordRepository`
- `ActiveTaskRepository`
- `TaskGroupRepository`
- `TaskQueryService`
- `CallbackService`
- `ProcessRuntimeService`
- `ProcessHistoryTaskEntity`
- `ProcessCallbackLogEntity`
- `ProcessOperationRecordEntity`

## 目标结构

目标结构必须严格沿用现有 `core` 下的一级包，不新增 `core/history`。历史轨迹写入属于任务归档协作，放入 `core/task`；轨迹和审批意见查询组装属于查询能力，放入 `core/query`。

```text
platform-core/src/main/java/com/flowmind/platform
├── core/
│   ├── definition/
│   ├── runtime/
│   │   ├── RuntimeStateValidator.java
│   │   ├── RuntimeTaskContext.java
│   │   └── RuntimeInstanceContext.java
│   ├── task/
│   │   ├── HistoryTaskWriter.java
│   │   └── HistoryArchiveCommand.java
│   ├── query/
│   │   ├── DefaultTaskQueryService.java
│   │   └── ProcessTraceAssembler.java
│   ├── attachment/
│   ├── callback/
│   │   ├── CallbackOutboxService.java
│   │   ├── WorkflowEventFactory.java
│   │   ├── CallbackLogMapper.java
│   │   └── DefaultCallbackService.java
│   ├── reminder/
│   ├── monitor/
│   ├── audit/
│   └── validation/
├── persistence/
│   └── repository/
│       ├── ProcessInstanceRepository.java
│       ├── ProcessHistoryTaskRepository.java
│       └── ProcessCallbackLogRepository.java
└── persistence/repository/
    ├── ActiveTaskRepository.java       # 新增只读 findById
    └── TaskGroupRepository.java        # 可选新增只读 findById

platform-core/src/test/java/com/flowmind/platform
├── core/runtime/
├── core/task/
├── core/callback/
├── core/query/
├── persistence/repository/
└── integration/
```

推荐数据流：

```text
任务动作请求
  -> OperationIdempotencyService.beginOrReplay
  -> RuntimeStateValidator 读取 active_task + instance
  -> B 线 CAS 更新活动任务
  -> HistoryTaskWriter 写 process_history_task
  -> B 线创建下一任务或办结实例
  -> CallbackOutboxService 写 process_callback_log(PENDING)
  -> OperationIdempotencyService.markSuccess
  -> 事务提交
  -> 后续扫描器或投递组件处理 PENDING 回调
```

## 统一编码规则

- Java 8、Spring Boot 2.7.18。
- 新增公开 API 类型使用 JavaBean，不使用 `record`、Java 9+ API 或新的重复公共类型。
- Repository 负责 SQL 访问，Service/Writer/Validator 负责事务编排和业务语义。
- DTO 面向调用方，Entity 面向数据库，不直接互相混用。
- JSON 字段必须通过统一工具或 Jackson 处理，不手写字符串拼接。
- `process_history_task.comment_text` 是审批意见唯一落点，不新增 `process_comment` 表。
- `process_history_task` 是流程轨迹主表，不新增 `process_trace` 表。
- `process_callback_log.event_id` 是回调幂等键，不新增回调幂等表。
- 回调 `event_type` 只能使用现有 `WorkflowEventTypeEnum`。
- 任务动作请求当前没有 `instanceId`，状态校验必须先按 `taskId` 查活动任务，再通过 `instance_id` 查实例。
- 状态校验不能替代数据库 CAS；CAS 返回 0 后不得写历史、回调或下一任务。
- M2 代码不得夹带审计日志和运行时附件实现。

## 微任务与编码提示词

### C2.1：对齐 M2 现有契约和代码缺口

目标：

- 核对 M2 执行计划与当前代码中的 DTO、Entity、DDL、Service 接口是否一致。
- 明确本阶段只写 `process_operation_record`、`process_history_task`、`process_callback_log`。
- 明确状态校验需要读取 `process_instance`、`process_active_task`、`process_task_group`。
- 明确审计日志和运行时附件不纳入 M2。

提示词：

“请只读审查 C 线 M2 的执行计划和当前代码。确认 `TaskOperationRequest` 没有 `instanceId`，`process_task_group` 字段名为 `branch_state_json`，审批意见来自 `process_history_task.comment_text`，回调事件类型使用 `WorkflowEventTypeEnum`。不得新增审计日志或运行时附件范围。”

测试与完成条件：

- 文档和代码字段无冲突。
- 形成 M2 需要新增/补齐类清单。
- 执行 `mvn -q -pl platform/platform-core -am test`，记录基线结果。

建议提交：`docs: align C M2 plan with runtime code`

### C2.2：复用并补齐操作幂等能力

目标：

- 复用现有 `OperationIdempotencyService` 和 `ProcessOperationRecordRepository`。
- 在运行时动作中统一使用 `beginOrReplay`、`markSuccess`、`markFailed`。
- 只补运行时需要的缺口，不重写已有幂等组件。

提示词：

“请基于现有 `OperationIdempotencyService` 和 `ProcessOperationRecordRepository` 接入运行时动作的幂等处理。不要新建第二套幂等 Service。运行时动作必须通过 `beginOrReplay` 判断 `NEW/TAKE_OVER/REPLAY_SUCCESS/REPLAY_FAILED/IN_PROGRESS/CONFLICT`，成功结果写入 `result_json`，同号不同请求返回稳定冲突错误。”

实现要点：

- `requestHash` 由动作 Service 生成，覆盖 `taskId/actionType/variables/comment/expectedTaskVersion` 等业务字段。
- 不包含当前时间、随机 ID、签名时间戳等非业务字段。
- `OperationIdempotencyDecision` 已是 Lombok `@Data`，用 `getType()` 判断，不假设已有 `isReplay()` 或 `canExecute()`。
- `markSuccess` 与主业务事务同提交。
- `markFailed` 只用于确定性失败。
- 可选补 `ProcessOperationRecordRepository.findExpired(now, limit)`，但不阻塞 M2 主链路。

测试与完成条件：

- 同一 `operationId + requestHash` 成功重放返回首次 `result_json`。
- 同一 `operationId` 不同 `requestHash` 返回冲突。
- `PROCESSING` 租约未过期返回处理中。
- 租约过期的相同请求可接管。
- 并发登记同一 `operationId` 只有一个进入业务执行。

建议提交：`feat: wire runtime operation idempotency`

### C2.3：补齐运行状态校验读取能力

目标：

- 新增运行期只读校验组件。
- 校验任务动作前的实例、活动任务、任务版本和状态。
- 不写历史、不写回调、不更新幂等。

提示词：

“请新增运行期状态校验组件 `RuntimeStateValidator`。任务动作请求只传 `taskId` 和 `expectedTaskVersion`，校验器先通过 `ActiveTaskRepository.findById(taskId)` 读取活动任务，再通过任务上的 `instanceId` 读取 `ProcessInstanceRepository.findById(instanceId)`。校验通过返回 `RuntimeTaskContext`，校验失败抛出带稳定错误码的业务异常。状态校验不得替代后续 CAS。”

实现要点：

- 新增 `ProcessInstanceRepository.findById`。
- 在 `ActiveTaskRepository` 新增只读 `findById`，不要改现有 CAS 方法语义。
- 可选在 `TaskGroupRepository` 新增只读 `findById`，为后续任务组校验保留。
- 修正任务组字段为 `branch_state_json`。
- `validateTaskAction(taskId, expectedTaskVersion, actionType, operator)` 返回 `RuntimeTaskContext`。
- `APPROVE/SEND` 要求任务状态为 `ACTIVE` 或 `CLAIMED`。
- `expectedTaskVersion` 与 `lock_version` 不一致时返回 `FLOW_TASK_CONCURRENT_MODIFIED`。
- 实例办理类动作要求实例状态为 `RUNNING`。

测试与完成条件：

- 任务不存在、实例不存在、实例非运行中、任务状态非法、版本不匹配都有稳定错误。
- 校验通过后仍模拟 CAS 失败，确认不会写任何副作用。
- `branch_state_json` 字段读取测试通过。

建议提交：`feat: add runtime state validator`

### C2.4：实现历史轨迹和审批意见写入

目标：

- 新增 `ProcessHistoryTaskRepository`。
- 在 `core/task` 新增 `HistoryTaskWriter` 和 `HistoryArchiveCommand`。
- 把任务归档时的审批意见写入 `process_history_task.comment_text`。

提示词：

“请为 C 线 M2 实现历史轨迹写入。新增 `ProcessHistoryTaskRepository`，使用现有 `ProcessHistoryTaskEntity` 和 SQLite 表 `process_history_task`。新增 `HistoryTaskWriter`，在 B 线活动任务 CAS 成功后调用，将活动任务、操作人、动作、审批意见和变量快照归档。不得新增 `process_comment` 或 `process_trace` 表，重复 `active_task_id + action_type + operation_id` 必须被幂等约束阻止。”

实现要点：

- `insert(ProcessHistoryTaskEntity entity)`。
- `findByInstanceId(String instanceId)`，排序 `started_at ASC, completed_at ASC, id ASC`。
- `existsByTaskActionOperation(activeTaskId, actionType, operationId)`。
- `queryCompletedTasks(CompletedTaskQuery query)` 可先为 M3 已办查询打底，但 M2 主验收集中在实例轨迹和意见。
- `comment_text` 允许为空，但 `queryComments` 只展示非空意见。
- `variables_snapshot` 用 JSON 序列化任务完成后的流程变量。
- `task_group_id` 和 `branch_key` 必须原样保留。

测试与完成条件：

- 申请节点、部门经理审批、财务确认均能归档。
- 审批意见正确写入 `comment_text`。
- 变量快照可序列化和反序列化。
- 重复归档同一 `active_task_id + action_type + operation_id` 不产生重复历史。

建议提交：`feat: add history task writer`

### C2.5：实现轨迹和审批意见查询

目标：

- 新增 `ProcessTraceAssembler`。
- 新增或接入 `DefaultTaskQueryService`。
- 实现 `queryHistoryTasks(instanceId)` 和 `queryComments(instanceId)`。

提示词：

“请实现 C 线 M2 的轨迹和审批意见查询。在 `core/query` 新增 `ProcessTraceAssembler`，`queryHistoryTasks(instanceId)` 查询 `process_history_task` 并转换为 `HistoryTaskDTO`。`queryComments(instanceId)` 复用历史任务查询结果，只返回 `comment_text` 非空记录，并转换为 `ProcessCommentDTO`。`commentId` 使用历史任务 ID。`TaskQueryService` 的其它方法若本阶段不实现，必须返回稳定 `UnsupportedOperationException` 或沿用既有骨架，保证编译通过。”

实现要点：

- `HistoryTaskDTO.historyTaskId <- process_history_task.id`。
- `HistoryTaskDTO.activeTaskId <- active_task_id`。
- `HistoryTaskDTO.comment <- comment_text`。
- `HistoryTaskDTO.variablesSnapshot <- variables_snapshot JSON`。
- `ProcessCommentDTO.commentId <- historyTask.id`。
- `ProcessCommentDTO.taskId <- active_task_id`。
- `ProcessCommentDTO.operatorUserId/operatorUserName <- assignee_user_id/assignee_user_name`。
- `ProcessCommentDTO.createdAt <- completed_at`。
- 如需展示节点名称，优先通过 `process_node` 按 `definition_id + node_code` 读取；当前 DTO 没有节点名称字段时不扩展公共 DTO。

测试与完成条件：

- 空实例轨迹按约定返回空列表或稳定错误。
- 完整实例返回三段历史。
- 审批意见查询过滤空意见。
- 排序稳定。

建议提交：`feat: expose history and comments queries`

### C2.6：实现回调日志 Outbox

目标：

- 新增 `ProcessCallbackLogRepository`。
- 新增 `CallbackOutboxService`。
- 新增 `WorkflowEventFactory` 和 `CallbackLogMapper`。
- 实现或接入 `DefaultCallbackService`。

提示词：

“请实现 M2 回调日志 Outbox。回调日志使用 `process_callback_log`，`event_id` 是唯一幂等键。事件类型必须使用现有 `WorkflowEventTypeEnum`，不得新增自由字符串事件类型。`CallbackService.publishCallback` 在 M2 只允许在当前主事务内写 `PENDING` Outbox，不同步调用 `WorkflowCallbackHandler`，不开启独立事务，不在该方法内标记 `SUCCESS/FAILED`。”

实现要点：

- `ProcessCallbackLogRepository.findByEventId`。
- `insertPending(ProcessCallbackLogEntity entity)`。
- `markSuccess(String eventId)`。
- `markFailed(String eventId, String lastError)`。
- `query(CallbackLogQuery query)`。
- `eventId` 推荐 `operationId + ":" + WorkflowEventTypeEnum.name() + ":" + sequence`。
- 重复 `eventId` 且载荷一致视为幂等跳过。
- 重复 `eventId` 但载荷不一致视为事件冲突。
- `payload_json` 保存完整 `WorkflowEvent`。
- 查询按 `created_at DESC, id DESC` 稳定排序。

测试与完成条件：

- 插入 `PENDING` 成功。
- 重复 `eventId` 幂等处理正确。
- `publishCallback` 只写入 `PENDING`。
- 投递成功更新 `SUCCESS`、投递失败更新 `FAILED + retry_count + last_error` 留给后续扫描器或投递组件。
- `queryCallbackLogs` 支持实例、事件类型、状态和分页。

建议提交：`feat: add callback outbox`

### C2.7：接入 B 线串行运行时动作

目标：

- 将 C 的幂等、状态校验、历史轨迹、审批意见和回调接入 B 线主流程。
- 不实现 B 的节点推进决策。
- 完成入金申请串行闭环联调。

提示词：

“请在 B 线运行时动作编排中接入 C 线 M2 组件。入口先调用 `OperationIdempotencyService.beginOrReplay`，任务动作先调用 `RuntimeStateValidator.validateTaskAction(taskId, expectedTaskVersion, actionType, operator)`，随后 B 线执行活动任务 CAS。CAS 成功后调用 `HistoryTaskWriter` 写历史和审批意见，再由 B 创建下一任务或办结实例，最后调用 `CallbackOutboxService` 写回调日志并 `markSuccess`。CAS 失败不得写历史或回调。不得在 C 线实现主状态机。”

实现要点：

- `submitTask` 使用 `ActionTypeEnum.SEND`。
- `approve` 使用 `ActionTypeEnum.APPROVE`。
- 发起流程可生成 `PROCESS_STARTED` 和 `TASK_CREATED` 回调，但是否写启动历史由产品展示决定。
- 任务完成可生成 `TASK_SUBMITTED` 或 `TASK_COMPLETED`，按 B/C 统一语义固定。
- 流程完成生成 `PROCESS_COMPLETED`。
- 成功重放直接返回 `process_operation_record.result_json`，不得重新写历史或回调。

测试与完成条件：

- 启动实例后能看到首个活动任务。
- 提交申请节点后历史中出现申请节点记录。
- 部门经理审批后历史中出现审批记录。
- 财务确认后流程完成，历史中出现财务确认记录。
- `queryHistoryTasks` 和 `queryComments` 查询完整。
- 回调日志与本次归档任务、新建任务一致。

建议提交：`feat: integrate runtime records with serial flow`

### C2.8：并发、幂等和回调失败联调测试

目标：

- 固化 M2 最容易回归的并发和幂等场景。
- 覆盖真实 SQLite 约束和 CAS 更新。
- 不只用内存对象模拟。

提示词：

“请为 C 线 M2 补齐并发、幂等和回调失败集成测试。测试必须使用真实 Repository 和 SQLite 约束，覆盖同一 `operationId` 重放、同一 `operationId` 不同请求冲突、两个不同 `operationId` 并发审批同一任务、回调处理器抛异常但主流程不回滚。失败请求不得产生历史或回调副作用。”

测试与完成条件：

- `OperationIdempotencyServiceTest` 覆盖决策类型。
- `RuntimeStateValidatorTest` 覆盖状态和版本错误。
- `ProcessHistoryTaskRepositoryTest` 覆盖唯一约束和排序。
- `DefaultTaskQueryServiceTest` 覆盖轨迹和意见。
- `ProcessCallbackLogRepositoryTest` 覆盖唯一事件和状态更新。
- `RuntimeM2IntegrationTest` 覆盖串行闭环、重放、并发、回调失败。
- 执行 `mvn -q -pl platform/platform-core -am test`。
- 根目录执行 `mvn -q test`。

建议提交：`test: cover runtime idempotency history callback`

### C2.9：文档收口和阶段记录

目标：

- 更新 M2 技术实现说明、测试文档和阶段完成记录。
- 记录实际新增类、方法、事务边界和未覆盖风险。
- 明确审计日志和运行时附件未纳入 M2。

提示词：

“请根据实际代码交付更新 C 线 M2 文档。记录新增 Repository、Service、Writer、Assembler 和测试用例，说明 `process_history_task.comment_text` 是审批意见落点，`process_callback_log.event_id` 是回调幂等键。明确审计日志留到 M5，实例级附件运行时能力留到 M4。记录 Maven 测试命令和结果。”

测试与完成条件：

- 文档与代码类名、方法名和表字段一致。
- 完成记录包含测试命令和结果。
- 未实现能力明确列为后续阶段，不模糊写成 M2 已完成。

建议提交：`docs: record C M2 runtime delivery`

## 推荐执行顺序

1. C2.1 文档和代码契约对齐。
2. C2.2 幂等组件复用和运行时接入点。
3. C2.3 状态校验读取能力。
4. C2.4 历史轨迹和审批意见写入。
5. C2.5 轨迹和审批意见查询。
6. C2.6 回调 Outbox。
7. C2.7 与 B 线串行闭环联调。
8. C2.8 并发、幂等、回调失败测试。
9. C2.9 文档收口。

## 最小验收清单

- `OperationIdempotencyService.beginOrReplay` 被运行时修改动作复用。
- `RuntimeStateValidator.validateTaskAction(taskId, expectedTaskVersion, actionType, operator)` 可用。
- `ProcessHistoryTaskRepository` 可插入和按实例查询历史。
- `HistoryTaskWriter` 在 CAS 成功后写历史，CAS 失败不写。
- `TaskQueryService.queryHistoryTasks` 可返回完整轨迹。
- `TaskQueryService.queryComments` 可返回审批意见列表。
- `ProcessCallbackLogRepository` 可写 `PENDING`、标记 `SUCCESS/FAILED`、分页查询。
- `CallbackOutboxService` 不在主事务内调用外部 SPI。
- 入金申请串行流程能查询到申请、经理审批、财务确认三段历史。
- 相同 `operationId` 重放不重复生成历史或回调。
- 并发审批同一任务只有一个成功。
- 审计日志和运行时附件未被夹带实现。
