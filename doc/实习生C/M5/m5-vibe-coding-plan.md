# 实习生 C：M5 委托、认领、已读、审计与监控 Vibe Coding 计划

## 产物与边界

本文件作为实习生 C 在 M5 阶段的专项编码计划。M5 在 M0-M4 基线上补齐委托待办、认领/取消认领、已读记录、统一审计、手动催办、提醒/告警查询与处理，以及跨阶段回归测试。

M5 不实现以下内容：

- B 线增强动作：`reject`、`returnToStarter`、`withdraw`、`directSend`、`transfer`、`addSign`。
- A 线定义配置和规则保存：节点监听、条件表达式、会签/或签配置、驳回/直送规则配置。
- 第二套 `ProcessRuntimeService`、`TaskQueryService`、`AdminProcessService`、`AttachmentService` 或 Starter 专用业务实现。
- 真实短信、邮件、企业微信、HTTP 回调投递；消息和回调继续走 `MessagePublisher`、`WorkflowCallbackHandler` SPI 或 Mock。
- 完整 M6 超时策略引擎、并行汇聚治理、管理员深度修复和复杂告警升级策略。
- 委托关系平台自有表；委托关系仍由 `DelegateProvider` 或本地 Mock 提供。

每个微任务完成后执行对应测试，并做一次只读自审。新增代码必须沿用现有 API、Repository、幂等、事务和 Starter 装配模式，不得为了 M5 快速闭环复制已有服务。

## 基线优先级

发生描述差异时按以下优先级执行：

1. `doc/rebuild-functional-requirements-optimized.md`。
2. `doc/流程平台设计与接口文档_v4.md`。
3. `doc/流程平台技术路线_v5.2.md`。
4. 当前代码中的公共 DTO、Request、Service、SPI、DDL。
5. `doc/实习生C/M5/执行计划.md`。
5. `doc/实习生C/实习生C开发文档.md`。
7. `doc/实习生B/m5-coding-plan.md`，仅用于跨线边界和联调约束。

当前代码中已经存在并必须优先复用：

- `ProcessRuntimeService`
- `DefaultProcessRuntimeService`
- `TaskQueryService`
- `DefaultTaskQueryService`
- `AdminProcessService`
- `ActiveTaskRepository`
- `ProcessHistoryTaskRepository`
- `HistoryTaskWriter`
- `RuntimeOperationExecutor`
- `RuntimeRequestHasher`
- `RuntimeTransactionExecutor`
- `CurrentUserProvider`
- `DelegateProvider`
- `MessagePublisher`
- `WorkflowCallbackHandler`
- `PlatformAutoConfiguration`
- `PlatformStandaloneConfiguration`

## 目标结构

目标结构优先沿用现有包。新增组件只承载 M5 外围能力，不改变公共 Service 边界。

```text
platform-core/src/main/java/com/flowmind/platform
├── api/
│   ├── dto/                         # ReadRecordDTO、ReminderDTO、AlertDTO、AuditLogDTO 等必要 DTO
│   ├── request/                     # MarkReadRequest、RemindTaskRequest、AlertQuery 等必要 Request
│   └── service/                     # 复用 ProcessMonitorService、TaskQueryService、ProcessRuntimeService
├── core/
│   ├── audit/
│   │   ├── AuditLogCommand.java
│   │   ├── AuditLogWriter.java
│   │   └── DefaultAuditLogWriter.java
│   ├── query/
│   │   ├── ReadRecordManager.java
│   │   └── ReadRecordMapper.java
│   ├── monitor/
│   │   ├── DefaultProcessMonitorService.java
│   │   ├── MonitorRequestValidator.java
│   │   ├── MonitorModelMapper.java
│   │   └── ReminderMessageFactory.java
│   └── runtime/
│       ├── TaskClaimCoordinator.java
│       └── ClaimActionResultFactory.java
├── persistence/
│   ├── entity/
│   └── repository/
│       ├── ProcessAuditLogRepository.java
│       ├── ProcessReadRecordRepository.java
│       ├── ReminderRecordRepository.java
│       └── AlertRecordRepository.java
└── web/
    ├── PlatformQueryController.java
    ├── ProcessMonitorController.java
    └── AdminQueryController.java        # 仅在现有 Controller 未覆盖时补齐

platform-starter/src/main/java/com/flowmind/platform/starter
└── PlatformAutoConfiguration.java

platform-core/src/test/java/com/flowmind/platform
├── core/audit/
├── core/query/
├── core/monitor/
├── core/runtime/
├── persistence/repository/
├── web/
└── integration/
```

