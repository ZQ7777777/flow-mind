# 实习生 B：M5 增强动作编码计划

## 1. 计划基线

本计划基于当前 `develop@809d4c5` 和现有文档制定，目标是在不重写 M0～M4 已有运行时底座的前提下，补齐 B 线负责的常用增强动作。

参考依据：

- `doc/流程平台技术路线_v5.2.md`
- `doc/流程平台设计与接口文档_v4.md`
- `doc/rebuild-functional-requirements-optimized.md`
- `doc/xyx（实习生A）/实习生A_完整技术文档.md`
- `doc/实习生B/实习生B_M0至当前工作总结.md`
- 当前 `platform-core`、`platform-starter` 代码和测试

当前代码事实：

1. M4 附件运行时已经合入，`DefaultAttachmentService`、附件 Repository、权限 Guard、文件存储补偿和 Runtime 附件接入不属于 M5 重复编码范围。
2. `ProcessRuntimeService` 已冻结 `reject`、`returnToStarter`、`withdraw`、`directSend`、`transfer`、`addSign`、`claim`、`unclaim` 方法。
3. 对应 Request、`ActionTypeEnum`、`WorkflowEventTypeEnum`、REST Controller 入口、数据库动作约束均已存在，不新增同义接口、DTO、枚举或 Controller。
4. `DefaultProcessRuntimeService` 中六个 B 线增强动作仍抛出阶段未实现异常；`claim/unclaim` 也未实现，但技术路线将其分配给 C 线。
5. `ActiveTaskRepository` 已具备 `complete`、`cancel`、`transfer`、`claim`、`unclaim` CAS 原语；M5 只补确实缺少的查询或原子更新，不新建平行任务仓储。
6. `RuntimeOperationExecutor`、`RuntimeRequestHasher`、`RuntimeTransactionExecutor`、`RuntimeStateValidator`、`RuntimeNodeAdvancer`、`HistoryTaskWriter`、回调 Outbox 和实例固化定义加载已经可复用。
7. `process_history_task.extra_json`、动作枚举、任务组及父任务组字段已经预留，可承载驳回来源、直送关联和加签恢复上下文；默认方案不新增数据库表和业务列。
8. 当前 `platform` 下 `mvn -q test` 的 `platform-core` 测试已运行，最终在 `platform-starter` 出现 11 个错误：测试和 `spring.factories` 引用了源码中不存在的 `PlatformKnife4jAutoConfiguration`。该问题与 M5 动作无关，但必须作为开工基线问题单独修复，不能把红色基线计入 M5 交付。

## 2. M5-B 范围

### 2.1 本计划实现

B 线在 M5 一次补齐以下六个已经公开但尚未实现的方法：

- 驳回 `reject`
- 退回发起人/首节点 `returnToStarter`
- 撤回 `withdraw`
- 直送 `directSend`
- 转办 `transfer`
- 加签 `addSign`

同时完成：

- 每个动作的幂等、任务版本 CAS、事务、历史轨迹、回调和确定性失败记录；
- 驳回与直送的可信来源关联；
- 撤回的上一历史节点定位和权限校验；
- 转办前后办理人快照；
- 加签任务创建、全部完成后恢复原任务；
- 普通 `approve` 对“加签临时任务”的专用收口分支；
- 与 A 线规则持久化、C 线审计/查询/回归测试的集成。

### 2.2 明确不实现

- `claim/unclaim`：由 C 线实现，B 不因已有 Repository 方法而接管其 Service 编排。
- 委托代办、已阅、提醒和审计查询：由 C 线负责。
- 节点监听、条件表达式及配置的通用保存/发布链路：由 A 线负责；驳回/直送规则的 JSON 命名空间、读取器和业务校验由 B 线负责。
- 通用 `OR_SIGN/COUNTERSIGN` 节点运行、嵌套并行、并行跨分支回退：留到 M6。
- 新增另一套 Runtime Service、历史任务 Repository、回调发布器、REST Controller 或 Starter 假实现。
- 重做附件保存、必填校验、权限和文件补偿。
- 修改已经冻结的公共方法签名；仅给 `TaskActionResult` 增加向后兼容的可选 `updatedTasks` 字段。

