# 实习生 C：M3 查询能力与 Starter 自动装配 Vibe Coding 计划

## 产物与边界

本文件作为实习生 C 在 M3 阶段的专项编码计划。M3 只实现查询读侧能力、REST 查询适配和 Starter 真实自动装配：待办、已办、我发起、活动任务、历史任务、审批意见，以及查询所需的 Repository、Assembler、Controller 和 Starter Bean。

M3 不实现以下内容：

- A 线定义生命周期写动作：发布、激活、停用、归档、灰度发布。
- B 线运行时主状态机：启动、提交、审批、节点推进、终止、删除、跳转、强制办结。
- M4 附件保存、下载、删除、权限校验和必填附件校验。
- M5 已阅、审计写入、委托认领动作、催办提醒。
- M6 超时扫描、告警、并行修复。

查询代码不得补写流程状态，不得为了展示结果修改 `process_instance`、`process_active_task`、`process_history_task`、`process_operation_record`、`process_callback_log` 或 `process_audit_log`。每个微任务完成后执行对应测试，并做一次只读自审。

## 基线优先级

发生描述差异时按以下优先级执行：

1. 当前代码中的公共 DTO、Request、Service、SPI、DDL。
2. `doc/实习生C/M3/执行计划.md`。
3. `doc/流程平台设计与接口文档_v4.md`。
4. `doc/流程平台技术路线_v5.2.md`。
5. `doc/实习生C/实习生C开发文档.md`。
6. `doc/rebuild-functional-requirements-optimized.md`。

当前代码中已经存在并必须复用：

- `TaskQueryService`
- `DefaultTaskQueryService`
- `ProcessRuntimeService.getInstance`
- `CallbackService.queryCallbackLogs`
- `PageQueryNormalizer`
- `ProcessHistoryTaskRepository`
- `ActiveTaskRepository`
- `ProcessInstanceRepository`
- `ProcessTraceAssembler`
- `CurrentUserProvider`
- `DelegateProvider`

## 目标结构

目标结构优先沿用现有包，不新增第二套查询 Service 或与 Starter 绑定的专用实现。

```text
platform-core/src/main/java/com/flowmind/platform
├── api/
│   └── dto/                         # 补齐查询 DTO 和必要列表展示字段
├── core/
│   └── query/
│       ├── DefaultTaskQueryService.java
│       ├── ProcessTraceAssembler.java
│       ├── RuntimeQueryAssembler.java        # 可选，统一列表映射
│       └── PageQueryNormalizer.java
├── persistence/
│   └── repository/
│       ├── ActiveTaskRepository.java         # 补待办/活动任务只读查询
│       ├── ProcessInstanceRepository.java    # 补我发起分页查询
│       └── ProcessHistoryTaskRepository.java # 增强已办过滤
└── web/
    └── PlatformQueryWebController.java       # 如确认 core 承载 REST

platform-starter/src/main/java/com/flowmind/platform/starter
├── PlatformAutoConfiguration.java
└── properties/PlatformProperties.java

platform-core/src/test/java/com/flowmind/platform
├── core/query/
├── persistence/repository/
├── web/
└── integration/

platform-starter/src/test/java/com/flowmind/platform/starter
```

推荐数据流：

```text
REST / Starter 调用
  -> TaskQueryService
  -> CurrentUserProvider 获取可信用户
  -> DelegateProvider 按当前代理人获取有效委托关系
  -> Repository 只读 process_active_task / process_instance / process_history_task
  -> Assembler 映射 TaskDTO / HistoryTaskDTO / ProcessInstanceDTO
  -> PageResult 返回调用方
```

待办查询数据流：

```text
当前用户
  -> 直接受理任务 assignee_user_id = currentUser
  -> 候选任务 candidate_user_ids JSON 精确包含 currentUser
  -> 委托任务：当前用户作为代理人时，DelegateProvider 返回的委托人名下可代办任务
  -> 数据库层合并、去重、过滤、排序、分页
  -> TaskDTO.taskVersion = process_active_task.lock_version
```

## 统一编码规则