推荐认领数据流：

```text
ProcessRuntimeService.claim/unclaim
  -> TaskClaimCoordinator
  -> CurrentUserProvider 校验可信用户
  -> RuntimeOperationExecutor 建立幂等租约
  -> ActiveTaskRepository 按 taskId + taskStatus + expectedTaskVersion CAS
  -> HistoryTaskWriter 写 CLAIM/UNCLAIM 控制轨迹
  -> AuditLogWriter 写 TASK 审计
  -> CallbackService / WorkflowCallbackHandler 写 TASK_CLAIMED 或 TASK_UNCLAIMED
  -> TaskActionResult.updatedTasks 返回最新 taskVersion
```

推荐监控数据流：

```text
ProcessMonitorService.remindTask / scanTimeoutTasks / handleAlert
  -> MonitorRequestValidator
  -> Repository 读取活动任务、提醒、告警
  -> RuntimeOperationExecutor 保证状态修改幂等
  -> AuditLogWriter 写 REMIND / ALERT_HANDLE 审计
  -> MessagePublisher 事务提交后发送提醒消息
  -> 更新 reminder_record SENT/FAILED
```

## 统一编码规则

- Java 8、Spring Boot 2.7.18。
- 新增公开 API 类型使用 JavaBean，不使用 `record`、Java 9+ API 或新的重复公共类型。
- 状态修改接口必须携带 `operationId`；任务级修改必须携带 `expectedTaskVersion`。
- 活动任务修改必须使用 `task_status + lock_version` 条件更新，禁止先查后无条件写。
- Repository 负责 SQL，Service/Coordinator 负责身份、权限、幂等、事务和 SPI 编排。
- 审计写入统一通过 `AuditLogWriter`，新增 M5 代码不得继续调用定义 Repository 写审计。
- 非终态控制轨迹如 `CLAIM/UNCLAIM/TRANSFER/REMIND/ALERT_HANDLE` 不得污染“已办节点”查询。
- `candidate_user_ids` 是 JSON 数组，候选人匹配必须精确匹配，不使用 `LIKE '%userId%'`。
- `DelegateProvider` 只负责委托关系来源，平台不持久化委托关系。
- 查询接口统一使用 `PageResult<T>` 和现有分页规范。
- `MessagePublisher` 发送失败不得回滚已提交的提醒记录，只更新发送状态和错误摘要。
- Starter 只装配 core 真实实现，宿主 Bean 必须可覆盖。

## 微任务与编码提示词

### C5.1：对齐 M5 当前基线和跨线边界

目标：

- 核对 M5 执行计划和当前代码中的 DTO、Service、Repository、DDL、Controller。
- 明确 `claim/unclaim` 当前是否仍为 `unsupported`。
- 明确 `process_read_record`、`process_audit_log`、`process_reminder_record`、`process_alert_record`、`process_operation_record` 的字段和约束是否满足 M5。
- 明确 B 线 M5 六个增强动作与 C 线审计、查询、回归的集成点。
- 记录当前 Maven 测试基线。

提示词：

“请只读审查 C 线 M5 执行计划和当前代码。确认 `ProcessRuntimeService.claim/unclaim`、`TaskQueryService.queryReadRecords`、`ProcessMonitorService`、审计查询和告警处理当前实现状态。列出 M5 必须补齐的 Repository、DTO、Controller、Starter 装配和测试缺口。不得实现 B 线 `reject/return/withdraw/directSend/transfer/addSign`，不得新增第二套公共 Service。”

测试与完成条件：

- 形成 M5 缺口清单。
- 明确每个缺口对应的现有表和现有类。
- 明确不属于 M5 的红色基线或跨线依赖。
- 执行并记录 `mvn -q -pl platform/platform-core -am test` 当前结果。

