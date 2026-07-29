# 实习生 B：M3 实例管理与管理员命令编码计划

## 概要

基于当前 `develop@5ea4eba`，M2 已具备实例启动、变量更新、实例详情、任务/任务组 CAS、历史归档、幂等执行、事务编排、节点推进和回调 Outbox。M3 不重写这些能力，只补齐：

- `terminate`、`deleteInstance`；
- `AdminProcessService.jumpToNode`、`forceComplete`；
- M2 合并后测试基线修复及既有状态维护回归；
- 为 C 线 Starter/REST 集成提供唯一命令实现。

管理员查询、Starter/REST、物理文件清理和 M5 审批动作不由 B 重复实现。

## 实施任务

### M3-B.0：修复 M2 测试基线

- 修复当前 14 个测试错误：测试中不再 Mock `final RuntimeAdvancePreparation`，改用真实准备结果；补齐并行推进测试的审批人夹具。
- 不修改 M2 已通过集成测试的业务语义。
- 门禁：`platform` 下 `mvn -q test` 全绿后才开始 M3 功能编码。

### M3-B.1：补齐实例管理持久化原语

- 扩展实例 Repository，提供条件状态转换：
  - `RUNNING -> TERMINATED`，同时清空当前节点并写结束时间；
  - `TERMINATED -> RUNNING`，供管理员跳转恢复流程；
  - `RUNNING -> COMPLETED`，供强制办结并清空当前节点。
- 扩展活动任务、任务组 Repository，按实例稳定读取开放记录，并逐条使用当前 `lockVersion` 执行取消 CAS。
- 增加实例级联删除 Repository，按外键顺序删除附件元数据、已阅、提醒、告警、历史任务、活动任务、任务组和实例；操作幂等记录不得删除。
- 提取通用审计日志 Repository，复用 A 线已有插入逻辑，避免运行时再次手写相同 SQL。
- 增加内部取消协调器：统一完成任务取消、任务组取消、`HistoryTaskWriter` 归档和归档结果组装，供终止、跳转、强制办结复用。

### M3-B.2：实现终止与实例删除

- `terminate`：
  - 校验 `operationId`、实例 ID，以及当前用户与 `operatorUserId` 一致；
  - 仅允许 `RUNNING` 实例；
  - 复用幂等执行器，以 `TERMINATE` 登记操作；
  - 同事务内取消开放任务和活动任务组、以 `TERMINATE` 归档任务、更新实例状态、写审计、写 `PROCESS_TERMINATED` 回调并保存成功快照；
  - 相同请求直接重放，不重复归档或回调。

- `deleteInstance`：
  - 允许删除任意现存实例状态；
  - 幂等、审计动作复用 `CANCEL`，回调使用 `PROCESS_CANCELED`；
  - 删除前保存实例快照并写取消回调，随后级联删除运行数据；
  - 审计和回调记录保留，将 `instance_id` 清空，并在 `detail_json/payload_json` 写入 `targetDeleted=true`、`deleteMode=HARD`；
  - 未过期及既有操作记录全部保留，删除操作成功结果保存为 `OperationResult(INSTANCE, deleted=true)`；
  - 成功重放时设置 `replayed=true`，不存在实例的全新请求返回稳定的实例不存在错误；
  - M3 只删除 `process_attachment` 元数据，不调用 `FileStorageProvider`；物理文件清理由 M4 统一处理。

### M3-B.3：实现管理员跳转与强制办结

新增唯一的 `DefaultAdminProcessService`，只实现两个命令方法：

- `jumpToNode`：
  - 支持 `RUNNING` 实例，以及从 `TERMINATED` 恢复流转；
  - 目标必须属于实例固化的定义版本；
  - 允许跳转到用户任务、条件网关、并行分支网关或结束节点；
  - 禁止跳转到开始节点和没有分支上下文的并行汇聚节点；
  - 在任何状态写入前调用 `RuntimeNodeAdvancer.prepareAdvance` 完成审批人和条件预解析；
  - 同事务取消原任务/任务组、以 `JUMP` 归档；终止实例先恢复为 `RUNNING`，再复用现有 `advanceToNode` 推进；
  - 写审计、`PROCESS_JUMPED` 回调、归档任务和新建任务结果。

- `forceComplete`：
  - 仅允许 `RUNNING` 实例；
  - 取消并归档全部开放任务和活动任务组，动作使用 `FORCE_COMPLETE`；
  - 实例置为 `COMPLETED`、清空当前节点、写审计和 `PROCESS_COMPLETED` 回调；
  - 不创建后续任务，不复用普通节点推进器。

`AdminProcessService` 的五个查询方法保留明确的阶段未实现异常，注明由 C 线补齐，不在 B 线复制查询 SQL。

### M3-B.4：状态维护与跨线收口

- 保留现有 `updateVariables`：仍只允许 `NOT_STARTED/RUNNING`，执行浅合并、幂等重放和同号不同请求拒绝；不另建变量服务。
- 保留现有 `getInstance` 聚合逻辑，补充终止、跳转恢复和强制办结后的详情断言。
- B 不修改 Starter 或 REST Controller；C 线完成适配后增加联合验收：
  - Starter 注入的 `ProcessRuntimeService/AdminProcessService` 指向 B 的唯一实现；
  - REST 可完成串行闭环、终止、删除、跳转和强制办结；
  - 适配层不得复制状态机。

## 公共接口与兼容性

- 不修改现有 `ProcessRuntimeService`、`AdminProcessService` 方法签名。
- 不新增 `DELETE_INSTANCE` 或 `PROCESS_DELETED` 枚举，删除复用冻结的取消语义。
- 不新增数据库表或状态列；删除标记写入现有日志 JSON。
- 使用现有稳定错误码：实例不存在、实例状态非法、节点不存在、任务/任务组并发冲突、幂等冲突和非法动作。
- 所有任务、任务组、实例状态、历史、审计、回调和幂等成功结果必须在同一事务提交。

## 测试与验收

- Repository：状态条件更新、逐任务/任务组取消、完整级联删除、日志脱钩及删除标记。
- 终止：正常终止、无活动任务、并行任务组、非运行态拒绝、幂等重放、同号不同请求、事务回滚。
- 删除：各实例状态、所有关联表清理、日志和操作记录保留、重复删除重放、外键完整性。
- 跳转：用户任务、条件网关、并行分支、结束节点、终止实例恢复；开始/汇聚/未知节点拒绝；审批人解析失败无副作用。
- 强制办结：任务与任务组全部取消、只产生一次办结回调、无后续任务、并发调用不重复归档。
- 并发：终止/跳转/强制办结与普通审批竞争时，数据库最终状态一致且无孤立任务、重复历史或重复回调。
- 回归：M2 串行、条件、并行、变量更新、实例详情、回调事务测试保持通过。
- 最终执行 `mvn -q test`，并完成 C 线 Starter/REST 联合验收。

## 已确认假设

- M3 包含跳转和强制办结，但不包含管理员查询。
- 当前阶段任何已认证用户均可执行管理命令，仅核对可信当前用户与请求操作人一致，不区分管理员角色。
- 实例删除使用 `CANCEL / PROCESS_CANCELED`。
- M3 删除附件元数据，不负责外部文件物理清理。
- 驳回、退回、撤回、转办、加签和认领仍留在 M5。