- Java 8、Spring Boot 2.7.18。
- 新增公开 API 类型使用 JavaBean，不使用 `record`、Java 9+ API 或新的重复公共类型。
- Repository 负责 SQL 访问，Service 负责身份、委托、分页和查询语义编排。
- Controller 只负责 HTTP 参数绑定和异常转换，不直接访问 Repository。
- DTO 面向调用方，Entity 面向数据库，不直接互相混用。
- JSON 字段必须通过 Jackson 或 SQLite JSON 能力处理，不手写字符串拼接。
- `candidate_user_ids` 是 JSON 数组，候选人匹配必须精确匹配，不使用 `LIKE '%userId%'`。
- `CurrentUserProvider` 是查询当前用户的可信来源，外部请求参数不得覆盖当前登录用户。
- `DelegateProvider` 只用于获取宿主提供的委托关系；M3 待办需要支持“当前代理人 -> 有效委托人列表”的查询语义。
- 所有分页接口统一使用 `PageResult<T>` 和 `PageQueryNormalizer`。
- 动态排序字段必须使用白名单，不拼接用户输入。
- 查询链路不写流程状态、不写历史、不写回调、不写幂等。
- Starter 只能装配 core 的真实查询实现，不维护第二套 Starter 专用查询实现。

## 微任务与编码提示词

### C3.1：对齐 M3 查询缺口和 DTO 字段

目标：

- 核对 `TaskQueryService` 中 M3 要实现的方法。
- 明确 `TodoTaskQuery`、`CompletedTaskQuery`、`StartedInstanceQuery` 需要补齐的过滤字段。
- 明确 `TaskDTO` 是否需要补充流程编码、流程名称、实例标题、发起人等列表展示字段。
- 明确 `DelegateProvider` 是否支持按当前代理人查询有效委托关系。
- 记录 `queryReadRecords` 继续留到 M5，不在 M3 伪造实现。

提示词：

“请只读审查 C 线 M3 执行计划和当前代码。确认 `DefaultTaskQueryService` 仍缺 `queryTodoTasks`、`queryStartedInstances`、`queryActiveTasks`，确认已办查询只支持 `userId/processCode`。确认 M3 查询层通过 `CurrentUserProvider` 获取可信当前用户，并确认 `DelegateProvider` 是否支持按当前代理人查询有效委托关系。列出 M3 需要补齐的 Query DTO 字段和 `TaskDTO` 展示字段，不创建重复 DTO，不实现 M4/M5/M6 能力。”

测试与完成条件：

- 形成字段增量清单。
- 明确哪些字段用于过滤，哪些字段只用于展示。
- 明确待办查询中的委托关系查询方向。
- 若修改 DTO，同步更新 DTO 测试。
- 执行 `mvn -q -pl platform/platform-core -am test`。

建议提交：`docs: align C M3 query field gaps`

### C3.2：补齐查询 DTO 和列表展示字段

目标：

- 扩展 `TodoTaskQuery`、`CompletedTaskQuery`、`StartedInstanceQuery` 的过滤能力。
- 必要时扩展 `TaskDTO` 的流程和实例展示字段。
- 保持 JavaBean 风格、中文 Javadoc 和既有命名习惯。

提示词：

“请补齐 C 线 M3 查询 DTO。`TodoTaskQuery` 支持流程编码/名称、实例标题、发起人、节点编码、任务状态、时间范围和排序选项；`CompletedTaskQuery` 支持流程、标题、发起人、节点、动作类型、完成时间范围；`StartedInstanceQuery` 支持标题、业务键、状态、当前节点、发起时间范围。必要时给 `TaskDTO` 补充列表展示字段。不得新增重复查询 DTO。”

实现要点：

- 时间范围字段使用 `LocalDateTime`。
- 状态和动作优先使用既有枚举；没有合适枚举时先用字符串并限制白名单。
- 空字段表示不过滤。
- 外部用户 ID 参数不得覆盖 `CurrentUserProvider` 的可信身份。

测试与完成条件：

- DTO 字段有中文 Javadoc。
- DTO 契约/字段测试通过。
- 不影响已有 M0-M2 DTO 测试。

建议提交：`feat: extend query dto fields for M3`

### C3.3：实现待办查询 Repository

目标：

- 支持本人待办、候选待办、委托代办三类来源。
- 数据库层统一合并、去重、过滤、排序和分页。
- 返回总数与列表使用同一组过滤条件。

提示词：

“请为 M3 待办查询补齐 Repository 只读方法。基于 `process_active_task` 关联 `process_instance`、`process_node`，查询 `assignee_user_id = 当前用户`、`candidate_user_ids` JSON 精确包含当前用户、当前用户作为代理人时委托人名下可代办任务三类来源。使用 CTE 或等价 SQL 在数据库层合并、去重、过滤、排序、分页。禁止用 `LIKE` 匹配 JSON 候选人，禁止先分别分页再内存合并。”

实现要点：