建议提交：`docs: align C M5 baseline gaps`

### C5.2：抽出统一审计写入能力

目标：

- 新增 `AuditLogWriter`、`AuditLogCommand`、`DefaultAuditLogWriter`。
- 新增或补齐 `ProcessAuditLogRepository` 的插入和分页查询。
- 让新增 M5 代码通过统一入口写审计。
- 为 A/B/C 后续动作保留稳定的审计写入边界。

提示词：

“请实现 C 线 M5 统一审计写入组件。新增 `AuditLogWriter.append(AuditLogCommand)` 作为唯一新增审计写入口，底层调用 `ProcessAuditLogRepository` 写 `process_audit_log`。支持目标类型、目标 ID、实例 ID、操作人、动作、时间范围分页查询。新增代码不得继续借用 `ProcessDefinitionRepository.insertAuditLog`。”

实现要点：

- `AuditLogCommand` 包含 `operationId`、`targetType`、`targetId`、`instanceId`、`actionType`、`operatorId`、`operatorName`、`detailJson`。
- 写入前校验必填字段和枚举值，失败关闭。
- 审计与主业务状态修改在同一事务内提交。
- 删除实例或定义后审计默认保留，必要时在 `detailJson` 标记 `targetDeleted=true`。
- 查询排序使用 `created_at DESC, id DESC`。

测试与完成条件：

- 审计插入成功。
- 必填字段缺失时失败清晰。
- 按目标类型、目标 ID、实例 ID、操作人、动作、时间范围过滤正确。
- 分页列表和总数一致。
- 新增 M5 代码只依赖 `AuditLogWriter`。

建议提交：`feat: add unified audit log writer`

### C5.3：实现认领与取消认领协调器

目标：

- 新增 `TaskClaimCoordinator` 和必要的结果映射组件。
- 将 `DefaultProcessRuntimeService.claim/unclaim` 从阶段占位改为委托协调器。
- 支持幂等、任务版本 CAS、权限校验、历史轨迹、审计、回调和最新任务版本返回。

提示词：

“请实现 C 线 M5 的 `claim/unclaim`。在 `DefaultProcessRuntimeService` 中保留唯一公开 Runtime Service，将两个方法委托给 `TaskClaimCoordinator`。`claim` 只允许候选人或有效委托代理人在 `ACTIVE` 任务上认领；`unclaim` 只允许实际认领人在 `CLAIMED` 任务上取消认领。两个动作都必须使用 `operationId` 幂等和 `expectedTaskVersion` CAS，写控制轨迹、审计、回调，并返回最新 `updatedTasks.taskVersion`。”

实现要点：

- 当前用户来自 `CurrentUserProvider`，请求操作人不得伪造。
- `claim` 校验实例 `RUNNING`、任务 `ACTIVE`、候选人或委托权限。
- `unclaim` 校验任务 `CLAIMED`，且 `assignee_user_id` 是当前用户。
- 代理场景允许代理人认领委托人的候选任务，但取消认领必须由实际认领人触发。
- CAS 失败返回并发冲突或明确状态错误，不留下历史、审计或回调。
- 成功后写 `CLAIM/UNCLAIM` 非终态控制轨迹。
- 回调事件建议使用 `TASK_CLAIMED/TASK_UNCLAIMED`；若公共枚举未冻结，先按现有事件模型扩展并补契约测试。

测试与完成条件：

- 候选人可认领，非候选人不可认领。
- 有效委托代理人可认领委托人的候选任务。
- 已认领任务不可再次认领。
- 实际认领人可取消认领，其他人不可取消。
- 相同 `operationId` 重放返回首次结果，不重复写历史、审计、回调。
- 两个不同 `operationId` 使用相同 `expectedTaskVersion` 并发认领时只有一个成功。
- 返回的 `updatedTasks[0].taskVersion` 为最新版本。

建议提交：`feat: implement task claim and unclaim`

### C5.4：收口委托待办查询

目标：

