# 实习生 B：M6 高级流转——会签编码计划

## 1. 基线与范围

- 基于当前 `develop@9c03609`；`platform` 下基线为 71 个测试套件、464 项测试全部通过。
- 复用现有 `COUNTERSIGN` 配置、审批人解析、任务组表、`incrementCompletedCount` CAS、幂等执行器、事务、历史归档、回调、审计和统一节点推进器。
- 不新增数据库表/列、公开 Service、Request、DTO、枚举或 REST 接口；会签继续使用现有 `approve`、`reject`。
- B 只实现会签；条件分支和或签归 A，查询、并行汇聚及外围能力归 C。
- 保持 M5 加签临时组语义，不把 `purpose=ADD_SIGN` 的 `COUNTERSIGN` 组当作定义级会签。
- 已确认：会签支持一票驳回；不支持会签中动态加签；并行分支内的会签只支持审批，一票驳回暂不支持。

## 2. 核心实现

### M6-B.1 会签任务创建

在现有 `RuntimeNodeAdvancer` 的用户任务创建分支中按 `multiInstanceMode` 分派：

- `SINGLE` 保持当前单任务逻辑。
- `COUNTERSIGN` 创建一条任务组和 N 条活动任务。
- `OR_SIGN` 不由 B 实现，不改写 A 线职责。

会签组固定为：

- `group_type=COUNTERSIGN`、`total_count=N`、`completed_count=0`、`group_status=ACTIVE`、`lock_version=0`。
- 即使只解析出一名审批人，也创建一人会签组，不降级为 `SINGLE`。
- 每名审批人对应一条任务，`candidate_user_ids` 为单元素列表、`assignee` 为空，保留认领、委托和转办能力。
- 所有任务共享会签组 ID；实例 `currentNodeCodes` 仍只出现一次该节点编码。
- 位于并行分支时，会签组通过 `parent_group_id/parent_branch_key` 保存外层上下文；会签任务使用内层会签组 ID，并保留外层 `branch_key`。
- 审批人为空、组或任一任务插入失败时整笔事务回滚，不留下半组数据。
- 现有动作产生的 `createdTasks` 返回全部会签任务，并沿用既有稳定 `TASK_CREATED` 回调。

### M6-B.2 会签审批与唯一推进

新增内部 `CountersignTaskCoordinator`，由现有运行时 Service 调用，不形成第二套公共 Service：

1. 加载任务、实例、固化定义和任务组，确认节点配置为 `COUNTERSIGN`，且不是 M5 加签临时组。
2. 从任务组的父字段计算后续推进上下文；在任务 CAS 前预解析唯一出线及后续审批人，避免修改状态后调用外部 SPI。
3. 使用 `expectedTaskVersion` 完成当前任务 CAS，并按现有 `APPROVE` 规则归档历史。
4. 读取活动会签组，以 `group_status=ACTIVE + lock_version` 调用现有 `incrementCompletedCount`：
   - CAS 冲突且组仍为 `ACTIVE` 时读取新版本并有限重试；
   - 组已终态或重试耗尽时返回 `FLOW_TASK_GROUP_CONCURRENT_MODIFIED`，当前任务和历史同时回滚。
5. 是否为最后一人必须根据“本次 CAS 前计数 + 1”判断；不得在更新后重读终态并误认其他请求为最后完成人。
6. 非最后一人只返回自己的归档任务，`createdTasks` 为空，不沿出线推进。
7. 最后一人取得唯一推进权，使用父任务组/分支上下文调用现有 `advanceToNode`；后续可进入用户任务、条件网关、并行拆分或结束节点。
8. 历史、组计数、下一任务、实例状态、回调及幂等成功结果保持同一事务。

### M6-B.3 一票驳回

扩展现有 `EnhancedTaskActionCoordinator.reject`，不新增专用驳回接口：

