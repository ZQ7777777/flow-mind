# 实习生 B：M2 运行时编码计划

## 1. 目标与现有代码复用

M2 在 `platform-core` 内实现 `ProcessRuntimeService` 的启动、提交、审批、变量更新和实例详情，跑通串行流程，并提前接入条件网关和单层并行网关。当前 `platform` 全量测试已通过，实施期间必须复用以下代码：

- 保持 [ProcessRuntimeService.java](E:/resume_project/flow-mind/platform/platform-core/src/main/java/com/flowmind/platform/api/service/ProcessRuntimeService.java:25) 及现有 Request、DTO、枚举不变；M2 实现 `startProcess`、`startAndSubmit`、`submitTask`、`approve`、`updateVariables`、`getInstance`，其余 M3/M5 方法暂按现有模式明确抛出阶段未实现异常。
- 直接复用 [ActiveTaskRepository.java](E:/resume_project/flow-mind/platform/platform-core/src/main/java/com/flowmind/platform/persistence/repository/ActiveTaskRepository.java:16) 的 `complete` 乐观锁更新，以及 `TaskGroupRepository.markBranchArrived` 的分支汇聚 CAS；只补插入、查询和状态统计，不重写条件更新 SQL。
- 复用 `OperationIdempotencyService`、`ProcessOperationRecordRepository`、`ProcessDefinitionCache`、`DefaultProcessDefinitionService.getDefinition`、`DefinitionModelValidator` 和现有状态校验器，不建立第二套幂等、缓存或状态规则。
- 复用现有 SQLite 表结构和实体，不新增运行时重复表；M2 仅补缺失 Repository。Starter 自动装配、REST、发布激活生命周期仍分别留给 M3/C 线和 A 线。
- 将现有 `DefinitionGraphIndex` 的排序和索引逻辑提取为可被校验器与运行时共同使用的内部只读流程图对象，避免 M2 再写一套节点/连线解析器。

## 2. 分步编码内容

### 第一步：补齐运行时持久化原语

- 扩展 `ProcessDefinitionRepository`：仅在新实例启动时，按 `processCode` 严格选择 `PUBLISHED + ACTIVE + grayStatus=OFF` 的全量激活版本并取得其 `definitionId`；无结果返回 `FLOW_DEFINITION_NOT_ACTIVE`，多结果视为定义状态异常。灰度选择不进入 M2。
- 新增 `ProcessInstanceRepository`：实例插入、按 ID 查询、限定 `NOT_STARTED/RUNNING` 合并变量、更新当前节点集合、办结实例、统计未完成任务/任务组。
- 扩展 `ActiveTaskRepository`：新增任务插入、按 ID 查询、按实例查询 `ACTIVE/CLAIMED` 任务、按实例统计未完成任务；已有 `complete(id, expectedVersion)` 保持不变。
- 新增 `HistoryTaskRepository`：插入历史记录、按实例稳定排序查询；始终写入非空 `operationId`，继续使用表中 `active_task_id + action_type + operation_id` 唯一约束。
- 扩展 `TaskGroupRepository`：新增并行组插入、按 ID 查询及活动组统计；汇聚继续调用现有 `markBranchArrived`，冲突时最多重读重试 3 次。
- 新增统一运行时 Mapper/JSON 组件，负责实例、任务、历史任务、任务组与 DTO 的转换，以及变量、候选人、当前节点、分支状态 JSON。结果 JSON 使用 Jackson Java Time 模块，不能复用只支持定义简要结果的 `JsonCodec`。

### 第二步：定义加载、请求校验和幂等执行框架

- `RuntimeDefinitionLoader` 明确拆分为两条读侧，缓存始终以 `definitionId` 为 key：
  - 新实例启动调用 `loadForStart(processCode)`：先按 `processCode` 选择唯一的已发布且激活全量版本，取得 `definitionId`，再调用 `loadByDefinitionId(definitionId)` 读取 `ProcessDefinitionCache`。缓存未命中时捕获缓存世代，按 `definitionId` 加载并校验完整定义，确认其仍满足新实例启动状态后安全回填缓存。
  - 已有实例继续流转调用 `loadForInstance(instance)`：直接以实例固化的 `definitionId` 读取对应定义，不再按 `processCode` 重新选择当前激活版本；实例中的 `version`、`processCode` 只用于核对加载结果是否仍是启动时绑定的定义快照，不参与缓存定位或数据库查询。
  - 已有实例绑定的定义后来被停用时，不得因此切换版本或阻断运行中实例；缓存失效且当前缓存规则不允许回填非激活定义时，允许按 `definitionId` 从数据库读取并完成快照与结构校验，但不写入 `ProcessDefinitionCache`。