## 3. 直接复用与最小增量

| 能力 | 当前代码 | M5 用法 |
| --- | --- | --- |
| 公共入口 | `ProcessRuntimeService`、`DefaultProcessRuntimeService` | 保留唯一公开实现，六个占位方法改为委托增强动作协调器 |
| 身份与基础权限 | `RuntimeRequestValidator`、`RuntimeStateValidator` | 扩展动作级校验；普通动作继续校验当前任务权限，撤回使用独立权限规则 |
| 幂等 | `RuntimeOperationExecutor`、`RuntimeRequestHasher` | 使用冻结 `operationId` 和请求哈希；同号同请求重放，同号不同请求拒绝 |
| 事务 | `RuntimeTransactionExecutor` | CAS、历史、任务组、后续任务、审计、回调和成功结果同事务提交 |
| 任务并发 | `ActiveTaskRepository` | 路由类动作复用 `complete/cancel`，转办复用 `transfer` |
| 节点推进 | `RuntimeNodeAdvancer` | 驳回、退回、撤回、直送只推进到已经校验的 `USER_TASK` |
| 定义快照 | `RuntimeDefinitionLoader`、`DefinitionGraphIndex` | 始终按实例的固化 `definitionId` 取图，不按 `processCode` 重新选版 |
| 历史 | `HistoryTaskWriter`、`ProcessHistoryTaskRepository` | 使用现有 `HistoryArchiveCommand.extraJson`；只在现有 Repository 增加历史定位查询 |
| 任务组 | `TaskGroupRepository`、`process_task_group` | 加签临时组复用计数、状态、锁版本和父组上下文，不实现通用会签 |
| 目标用户 | `OrganizationProvider.findUser` | 转办、加签在任何任务 CAS 前解析用户并固化名称 |
| 回调 | `CallbackService`、已有事件枚举 | 扩展现有事件映射，不创建第二套事件工厂 |
| 审计 | C 线 M5 的统一审计 Writer；未合入前暂以接口依赖表示 | B 只调用统一写入能力，不继续借用定义仓储复制 SQL |
| REST | `ProcessRuntimeController` | 入口已存在，只补 Service 行为和 Controller 契约测试 |

注意：当前同时存在 `HistoryTaskRepository` 和 `ProcessHistoryTaskRepository`。M5 不再创建第三个历史仓储；新增“上一节点、驳回来源、加签来源”等查询统一加到具备完整行映射和幂等查询的 `ProcessHistoryTaskRepository`。是否在本阶段彻底合并两个旧 Repository，不作为功能前置条件，避免扩大改动。

## 4. 内部设计

### 4.1 增强动作协调器

新增内部 `EnhancedTaskActionCoordinator`，承载六个增强动作的业务编排，避免继续扩大当前约 1200 行的 `DefaultProcessRuntimeService`。

边界：

- `DefaultProcessRuntimeService` 仍是唯一 `ProcessRuntimeService` Bean。
- 增强动作协调器不是新的公共 Service，不改变 Starter/API 契约。
- 普通启动、提交、审批、终止、删除仍留在现有实现。
- `approve` 在读取任务组后，仅把标记为“加签临时组”的任务交给协调器收口；普通审批继续走现有主链路。

公共执行顺序：

1. 校验请求必填项和可信当前用户。
2. 调用 `RuntimeOperationExecutor.begin` 建立幂等租约。
3. 成功重放直接反序列化首次 `TaskActionResult`，不得再次查询已经终态的任务。
4. 在状态修改前加载任务、实例、固化定义、历史上下文和 B 线动作规则。
5. 所有外部用户解析和目标节点 `prepareAdvance` 在任务 CAS 前完成。
6. 在统一事务内执行任务 CAS、任务组更新、历史/审计、任务创建、实例当前节点刷新、回调 Outbox 和幂等成功结果。
7. 确定性校验或状态失败写入幂等失败码；数据库或进程异常保留可接管的处理中记录。