1. 继续复用 M5 的目标用户节点校验和 `listenerConfig.taskActionRules.reject`。
2. 仅允许无外层并行上下文的定义级会签组；并行分支内会签驳回返回 `FLOW_GROUPED_TASK_ACTION_NOT_SUPPORTED`。
3. 在写入前预解析驳回目标审批人。
4. 完成当前驳回任务并归档为 `REJECT`，随后以组状态和版本 CAS 将会签组置为 `CANCELED`。
5. 读取同组其余开放任务，逐条按任务版本取消并以 `CANCEL` 归档；已完成审批历史保持不变。
6. 任一同组任务发生并发修改则整笔驳回回滚，不允许留下“组已取消但仍有开放任务”的状态。
7. 沿驳回目标重新推进；若目标仍是会签节点，则创建一套新的会签组。
8. `TaskActionResult.archivedTasks` 包含本次 `REJECT` 和所有被取消任务，`createdTasks` 包含驳回目标任务；沿用 `PROCESS_REJECTED`、`TASK_CREATED` 回调和统一审计。
9. 并发审批与驳回以任务组终态 CAS 决胜：最终审批先完成组则驳回失败；驳回先取消组则审批回滚，二者不能同时推进。

### M6-B.4 最小持久化增量与装配

- `TaskGroupRepository` 继续复用 `insert/findById/incrementCompletedCount/cancel`，不复制计数 SQL。
- `ActiveTaskRepository` 只增加按任务组稳定查询开放任务的能力，用于一票驳回取消同组任务。
- `CountersignTaskCoordinator` 同时注入普通审批链路和增强动作协调器；Starter、独立应用和 REST 仍使用同一个 `DefaultProcessRuntimeService`。
- 保留现有 M5 组合边界：会签中不允许 `addSign`、`returnToStarter`、`withdraw`、`directSend`；转办、认领/取消认领继续按现有单任务 CAS 工作。

## 3. 分步交付

1. **M6-B.0 基线冻结**：记录 `9c03609`、464 项测试和工作区既有删除改动，开发中不恢复或覆盖用户文件。
2. **M6-B.1 创建闭环**：完成会签组、多人任务、父并行上下文和创建结果测试。
3. **M6-B.2 审批闭环**：完成部分审批、最后一人推进、有限重试和 M5 加签组隔离。
4. **M6-B.3 一票驳回**：完成整组取消、取消历史、目标推进、并行组合门禁及审批/驳回竞争。
5. **M6-B.4 跨线集成**：与 A 的条件/或签、C 的并行汇聚和查询结果联测，避免复制各线实现。
6. **M6-B.5 收口**：运行 `mvn -q test`，更新 `doc/实习生B/阶段完成记录.md`，形成 `doc/实习生B/m6-coding-plan.md` 对应的实现与验收记录。

## 4. 测试与验收

- 创建：三人会签生成一组三任务；审批人去重排序；一人会签仍建组；解析为空和中途插入失败无残留。
- 审批：前两人仅归档自身任务；最后一人只创建一次后续任务；组计数和状态正确。
- 并发：最后两人并发审批均只完成各自任务，且仅一人推进；组版本冲突可有限重试；重试耗尽稳定失败。
- 幂等：相同 `operationId` 返回首次结果，不重复计数、历史、任务和回调；同号不同请求拒绝。
- 一票驳回：取消剩余会签任务并归档，已审批记录保留，只创建一次驳回目标任务。
- 竞争：最终审批与一票驳回并发时只有一种终态和一条推进路径；失败方事务无副作用。
- 组合：会签后进入普通用户任务、条件网关、并行拆分和结束节点；并行分支内会签审批完成后恢复正确外层上下文并参与汇聚。
- 门禁：并行分支内会签驳回、会签中动态加签及其他未开放分组动作返回明确错误。
- 查询与回调：每名审批人只能看到自己的会签待办；实例活动任务和历史完整；每次审批有 `TASK_COMPLETED`，实际创建任务有 `TASK_CREATED`，驳回有 `PROCESS_REJECTED`。
- 回归：M2 串行/条件/并行、M3 管理动作、M5 加签恢复、转办/认领、查询、Starter 和 REST 测试全部保持通过。

## 5. 固定假设

- 会签完成条件固定为全部审批任务 `approve`；本期不实现比例会签、权重、超半数、减签或超时自动投票。
- 一票驳回只作用于串行上下文中的定义级会签组；并行会签驳回等待后续专门设计跨分支回退。
- 会签人数进入节点时冻结，M6 不允许运行中扩大 `totalCount`。
- 不处理现有 `RuntimeNodeAdvancer` 明确拒绝的嵌套并行拆分；“并行分支内会签”不等于开放嵌套并行网关。
- 不修改冻结的公共返回结构；被取消任务通过现有 `archivedTasks` 表达，不新增 `canceledTaskIds`。