- 请求校验统一覆盖：必填字段、当前用户与请求中的发起人/操作人一致、任务存在、实例状态、任务状态、候选人/受理人权限和 `expectedTaskVersion`。
- 使用 `CurrentUserProvider` 固化发起人和实际办理人名称；使用 `ApproverResolver` 解析用户任务候选人。解析结果按用户 ID 去重且不得为空；M2 的普通用户节点只支持 `SINGLE`，或签/会签继续留到 M6。
- 为每类请求生成排除 `operationId` 的规范化 SHA-256 摘要：Map 键稳定排序，附件使用元数据及内容摘要，不把原始文件内容写入操作记录。
- 强化现有 `OperationIdempotencyService`，不新建运行时幂等器：
  - 并发插入同一 `operationId` 时捕获唯一键竞争并重新读取；
  - 过期租约接管使用条件更新，保证只有一个请求取得接管权；
  - 增加实例/任务目标绑定；
  - 成功结果与业务数据同事务提交；
  - 相同请求成功重放恢复原 DTO，`TaskActionResult.replayed=true`；
  - 同号不同请求、处理中和已失败记录分别返回冻结错误；
  - 仅确定性业务失败写 `FAILED`，数据库中断等不确定失败保留 `PROCESSING` 租约。

### 第三步：统一节点推进器与任务创建

实现内部 `advanceToNode`，启动、提交、审批均调用同一入口：

- `USER_TASK`：调用 `ApproverResolver`，为 `SINGLE` 节点创建一条候选任务；并行分支任务继承 `taskGroupId` 和 `branchKey`。
- `EXCLUSIVE_GATEWAY`：按现有图索引中的 `sortOrder/edgeCode` 稳定顺序检查非默认出线；通过 A 线正式提供的 `ConditionExpressionEvaluator.evaluate(expression, variables)` 判断，不在 B 线定义表达式语法。首条命中即推进；均不命中走唯一默认线，否则返回 `FLOW_GATEWAY_NO_MATCH`。
- `PARALLEL_SPLIT_GATEWAY`：创建一条 `PARALLEL_GATEWAY` 任务组，以每条出线 `edgeCode` 初始化 `RUNNING` 分支并分别推进。本阶段仅支持单层并行；在已有并行上下文中再次遇到拆分网关时返回配置不支持错误。
- `PARALLEL_JOIN_GATEWAY`：校验任务组、配对网关和分支键，通过现有 `markBranchArrived` 原子更新；未汇齐时停止，成功把组更新为 `COMPLETED` 的最后分支沿汇聚网关唯一出线继续。
- `END`：确认不存在 `ACTIVE/CLAIMED` 任务及活动任务组后，将实例置为 `COMPLETED` 并写结束时间。
- `START`：只允许作为初始化入口，运行中再次进入视为定义错误。
- 每条自动路径维护独立 `visitedNodeCodes`，最大步数取节点数与连线数之和加一；检测到自动节点闭环或超限时整体回滚。
- 每次推进结束后，根据数据库中仍活动的任务重新计算 `currentNodeCodes`，避免串行、条件和并行分别维护不同逻辑。

### 第四步：实现六个 M2 Service 方法

- `startProcess`
  - 校验并加载激活定义，保存定义、版本、流程名称、发起人、附件配置组和初始变量快照；
  - 创建 `NOT_STARTED` 实例，`startedAt/currentNodeCodes/createdTasks` 为空，不创建任务和启动回调；
  - 保存可重放结果。

- `startAndSubmit`
  - 创建 `RUNNING` 实例并写 `startedAt`；
  - 从唯一开始节点出线调用统一推进器，允许经过条件或单层并行网关，直到创建首批任务或直接结束；
  - 返回实例及 `createdTasks`，写流程启动、任务创建、必要时流程完成事件。

- `submitTask`
  - 仅用于发起人办理 `STARTER` 规则的申请任务；
  - 在任务 CAS 前调用现有 `AttachmentService` 完成节点附件校验/保存协作，B 不实现附件存储；调用顺序测试必须证明文件或外部 SPI 不发生在任务 CAS 与数据库提交之间；
  - 使用现有 `ActiveTaskRepository.complete` 取得处理权，失败映射为 `FLOW_TASK_CONCURRENT_MODIFIED`；
  - 对 `SubmitTaskRequest.variables` 做浅合并：未提交键保留、同名键覆盖、M2 不提供删除变量语义；
  - 归档为 `SEND/NORMAL` 历史任务，保存意见和合并后的变量快照，再推进后续节点。

- `approve`
  - 仅办理非 `STARTER` 用户任务；`ApproveTaskRequest` 当前没有变量字段，因此审批不更新变量；
  - 完成任务 CAS，归档为 `APPROVE/NORMAL`，保存实际办理人、意见和当前变量快照，再推进后续节点。