- 开放任务状态为 `ACTIVE`、`CLAIMED`。
- 候选任务仅允许 `task_status = ACTIVE`。
- 同一任务命中多来源时按 `task_id` 去重。
- 排序使用 `due_at`、`created_at`、`id` 稳定排序。
- `lock_version` 必须映射为 `TaskDTO.taskVersion`。

测试与完成条件：

- 直接受理任务可查。
- 候选人 JSON 精确匹配，`u1` 不匹配 `u10`。
- 已被他人认领的候选任务不出现在候选待办。
- 委托任务带 `delegateFromUserId/delegateFromUserName`，且委托人 ID 来自按当前代理人查询到的有效委托关系。
- 列表和总数一致。

建议提交：`feat: add todo task query repository`

### C3.4：实现我发起和活动任务查询 Repository

目标：

- 为 `queryStartedInstances` 提供分页查询和总数。
- 为 `queryActiveTasks(instanceId)` 提供实例内开放任务查询。
- 补齐实例、节点、候选人和版本映射所需字段。

提示词：

“请补齐 M3 的实例查询只读能力。`ProcessInstanceRepository` 增加我发起分页查询和 count，支持流程编码、标题、业务键、实例状态、当前节点、发起时间范围。`ActiveTaskRepository` 增加实例活动任务查询，只返回 `ACTIVE/CLAIMED`，按创建时间和 ID 稳定排序，并关联节点名称。”

实现要点：

- 我发起查询以 `CurrentUserProvider` 的用户 ID 为准。
- `current_node_codes` 是 JSON/文本快照，过滤当前节点时不得误匹配短编码。
- 活动任务不返回 `COMPLETED/CANCELED`。
- 为空结果时返回空 `records` 或空列表，不返回 `null`。

测试与完成条件：

- 我发起列表可按状态、业务键、标题、时间过滤。
- 非当前用户发起的实例不可见。
- 活动任务只返回开放任务。
- 排序稳定。

建议提交：`feat: add started instance and active task queries`

### C3.5：增强已办、历史任务和审批意见查询

目标：

- 保留已有历史任务与审批意见查询。
- 增强已办分页过滤和展示字段。
- 不新增 `process_comment` 或 `process_trace` 表。

提示词：

“请增强 C 线 M3 已办查询。复用 `ProcessHistoryTaskRepository` 和 `ProcessTraceAssembler`，从 `process_history_task` 查询当前用户已办任务，关联 `process_instance` 和 `process_node` 补充流程、标题、节点展示。审批意见继续来自 `comment_text` 非空历史记录，不新增评论表或轨迹表。”

实现要点：

- 已办默认按可信当前用户过滤办理人。
- 支持流程、标题、发起人、节点、动作类型、完成时间范围。
- 历史轨迹顺序保持 `started_at ASC, completed_at ASC, id ASC`。
- 审批意见顺序与历史轨迹一致。

测试与完成条件：

- 已办分页和总数一致。
- 空意见不生成 `ProcessCommentDTO`。
- 委托来源、任务组、分支字段保留。
- 旧的 M2 历史/意见测试继续通过。

建议提交：`feat: enhance completed task query`

### C3.6：完成 `DefaultTaskQueryService`

目标：

- 实现 `queryTodoTasks`、`queryStartedInstances`、`queryActiveTasks`。
- 保留并回归 `queryCompletedTasks`、`queryHistoryTasks`、`queryComments`。
- 统一身份、委托、分页、异常处理。

提示词：

“请完成 `DefaultTaskQueryService` 的 M3 查询方法。通过 `CurrentUserProvider` 获取可信当前用户，通过 `DelegateProvider` 按当前代理人获取有效委托关系，调用查询 Repository 返回 `PageResult`。保留已有历史任务和审批意见行为。`queryReadRecords` 继续显式标记为 M5 未实现，不返回假数据。”

实现要点：

- 查询条件为 `null` 时创建默认 Query。
- 所有分页使用 `PageQueryNormalizer`。
- 外部传入用户 ID 与可信用户不一致时采用统一策略。
- `PageResult.records` 不为 `null`。
- 不在 Service 中拼 SQL。

测试与完成条件：

- 待办、已办、我发起均返回 `PageResult`。
- 活动任务、历史任务、意见返回稳定列表。
- 重复查询不改变任何表数据。
- SPI 缺失或异常时错误清晰。

建议提交：`feat: complete default task query service`

### C3.7：实现 REST 查询适配

目标：

- 暴露 M3 必做 GET 查询接口。
- 参数绑定到已有 Query DTO。
- 复用 Service，不直接访问 Repository。