### 4.2 内部动作类型和错误码

在 `RuntimeOperationTypes` 中为六个动作增加名称常量，值直接复用现有动作名：

- `REJECT`
- `RETURN`
- `WITHDRAW`
- `DIRECT_SEND`
- `TRANSFER`
- `ADD_SIGN`

`process_operation_record` 的 SQLite CHECK 已包含这些值，不新增迁移。

补充稳定错误码：

- `FLOW_REJECT_TARGET_NOT_ALLOWED`
- `FLOW_WITHDRAW_HISTORY_NOT_FOUND`
- `FLOW_WITHDRAW_PERMISSION_DENIED`
- `FLOW_DIRECT_SEND_SOURCE_NOT_FOUND`
- `FLOW_TARGET_USER_NOT_FOUND`
- `FLOW_ADD_SIGN_CONTEXT_INVALID`
- `FLOW_GROUPED_TASK_ACTION_NOT_SUPPORTED`

不要把所有业务拒绝继续压成 `FLOW_INVALID_ACTION`，否则 REST、调用方和回归测试无法区分参数错误、权限错误、来源缺失和并行组合不支持。

### 4.3 历史扩展 JSON

M5 使用已有 `process_history_task.extra_json` 保存动作关系，统一带 `schemaVersion=1`。结构如下：

```json
{
  "schemaVersion": 1,
  "sourceNodeCode": "managerApprove",
  "targetNodeCode": "apply",
  "sourceTaskId": "task-manager",
  "createdTaskIds": ["task-rework"],
  "relatedHistoryTaskId": "history-reject",
  "operatorUserId": "user-001",
  "fromAssigneeUserId": "user-001",
  "targetUserIds": ["user-002"],
  "addSignGroupId": "group-add-sign"
}
```

各动作只写与自身相关的键，不写空占位。JSON 由一个内部 Mapper 统一读写，禁止六个方法分别手拼字符串。

设计收益：

- 驳回历史记录可反向绑定它创建的返工任务；
- 直送必须以当前任务 ID 命中对应驳回历史，不能相信调用方随意传入的目标；
- 撤回可记录被恢复的上一条历史；
- 转办可保留转办前后人员；
- 加签可关联临时任务组和新增任务；
- 无需给活动任务或历史表新增专用列。

### 4.4 任务组组合边界

冻结边界：

- `transfer` 可作用于普通任务、并行分支任务和加签临时任务，因为它只修改单个任务办理人，不推进分支。
- `reject`、`returnToStarter`、`withdraw`、`directSend` 在 M5 只允许没有 `taskGroupId/branchKey` 的串行 `SINGLE` 任务。
- `addSign` 在 M5 只允许普通串行任务，不允许在并行、或签、会签或已有加签临时任务上再次加签。
- 不支持的组合显式返回 `FLOW_GROUPED_TASK_ACTION_NOT_SUPPORTED`，不得只更新单条任务后留下活动任务组。

这样不会在 M5 偷跑 M6 的跨分支回退、通用会签和嵌套任务组语义。若产品要求增强动作必须覆盖并行任务，应单独扩展 M6 任务组状态机后再开放。

## 5. 分步编码任务

### M5-B.0：恢复并冻结测试基线

1. 由当前 Starter/C 线负责人修复缺失的 `PlatformKnife4jAutoConfiguration` 或移除失效引用。
2. 在独立提交中恢复 `platform` 下 `mvn -q test` 全绿。
3. 记录 M5 开工基线提交号和测试数。
4. 基线修复不得夹带增强动作代码，避免把既有问题误计为 B 的 M5 回归。