- `updateVariables`
  - 按已确认范围提前在 M2 完成；允许 `NOT_STARTED`、`RUNNING` 实例，拒绝终态实例；
  - 浅合并变量并保存幂等结果，不创建、完成或归档任务。

- `getInstance`
  - 聚合实例、当前 `ACTIVE/CLAIMED` 任务和全部历史任务；
  - 历史按 `completedAt + id`、活动任务按 `createdAt + id` 稳定排序；
  - `comments` 由非空历史任务意见映射，不新增重复意见表；分页待办、已办、我发起仍由 C 线 `TaskQueryService` 完成。

### 第五步：事务、回调和跨线协作

- 使用独立事务执行组件承载主数据库事务，避免同类内部调用导致 `@Transactional` 自调用失效。
- 单次任务动作中的任务 CAS、变量更新、历史归档、任务组更新、后续任务、实例状态、回调 Outbox 和幂等成功结果必须同事务提交；任一步失败全部回滚。
- B 继续调用现有 `CallbackService.publishCallback(WorkflowEvent)`，其 M2 生产语义必须由 C 线实现为“当前事务内追加 `PENDING` Outbox”，不得同步调用外部回调处理器。
- 事件 ID 固定为 `operationId:eventType:targetId`；覆盖流程启动、任务完成、每条任务创建及流程完成，重放不得生成第二条记录。
- B 的单元测试使用正式接口的 Fake；不在生产代码新增临时审批人、附件、回调或条件表达式实现。
- 当前发布/激活方法尚未实现，B 的 SQLite 测试直接通过正式 Repository 夹具准备激活定义；A 线生命周期合入后再执行真实发布激活联合验收。
- M2 不修改 `platform-starter` 自动装配，不实现 REST、审计日志、灰度选择、终止/删除、增强动作、或签/会签和嵌套并行。

## 3. 测试与提交顺序

1. Repository 集成测试：实例/任务/历史插入查询、变量更新状态门禁、现有任务 CAS、任务组分支 CAS、稳定排序和唯一约束。
2. 定义加载测试：新实例按 `processCode` 选择激活定义后以 `definitionId` 命中/回填缓存；已有实例直接按固化 `definitionId` 加载且不重新选版；缓存世代失效；新实例拒绝草稿/未激活定义；已有实例发现 `definitionId` 对应详情与固化 `version/processCode` 不一致时拒绝；定义停用后已有实例仍能按固化版本加载但不回填当前缓存，并验证复用现有图索引。
3. 幂等测试：首次执行、成功重放、同号异参、有效租约、过期租约并发接管、成功结果反序列化和目标绑定。
4. 推进器单元测试：用户任务、条件首条/后续命中、默认线、无匹配、表达式异常、循环保护、并行拆分、部分汇聚、最后分支推进、嵌套并行拒绝。
5. Service 单元测试：六个 M2 方法的正常、参数、身份、权限、状态、版本及 SPI 异常路径；验证附件/审批人解析发生在任务 CAS 前。
6. SQLite 闭环测试：
   - `startProcess` 只产生 `NOT_STARTED` 实例；
   - `startAndSubmit → submitTask → approve → approve` 完成申请、经理、财务串行闭环；
   - 条件网关按变量选路；
   - 单层并行分支汇聚后只创建一次后续任务；
   - 办结后无活动状态任务，历史与意见完整；
   - `getInstance` 返回正确活动任务、历史和变量。
7. 并发与回滚测试：两个请求同时办理同一任务仅一个成功；两个并行末分支并发到达只推进一次；在历史、后续任务和回调位置注入异常时不产生部分提交。
8. 运行 `mvn -q test`（`platform` 目录）和根目录 `mvn -q test`，保持现有 M0/M1、Starter 测试全部通过。

## 4. 验收与固定假设

- `startProcess` 只建 `NOT_STARTED` 实例；只有 `startAndSubmit` 创建首批任务。
- `updateVariables` 按用户确认提前纳入 M2。
- M2 提前支持条件网关和单层并行网关；条件求值由 A 线正式端口实现，嵌套并行、或签、会签仍不在本阶段。
- B 不接管 C 线附件、回调、查询或审计生产实现；跨线实现未合入时以正式接口 Fake 完成 B 线单元测试，最终联合验收必须替换为真实实现。
- 只有新实例启动才按 `processCode` 选择全量激活版本，且 M2 不处理灰度命中；已有实例始终按固化 `definitionId` 加载原定义，`version/processCode` 仅用于快照一致性校验，定义停用不触发重新选版。
- 现有数据库结构满足 M2，不新增任务版本列、任务组版本列或重复历史表。