提示词：

“请新增 M3 查询 REST Controller。实现 `/api/platform/tasks/todo`、`/api/platform/tasks/completed`、`/api/platform/instances/started`、`/api/platform/instances/{instanceId}/active-tasks`、`/api/platform/instances/{instanceId}/history-tasks`、`/api/platform/instances/{instanceId}/comments`。Controller 只做参数绑定和 Service 调用，不写 SQL，不做状态修改。”

实现要点：

- 若 `platform-core` 承载 REST，需要先补 Spring Web MVC 依赖。
- Controller 放在 `com.flowmind.platform.web`。
- 异常转换不得暴露 SQL、SQLite 路径或文件系统细节。
- 可选暴露 `GET /api/platform/instances/{instanceId}` 委托 `ProcessRuntimeService.getInstance`。

测试与完成条件：

- MVC 测试覆盖路径、参数绑定、默认分页。
- Service 被正确调用。
- 错误响应稳定。
- REST 层无 Repository 依赖。

建议提交：`feat: add M3 query rest endpoints`

### C3.8：Starter 接入真实查询 Service

目标：

- Starter 默认装配真实 `DefaultTaskQueryService`。
- 接通 `DataSource`、`JdbcTemplate`、Repository、Assembler 和 Schema 初始化。
- 保持宿主 Bean 覆盖能力。

提示词：

“请改造 `platform-starter` 的自动装配，使默认 `TaskQueryService` 使用 core 中真实 `DefaultTaskQueryService`，不再返回 `UnsupportedTaskQueryService`。宿主提供 `TaskQueryService`、`DataSource`、`JdbcTemplate` 或 SPI 时必须让位。未提供 DataSource 时根据 `flow-mind.platform.sqlite.path` 创建 SQLite 数据源，并按顺序初始化 schema。”

实现要点：

- `flow-mind.platform.enabled=false` 时不装配平台 Bean。
- `flow-mind.platform.mock.enabled=false` 时不装配 Mock SPI。
- Schema 初始化按脚本版本顺序执行。
- Starter 不复制查询业务逻辑。
- 保留宿主自定义 Service 覆盖测试。

测试与完成条件：

- 默认上下文能注入真实 `TaskQueryService`。
- 临时 SQLite 中插入夹具后能查询待办/已办/我发起。
- 自定义 `TaskQueryService` 覆盖默认 Bean。
- 自定义 `DataSource` 覆盖默认 SQLite。
- Mock SPI 开关行为符合预期。

建议提交：`feat: wire starter to real query service`

### C3.9：A/B 联调与回归固定

目标：

- 使用 A 的可发布定义和 B 的串行运行时闭环验证查询。
- 固定 M3 端到端验收用例。
- 记录阶段完成情况。

提示词：

“请基于 A/B 已有能力做 C 线 M3 联调。构造入金申请串行流程，从发起、提交、审批到办结，验证待办减少、已办增加、我发起状态更新、活动任务清空、历史任务和审批意见完整。不得修改 A/B 主状态机，只补 C 查询侧需要的测试夹具和断言。”

测试与完成条件：

- 串行流程办结后无活动任务。
- 已办包含提交和审批历史。
- 我发起实例状态变为 `COMPLETED`。
- 历史轨迹和意见可查。
- `mvn -q test` 在 `platform` 目录通过；必要时再执行仓库根目录 `mvn -q test`。

建议提交：`test: add M3 query integration coverage`

## 验收清单

- `TaskQueryService.queryTodoTasks` 可用。
- `TaskQueryService.queryCompletedTasks` 支持 M3 过滤并保持 M2 历史行为。
- `TaskQueryService.queryStartedInstances` 可用。
- `TaskQueryService.queryActiveTasks` 可用。
- `queryHistoryTasks` 和 `queryComments` 回归通过。
- 候选人查询精确匹配 `candidate_user_ids` JSON 数组。
- 待办区分本人、候选、委托来源，并返回最新 `taskVersion`。
- REST 查询接口与 Service 行为一致。
- Starter 默认装配真实查询能力，宿主可覆盖。
- 查询链路不写状态、不写历史、不写回调、不写幂等。

## 建议提交顺序

1. `docs: align C M3 query field gaps`
2. `feat: extend query dto fields for M3`
3. `feat: add todo task query repository`
4. `feat: add started instance and active task queries`
5. `feat: enhance completed task query`
6. `feat: complete default task query service`
7. `feat: add M3 query rest endpoints`
8. `feat: wire starter to real query service`
9. `test: add M3 query integration coverage`