- 在 `DefaultTaskQueryService` 和相关 Repository 中补齐 M5 委托待办语义。
- 合并本人任务、候选任务、委托人任务并去重。
- 保证已认领、转办、加签等动作后的待办可见性稳定。

提示词：

“请收口 C 线 M5 待办查询。通过 `CurrentUserProvider` 获取当前用户，通过 `DelegateProvider` 查询当前用户代理的委托人，合并本人已指派任务、本人候选任务、委托人可代办任务。数据库层统一过滤、排序、去重、分页，返回 `delegateFromUserId/delegateFromUserName` 和最新 `taskVersion`。不得用内存先分页再合并。”

实现要点：

- 直接任务：`assignee_user_id = currentUser`。
- 候选任务：`candidate_user_ids` 精确包含当前用户，且任务未被他人认领。
- 委托任务：当前用户代理的委托人名下可办理任务。
- 同一任务命中多个来源时按 `task_id` 去重，并按来源优先级保留展示信息。
- 已认领给委托人的任务允许代理人看到；已认领给其他人的任务不可作为候选任务展示。
- B 线 `transfer/addSign/reject` 后，C 线待办查询不得出现重复任务或脏来源。

测试与完成条件：

- 本人指派任务可查。
- 本人候选任务可查，候选 JSON 精确匹配。
- 委托代办任务可查并带委托来源。
- 同一任务多来源命中时只返回一条。
- 已认领给他人的候选任务不可见。
- 转办后旧办理人不可见，新办理人可见。
- 加签临时任务按实际办理人可见。

建议提交：`feat: finalize delegated todo query`

### C5.5：实现已读记录写入与查询

目标：

- 新增或补齐已读 DTO、Request、Repository、Manager。
- 支持显式标记已读和分页查询已读记录。
- 可选在实例详情读取时自动标记已读。

提示词：

“请实现 C 线 M5 已读记录能力。新增 `ReadRecordManager` 和 `ProcessReadRecordRepository`，按 `instance_id + user_id` 幂等 upsert 已读时间。提供显式接口 `POST /api/platform/instances/{instanceId}/read` 和查询接口，支持按实例、用户、时间范围分页查询。重复标记不得生成重复记录。”

实现要点：

- 当前用户来自 `CurrentUserProvider`，不信任外部传入的阅读人。
- 写入使用 `INSERT ... ON CONFLICT(instance_id, user_id) DO UPDATE` 或等价 SQLite 兼容写法。
- `read_at` 使用服务端时间或请求中经过校验的时间。
- 查询排序使用 `read_at DESC, id DESC`。
- 首次已读可写审计；若实现成本过高，M5 只保证 upsert 和查询，不重复刷审计。
- 删除实例时已读记录按既有删除策略清理。

测试与完成条件：

- 首次标记生成一条记录。
- 重复标记只更新 `read_at`，不新增记录。
- 多用户同一实例分别有独立记录。
- 按实例、用户、时间范围过滤正确。
- 分页列表和总数一致。
- REST 参数绑定和错误响应稳定。

建议提交：`feat: add process read records`

### C5.6：实现手动催办和提醒查询

目标：

- 实现 `DefaultProcessMonitorService.remindTask`。
- 补齐 `ReminderRecordRepository`、`ReminderMessageFactory` 和提醒查询。
- 支持提醒发送成功/失败状态更新和幂等重放。

提示词：

“请实现 C 线 M5 手动催办。`remindTask` 校验 `operationId/taskId/message/operatorUserId`，读取运行中实例的 `ACTIVE/CLAIMED` 任务，解析提醒目标人，事务内写 `process_reminder_record(PENDING)`、审计和幂等成功结果。事务提交后调用 `MessagePublisher.publish`，发送成功更新 `SENT`，失败更新 `FAILED/error_message`，但不回滚提醒记录。补齐提醒分页查询。”

实现要点：

- 手动催办权限按 v4：当前任务办理人、流程发起人或管理员可触发；如管理员能力未落地，先按当前用户和发起人规则实现，并标注限制。
- 目标人优先任务办理人，无办理人取候选人。
- 委托任务默认不直接推代理人，除非产品明确要求。
- 幂等重放返回首次提醒记录，不重复调用 `MessagePublisher`。
- 消息 payload 包含实例、任务、节点、发起人、提醒人和摘要信息。