门禁：基线未恢复前可以写 M5 单元测试和代码，但不得宣布 M5 全量测试通过。

### M5-B.1：冻结跨线契约和内部上下文

1. B 线在现有 `listenerConfig` 中冻结唯一命名空间 `taskActionRules`，并提供读取和目标合法性校验组件：
   - 是否允许驳回；
   - 允许的目标范围或节点编码；
   - 是否允许直送；
   - 直送目标只能来自可信驳回来源；
   - 结构固定为 `taskActionRules.reject.enabled`、`taskActionRules.reject.targetNodeCodes`、`taskActionRules.directSend.enabled` 和固定值 `taskActionRules.directSend.targetMode=REJECT_SOURCE`。
2. C 线提供统一审计写入组件；B 不再直接通过 `ProcessDefinitionRepository.insertAuditLog` 扩散定义仓储职责。
3. B 定义内部、非 API 的：
   - `EnhancedActionContext`：实例、任务、定义、节点、操作者、变量；
   - `EnhancedActionHistoryMetadata`：统一 `extra_json` 映射；
   - `AddSignGroupContext`：加签来源任务快照和父分支上下文。
4. 为新增错误码、内部动作类型、历史 JSON 兼容性补契约测试。

门禁：公共 Request、Service、枚举和 Controller 不重复创建；A 线继续复用通用配置保存/发布链路，不另建规则字段；C 依赖尚未合入时使用明确接口或测试桩，不复制其最终实现。

### M5-B.2：补历史和任务持久化原语

只扩展现有 Repository：

- `ProcessHistoryTaskRepository`
  - 按实例倒序查找可恢复的上一条“终态任务历史”；
  - 查找发起/首节点的 `SEND` 历史；
  - 按当前活动任务 ID 查找尚未消费的驳回来源；
  - 查询时按 `completed_at DESC, id DESC` 稳定排序；
  - 读取和校验 `extra_json.schemaVersion`。
- `ActiveTaskRepository`
  - 按任务组稳定读取开放任务；
  - 如现有 `transfer` 返回行数已满足需求则直接复用，不新增同义方法；
  - 加签恢复原任务仍复用现有 `insert`。
- `TaskGroupRepository`
  - 继续复用 `insert/findById/incrementCompletedCount/cancel`；
  - 仅在确有必要时增加“按组类型和 purpose 校验”的只读方法，不复制计数 CAS。

历史选择必须排除仅用于动作留痕、但未代表节点完成的记录，例如 `TRANSFER`、`CLAIM`、`UNCLAIM` 和 `ADD_SIGN` 的控制记录；否则撤回可能恢复到错误节点。

### M5-B.3：实现驳回和退回

#### 驳回 `reject`

执行规则：

1. 使用公共任务身份、运行态、权限和版本校验。
2. 要求 `targetNodeCode` 非空。
3. 从实例固化定义读取目标节点，目标必须为 `USER_TASK`。
4. 调用 B 线 `taskActionRules` 读取器，校验目标属于当前节点允许的驳回范围。
5. 在任何写入前调用 `RuntimeNodeAdvancer.prepareAdvance` 解析目标审批人。
6. 事务内以 `expectedTaskVersion` 完成当前任务 CAS。
7. 调用 `advanceToNode` 创建目标任务。
8. 使用 `REJECT` 归档当前任务，`extra_json` 写入来源节点、目标节点和实际创建任务 ID。
9. 写任务级审计、`PROCESS_REJECTED`、每个 `TASK_CREATED` 回调和幂等成功结果。

驳回只创建目标用户任务，不沿该目标的出线继续推进。

#### 退回 `returnToStarter`

执行规则：