测试与完成条件：

- 活动任务可催办。
- 已完成或已取消任务不可催办。
- 无目标人返回明确错误。
- 发送成功后状态为 `SENT`。
- 发送失败后状态为 `FAILED`，主记录保留。
- 相同 `operationId` 重放不重复发消息。
- 提醒查询支持实例、任务、状态、类型、时间分页。

建议提交：`feat: implement manual reminder`

### C5.7：实现告警查询、处理和最小超时扫描

目标：

- 补齐 `AlertRecordRepository` 和告警分页查询。
- 实现 `handleAlert`，支持处理或忽略。
- 实现 M5 最小 `scanTimeoutTasks` 闭环。

提示词：

“请实现 C 线 M5 告警能力和最小超时扫描。`queryAlerts` 支持实例、任务、状态、类型、级别、时间分页；`handleAlert` 只允许 `OPEN -> HANDLED/IGNORED`，写处理人、处理时间、审计和幂等结果；`scanTimeoutTasks` 扫描 `due_at <= now` 且状态为 `ACTIVE/CLAIMED` 的任务，`dryRun=true` 只返回任务，`dryRun=false` 生成一条 `TIMEOUT` 提醒或 `TASK_TIMEOUT` 告警，并避免重复生成。”

实现要点：

- 告警处理权限按 v4：仅管理员；如果当前权限模型缺失，先以明确的可替换 Guard 或当前 Mock 管理员规则实现。
- 已关闭告警重复处理返回幂等结果或明确错误。
- 不删除告警记录。
- 超时扫描支持 `now`、`limit`、`dryRun`、`operatorUserId`。
- M5 不解析复杂节点级提醒/告警策略，只做可验收最小闭环。
- 防重复可按 `task_id + alert_type + 时间窗口` 或现有唯一约束实现。

测试与完成条件：

- 告警分页过滤正确。
- `OPEN` 告警可处理为 `HANDLED`。
- `OPEN` 告警可处理为 `IGNORED`。
- 已关闭告警不可重复产生副作用。
- `dryRun=true` 不写提醒或告警。
- `dryRun=false` 对超时任务生成记录。
- 重复扫描同一时间窗口不重复生成记录。

建议提交：`feat: add alert handling and timeout scan`

### C5.8：补齐 REST 与 Starter 装配

目标：

- 暴露 M5 必要 REST 接口。
- Starter 和独立 REST 应用装配相同的 M5 core 实现。
- 宿主可覆盖关键 SPI 和 Service。

提示词：

“请补齐 C 线 M5 REST 和 Starter 装配。REST 暴露认领、取消认领、已读标记、已读查询、手动催办、提醒查询、超时扫描、告警查询、告警处理、审计查询入口。Controller 只做参数绑定和 Service 调用，不直接访问 Repository。`PlatformAutoConfiguration` 装配 `AuditLogWriter`、`DefaultProcessMonitorService`、新增 Repository 和 M5 查询组件，使用 `@ConditionalOnMissingBean` 保持宿主覆盖能力。”

实现要点：

- 认领接口如已在 `ProcessRuntimeController` 存在，只补测试和 Service 行为。
- 路径以 v4 文档和现有 Controller 为准；执行计划中的路径只作候选。
- Controller 不暴露 SQL、SQLite 路径或内部异常堆栈。
- `flow-mind.platform.enabled=false` 时不装配平台 Bean。
- `flow-mind.platform.mock.enabled=false` 时不装配本地 Mock SPI。
- 独立应用和 Starter 复用同一套 core Bean。

测试与完成条件：

- REST 参数绑定正确。
- Controller 不直接依赖 Repository。
- 默认 Starter 上下文能注入 M5 新增 Service/Writer/Repository。
- 宿主自定义 Bean 可覆盖默认实现。
- Mock 开关行为符合既有规则。

建议提交：`feat: wire M5 rest and starter beans`

### C5.9：跨阶段联调与回归固定

目标：

- 验证 M0-M5 组合能力。
- 验证 B 线 M5 增强动作后 C 线查询、审计、回调展示不被破坏。
- 固定 M5 端到端验收用例。