1. 公共任务校验后，从历史中定位本实例实际经过的发起/首节点 `SEND` 记录。
2. 目标节点必须仍属于实例固化定义并为 `USER_TASK`。
3. 当前任务完成 CAS，以 `RETURN` 归档。
4. 复用目标节点推进器重新创建发起/首节点任务。
5. 历史扩展信息记录目标历史 ID，触发 `PROCESS_RETURNED` 和任务创建回调。

不通过遍历最新流程定义猜测首节点，避免条件分支下恢复到实例从未经过的路径。

### M5-B.4：实现撤回和直送

#### 撤回 `withdraw`

撤回与普通办理权限不同，不能直接复用“当前任务候选人/办理人”校验。

冻结规则：

1. `taskId` 表示将被取消的当前下游活动任务，`expectedTaskVersion` 对应该任务。
2. 实例必须只有这一条开放任务，且任务不属于活动任务组。
3. 从历史中找到紧邻当前任务之前的上一条终态任务历史。
4. 当前操作人必须是该历史任务的实际办理人；这表示“谁发送到当前节点，谁可以撤回”。
5. 在写入前预解析上一历史节点审批人。
6. 事务内取消当前任务 CAS，以 `WITHDRAW` 归档被取消任务；历史的办理人字段保留被取消任务原办理人，撤回操作人写入 `extra_json` 和审计。
7. 重新创建上一历史节点任务，写 `PROCESS_WITHDRAWN`、任务创建回调和成功结果。

撤回不删除原历史；完整轨迹应表现为“上一节点已办理 → 下游任务被撤回 → 上一节点重新创建”。

#### 直送 `directSend`

执行规则：

1. 当前操作者必须有当前返工任务办理权限。
2. 使用当前 `taskId` 查找创建它的未消费 `REJECT` 历史。
3. `DirectSendRequest.targetNodeCode` 必须等于该驳回历史的 `sourceNodeCode`；不一致时拒绝。
4. B 线 `taskActionRules.directSend.enabled` 必须允许直送，且 `targetMode` 必须为 `REJECT_SOURCE`。
5. 在写入前预解析直送目标审批人。
6. 事务内完成当前返工任务 CAS，以 `DIRECT_SEND` 归档，创建原驳回来源节点任务。
7. `extra_json` 记录对应驳回历史 ID；写 `PROCESS_DIRECT_SENT`、任务创建回调和成功结果。
8. 同一驳回来源只能被消费一次；依靠当前任务终态 CAS和历史关系共同保证。

直送不能只相信请求中的 `targetNodeCode`，否则它会退化为普通用户可调用的任意节点跳转。

### M5-B.5：实现转办

执行规则：

1. 复用公共任务身份、运行态、权限和版本校验。
2. `targetUserId` 必填，默认拒绝转给当前实际办理人。
3. 在任务 CAS 前通过 `OrganizationProvider.findUser` 校验目标用户存在，并取得用户名称快照。
4. 事务内调用现有 `ActiveTaskRepository.transfer`，将目标用户写为唯一办理人并增加 `lock_version`。
5. 写一条 `TRANSFER` 动作历史或审计轨迹，记录转办人、原办理人/候选人、目标用户和任务新版本。
6. 触发 `TASK_TRANSFERRED` 回调，不创建后续节点，不修改实例流程位置。
7. 同一任务上的审批、撤回、认领和转办并发时只能有一个 CAS 成功。

保留现有 `transfer` 的任务状态语义：`ACTIVE` 仍为 `ACTIVE`，`CLAIMED` 仍为 `CLAIMED`；由于 `assignee_user_id` 已非空，现有权限校验只允许目标用户办理。

转办成功后任务版本从 `N` 变为 `N+1`。给 `TaskActionResult` 增加可选 `updatedTasks` 字段返回更新后的当前任务，避免调用方必须额外查询；旧字段和旧 JSON 均保持兼容。

### M5-B.6：实现加签和加签任务收口

采用“并行加签、不区分前后”：暂停原任务，为所有新增签署人同时创建待办，全部完成后恢复原任务；M5 不提供前加签/后加签模式参数。

1. 当前任务办理人提交去重后的 `addSignUserIds`。
2. 在任务 CAS 前通过 `OrganizationProvider.findUser` 解析全部用户并固化姓名；空列表、无效用户、当前办理人重复、同一用户重复按冻结规则处理。
3. 当前任务取消 CAS，以 `ADD_SIGN` 归档。
4. 创建一个 `group_type=COUNTERSIGN` 的临时任务组：
   - `total_count` 为去重后的加签人数；
   - `completed_count=0`、`group_status=ACTIVE`；
   - `branch_state_json` 保存 `purpose=ADD_SIGN`、来源任务快照、来源操作号和 `schemaVersion`；
   - 普通串行任务的 `parent_group_id/parent_branch_key` 为空。
5. 为每个加签人创建一条同节点临时任务，写入该临时组 ID，并把目标用户写为唯一办理人。
6. 写任务级审计、`TASK_ADDED_SIGN` 和每条 `TASK_CREATED` 回调。

扩展 `approve`：

1. 如果任务没有任务组或任务组不是 `purpose=ADD_SIGN`，继续走现有普通审批逻辑。
2. 加签临时任务审批时只完成和归档自己的任务，不沿节点出线推进。
3. 使用现有 `incrementCompletedCount` 做任务组 CAS和有限重试。
4. 非最后一个完成者返回空 `createdTasks`。
5. 最后一个完成者把组置为 `COMPLETED` 后，根据组内来源快照重新创建原任务，恢复原候选人、办理人和节点，不解析新的流程定义版本。
6. 最后一个完成者返回恢复后的任务并发出 `TASK_CREATED`；实例仍停留在原节点。

该方案只复用 `COUNTERSIGN` 的计数原语，不开放定义级通用会签，不修改 `RuntimeRequestValidator.resolveApprovers` 当前只支持 `SINGLE` 的边界。

限制：

- M5 不允许加签任务再次加签。
- M5 不允许对并行/或签/会签任务加签。
- 加签期间实例终止、跳转或强制办结继续复用 `InstanceTaskCancellationService` 取消临时任务和任务组。
- 加签任务不能驳回、退回、撤回或直送；只能审批、转办，或被管理动作取消。

### M5-B.7：历史、审计、回调和返回结果统一

1. 扩展 `HistoryTaskWriter` 的命令式入口用法，不再在六个动作内手工创建 `ProcessHistoryTaskEntity`。
2. 历史动作：
   - 完成类：`REJECT`、`RETURN`、`DIRECT_SEND`
   - 取消/恢复类：`WITHDRAW`、`ADD_SIGN`
   - 非终态轨迹：`TRANSFER`
3. C 线已办查询需要区分“节点终态记录”和“过程动作记录”，避免把 `TRANSFER` 误计为一次已办节点。
4. 扩展现有回调映射：
   - `REJECT -> PROCESS_REJECTED`
   - `RETURN -> PROCESS_RETURNED`
   - `WITHDRAW -> PROCESS_WITHDRAWN`
   - `DIRECT_SEND -> PROCESS_DIRECT_SENT`
   - `TRANSFER -> TASK_TRANSFERRED`
   - `ADD_SIGN -> TASK_ADDED_SIGN`
5. 路由类动作和加签同时为实际新建任务写稳定 `TASK_CREATED` 事件。
6. `eventId` 延续 `operationId:eventType:targetId`；一个动作创建多个任务时以任务 ID 区分，幂等重放不重复写事件。
7. 审计 `targetType=TASK`、`targetId=原请求taskId`，detail JSON 与历史扩展字段使用同一内部 Mapper。

### M5-B.8：装配和跨线收口