提示词：

“请做 C 线 M5 跨阶段回归。基于入金申请串行流程和 B 线 M5 增强动作，验证 `claim/unclaim`、委托待办、已读、审计、提醒、告警和回调查询。重点断言 `TRANSFER/CLAIM/UNCLAIM/REMIND/ALERT_HANDLE` 等非终态动作不会污染已办节点查询，待办不会重复展示，审计和回调可追溯。”

测试与完成条件：

- 入金申请串行流程回归通过。
- 认领后待办状态和 `taskVersion` 正确。
- 取消认领后任务恢复候选可见。
- 委托代理人可看到委托待办且无重复。
- 已读记录可写可查。
- 手动催办有提醒记录和消息 Mock 记录。
- 超时扫描可生成提醒或告警。
- 告警可处理或忽略并写审计。
- B 线 `transfer/addSign/reject` 后 C 线待办、已办、审计、回调查询稳定。

建议提交：`test: add C M5 cross stage regression`

### C5.10：文档收口和阶段记录

目标：

- 更新 M5 技术实现说明、阶段完成记录和测试结果文档，生成技术实现说明.md和完成记录测试结果.md。
- 记录实际新增类、接口、表/索引、装配规则、已知限制。
- 明确 M6 留项。

提示词：

“请根据实际代码交付更新 C 线 M5 文档。记录 `AuditLogWriter`、`TaskClaimCoordinator`、`ReadRecordManager`、`DefaultProcessMonitorService`、新增 Repository、REST 路径、Starter 装配和测试结果。说明 B 线增强动作不由 C 线实现，M6 继续处理复杂超时策略、并行汇聚治理、管理员深度修复和告警升级。”

测试与完成条件：

- 文档中的类名、方法名、路径、配置项与代码一致。
- 记录 Maven 测试命令和结果。
- 明确未实现能力和后续阶段归属。
- 阶段完成记录可作为交接材料。

建议提交：`docs: record C M5 delivery`

## 推荐执行顺序

1. C5.1 对齐 M5 当前基线和跨线边界。
2. C5.2 抽出统一审计写入能力。
3. C5.3 实现认领与取消认领协调器。
4. C5.4 收口委托待办查询。
5. C5.5 实现已读记录写入与查询。
6. C5.6 实现手动催办和提醒查询。
7. C5.7 实现告警查询、处理和最小超时扫描。
8. C5.8 补齐 REST 与 Starter 装配。
9. C5.9 跨阶段联调与回归固定。
10. C5.10 文档收口和阶段记录。

## 最小验收清单

- `claim/unclaim` 不再抛阶段未实现异常。
- `claim/unclaim` 使用 `operationId` 幂等和 `expectedTaskVersion` CAS。
- 认领成功后任务为 `CLAIMED`，返回最新 `taskVersion`。
- 取消认领成功后任务恢复 `ACTIVE`，返回最新 `taskVersion`。
- 认领和取消认领写非终态历史轨迹、审计和回调。
- 待办查询稳定展示本人、候选、委托代办任务，且不重复。
- 待办查询返回 `delegateFromUserId/delegateFromUserName` 和最新 `taskVersion`。
- 已读记录按 `instance_id + user_id` 幂等写入。
- 已读记录可按实例、用户、时间范围分页查询。
- `AuditLogWriter` 是新增审计写入统一入口。
- 手动催办可写提醒记录并调用 `MessagePublisher`。
- 提醒发送失败只更新提醒状态，不回滚提醒记录。
- 提醒记录可分页查询。
- 告警可分页查询。
- 告警可处理或忽略，并写审计。
- 最小超时扫描可 dry-run，也可生成提醒或告警。
- 重复超时扫描不会无限重复发送。
- REST 与 Starter 均能使用 M5 新增能力。
- B 线增强动作后的 C 线待办、已办、审计、回调查询回归通过。

## 建议验证命令

```powershell
mvn -q -pl platform/platform-core -am test
mvn -q -pl platform/platform-starter -am -DfailIfNoTests=false test
mvn -q test
```