1. `DefaultProcessRuntimeService` 注入唯一增强动作协调器，不新增第二个 `ProcessRuntimeService` Bean。
2. 独立应用通过已有组件扫描获得实现，现有 `ProcessRuntimeController` 不改路径、不复制动作。
3. Starter 当前仍未显式装配 `ProcessRuntimeService/AdminProcessService`；该 M3 遗留由 Starter 负责人修复后，增加联合装配测试，B 不在 M5 创建 Unsupported Runtime 替代品。
4. B 线冻结并实现 `listenerConfig.taskActionRules` 的 Mapper、读取器和业务校验；与 A 线联合验证通用保存/发布链路能原样持久化该命名空间。
5. 与 C 线联合验证：
   - 审计可查；
   - 已办列表不被非终态动作污染；
   - 待办展示转办后的办理人和加签临时任务；
   - 回调 payload 包含归档任务、新建任务和操作者；
   - `claim/unclaim` 与转办并发互斥。

## 6. 预计代码改动

优先修改：

- `core/runtime/DefaultProcessRuntimeService.java`
- `core/runtime/RuntimeRequestValidator.java`
- `core/runtime/RuntimeOperationTypes.java`
- `core/runtime/RuntimeErrorCodes.java`
- `core/task/HistoryTaskWriter.java`
- `persistence/repository/ProcessHistoryTaskRepository.java`
- `persistence/repository/ActiveTaskRepository.java`
- `persistence/repository/TaskGroupRepository.java`
- `api/dto/TaskActionResult.java`（增加可选 `updatedTasks`，保持旧 JSON 兼容）

新增内部类：

- `core/runtime/EnhancedTaskActionCoordinator.java`
- `core/runtime/EnhancedActionContext.java`
- `core/runtime/EnhancedActionHistoryMetadata.java`
- `core/runtime/AddSignGroupContext.java`

测试新增：

- `EnhancedTaskActionCoordinatorTest`
- `M5EnhancedActionIntegrationTest`
- `M5EnhancedActionConcurrencyIntegrationTest`
- `EnhancedActionHistoryMetadataTest`

继续扩展：

- `TaskRepositoryIntegrationTest`
- `DefaultProcessRuntimeServiceTest`
- `RuntimeRequestContractTest`
- `PlatformServiceControllerTest`
- C 线跨阶段 Spring Boot / Starter / REST 验收测试

不计划修改：

- M4 附件表、附件 Service 主体和附件 REST；
- 流程定义、节点、连线主体表；
- 已冻结的六个 Request 类和 `ProcessRuntimeService` 方法签名；
- 现有动作、事件枚举值；
- `ProcessRuntimeController` 的动作入口。

## 7. 测试矩阵

### 7.1 单元测试

- 每个请求的空字段、身份伪造、任务不存在、实例非运行态、状态非法和版本冲突。
- 驳回目标不存在、非用户任务、B 线 `taskActionRules` 禁止、审批人解析失败。
- 退回找不到发起历史。
- 撤回找不到上一历史、非上一办理人、多活动任务、分组任务。
- 直送无驳回来源、伪造目标、来源已消费、规则禁止。
- 转办目标不存在、转给自己、目标解析异常。
- 加签空列表、重复用户、无效用户、嵌套加签、非加签组误判。
- 历史 `extra_json` 读写、未知版本失败关闭。
- 回调事件类型、稳定事件 ID 和 payload。

### 7.2 Repository 集成测试

- `transfer`、`complete`、`cancel` 同版本竞争只能一个成功。
- 上一终态历史筛选排除 `TRANSFER/CLAIM/UNCLAIM` 控制记录。
- 驳回历史能按它创建的活动任务反查。
- 加签任务组计数不超上限，只有最后一个完成者取得恢复权。
- 加签组取消后不能继续增加计数或恢复原任务。

### 7.3 动作集成测试

- 串行流程：审批节点驳回申请节点，申请重新办理后直送原审批节点。
- 当前审批任务退回到发起/首节点。
- 上一办理人撤回下游待办并重新得到上一节点任务。
- 转办后旧办理人失去权限、目标办理人取得新版本并可审批。
- 两人并行加签：原任务暂停，同时创建两条临时任务；无论完成顺序如何，仅最后一次恢复原任务，流程不提前推进。
- 各动作的历史、审计、回调、当前节点和返回结果一致。
- 相同 `operationId` 重放不重复写历史、任务、组、审计或回调。
- 同号不同请求返回幂等冲突。

### 7.4 并发与回滚

- `approve vs reject`
- `approve vs transfer`
- `transfer vs transfer`
- `withdraw vs current assignee approve`
- `directSend vs current task approve`
- `addSign vs approve`
- 两个加签任务同时成为最后完成者
- 管理员终止/跳转与加签任务完成竞争

每组断言：

- 只有一个合法 CAS 成功；
- 不出现重复历史、重复后续任务、重复组计数或重复回调；
- 事务失败后任务、历史、任务组、审计、回调和幂等成功结果全部回滚；
- 外部用户解析或规则校验失败发生在任务 CAS 前。

### 7.5 全量回归

1. `platform-core` 定向 M5 测试。
2. `platform` 下 `mvn -q test`。
3. 仓库根目录 `mvn -q test`。
4. Starter 注入和独立 REST 六个动作冒烟。
5. M2 串行、条件、单层并行，M3 终止/删除/跳转/强制办结，M4 附件全量回归。

## 8. 完成标准

- 六个 B 线增强动作不再抛阶段未实现异常。
- 没有新增同义 Request、Service、Repository、枚举、Controller 或附件实现。
- 每个写动作都使用 `operationId`；每个任务动作都使用 `expectedTaskVersion`。
- 路由类动作正确更新当前任务、历史、目标任务和实例当前节点。
- 直送只能消费可信驳回来源，不能充当普通用户跳转。
- 撤回只能由冻结权限规则允许的人触发。
- 转办后旧办理人不能继续办理，调用方能取得新任务版本。
- 加签不会推进流程出线；全部加签任务完成后只恢复一次原任务。
- 历史、审计、回调和幂等成功结果与状态修改同事务提交。
- 不支持的并行/会签组合明确失败，不留下孤立任务或任务组。
- 全量 Maven 测试通过，并更新 B 的技术实现说明、阶段完成记录和跨线验收结果。

## 9. 风险和跨线依赖

| 风险/依赖 | 当前事实 | 处理方式 |
| --- | --- | --- |
| Starter 测试基线为红 | 缺少被测试和 `spring.factories` 引用的 Knife4j 自动配置类 | 独立基线修复，完成后再统计 M5 回归 |
| 驳回/直送规则尚无可见运行组件 | `listener_config` 已能保存，但当前代码未检出专用规则解析器 | B 在 `listenerConfig.taskActionRules` 下冻结结构并实现唯一读取器；A 复用通用保存/发布链路 |
| C 线统一审计 Writer 尚不存在 | 当前 B/M3 借用定义 Repository 写审计 | C 提取唯一 Writer；B 只接入 |
| Starter 未显式装配 Runtime Service | 独立应用靠组件扫描，Starter 自动配置主要是 C 线服务和附件 | 由 Starter 负责人补齐，B 做联合验收 |
| 转办响应没有“更新任务”字段 | `TaskActionResult` 只有 archived/created | 增加可选 `updatedTasks`，保留旧字段和反序列化兼容性 |
| 历史 DTO 不暴露 `extra_json` | 内部实体已保存，公共 DTO 无字段 | M5 内部关联可不暴露；如管理端需要查看，由 C 线另评审 |
| 加签缺少专用活动任务类型 | 活动任务表没有 task_kind 或 parent_task_id | 默认复用任务组计数和 JSON purpose，不新增表列 |
| 增强动作与并行组合复杂 | 当前只支持单层并行，M6 才补通用会签/汇聚治理 | M5 路由动作失败关闭，避免留下孤立分支 |
