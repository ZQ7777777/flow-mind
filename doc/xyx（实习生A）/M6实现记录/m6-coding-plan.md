# 实习生A：M6 条件分支与或签编码计划

阶段：M6 高级流转  
负责人：实习生A（徐雨新）  
更新日期：2026-07-28  
参考文档：《流程平台技术路线_v5.2》《实习生A_完整技术文档》《实习生B/plan.md》《流程平台设计与接口文档_v4》  
技术基线：Java 8、Spring Boot 2.7.18、Spring JDBC、SQLite

## 1. 目标与边界

根据技术路线 v5.2，M6 阶段 A 线负责条件分支和或签。结合当前代码审查结果，本阶段目标不是重做已经落地的定义配置能力，而是在共享运行时链路中补齐高级流转验收闭环：

- 条件分支：复核并补强当前 `EXCLUSIVE_GATEWAY` 运行时路由，确保条件命中、默认出线、无匹配回滚、表达式异常回滚和稳定排序都有服务级验收。
- 或签：将 `multiInstanceMode=OR_SIGN` 从“配置可保存、审批人集合可解析”推进到“运行时可创建任务组、多任务竞争、唯一推进、取消其余任务、历史与回调一致”的闭环。
- 保持 `approve(ApproveTaskRequest)` 作为办理或签任务的唯一公共入口，不新增专用 REST 接口。

A 线 M6 不交付以下内容：

- 不实现 `COUNTERSIGN` 会签闭环。会签由 B 线负责，A 线只保证 `OR_SIGN` 不破坏现有 `COUNTERSIGN` 配置和加签临时任务组语义。
- 不实现并行汇聚、管理员修复、超时处理、催办、异常告警。这些属于 C 线；A 线只保证或签后可以继续进入现有并行网关或普通节点。
- 不打开分组任务上的驳回、撤回、转办、加签、直送等增强动作。当前 `EnhancedTaskActionCoordinator` 已对分组任务返回 `GROUPED_TASK_ACTION_NOT_SUPPORTED`，M6 A 线保持该边界。
- 不新增数据库表，不做多数据库适配，不引入 Spring Boot 3.x、Jakarta 或 Java 9+ API。

## 2. 当前代码审查结论

### 2.1 已完成能力

当前仓库已经具备以下可复用基础：

- 工程结构：`platform-core` 为主体模块，`platform-starter` 为自动装配模块，技术栈符合 Java 8、Spring Boot 2.7.18、Spring JDBC、SQLite。
- 定义管理：`DefaultProcessDefinitionService` 已实现创建定义、保存流程图、发布前校验、发布、激活、停用、归档、复制、删除、详情查询和分页查询。灰度启用/关闭接口保留但当前显式 `UnsupportedOperationException`。
- 定义配置：`ProcessNodeDTO.multiInstanceMode`、`ProcessNodeDTO.listenerConfig`、`ProcessEdgeDTO.conditionExpression`、`ProcessEdgeDTO.defaultEdge` 已可保存、复制、查询并纳入幂等 hash。
- 发布校验：`DefinitionModelValidator` 已校验排他网关至少两条出线、最多一条默认出线、非默认出线必须有条件表达式、条件表达式只能放在排他网关非默认出线上、非用户任务不得配置 `OR_SIGN/COUNTERSIGN`。
- 条件表达式：`ConditionExpressionSyntaxValidator` 与 `SimpleConditionExpressionEvaluator` 已支持受限单变量比较表达式，拒绝脚本、函数、复杂逻辑组合。
- 审批人解析：`OrganizationProvider`、`ApproverResolver`、`DefaultApproverResolver` 和 `ApproverResolveRequestFactory` 已接入运行时任务创建路径，`RuntimeRequestValidator.resolveApprovers` 会去重、排序并拒绝空候选人。
- 串行运行时：`DefaultProcessRuntimeService` 已实现 `startProcess`、`startAndSubmit`、`submitTask`、`approve`、变量更新、实例详情、终止和实例删除；普通用户任务通过任务 CAS、历史归档、回调和幂等记录推进。
- 网关运行时：`RuntimeNodeAdvancer` 已实现 `EXCLUSIVE_GATEWAY` 条件路由、`PARALLEL_SPLIT_GATEWAY` 单层并行拆分、`PARALLEL_JOIN_GATEWAY` 汇聚计数和自动节点环路检测。
- 任务组基础：`process_task_group`、`TaskGroupRepository`、`TaskGroupTypeEnum.OR_SIGN/COUNTERSIGN/PARALLEL_GATEWAY` 已存在，支持任务组状态、完成计数、乐观锁和分支状态。
- 附件与查询：`DefaultAttachmentService`、`DefaultTaskQueryService`、`DefaultCallbackService`、REST Controller 和 Starter 部分装配已存在。`ProcessMonitorService` 在 starter 默认实现中仍是 unsupported 占位。
- 增强动作：`EnhancedTaskActionCoordinator` 已实现驳回、退回、撤回、直送、转办、加签的保守语义，并显式拒绝分组任务组合。

### 2.2 M6 直接缺口

- `RuntimeNodeAdvancer.createUserTask` 当前无论 `SINGLE` 还是 `OR_SIGN`，都只创建一条活动任务，候选人列表写入同一任务，未创建 `OR_SIGN` 任务组。
- `DefaultProcessRuntimeService.handleTaskActionInTransaction` 当前完成任一任务后直接沿唯一出线推进，未在 `OR_SIGN` 场景中抢占任务组、取消同组其他任务或阻止重复推进。
- `TaskGroupRepository.incrementCompletedCount` 适合会签或加签临时任务计数，不适合或签“首个完成即完成任务组”的语义，需要新增或签专用 CAS 原语。
- `ActiveTaskRepository` 缺少按 `task_group_id` 稳定读取同组开放任务的 Repository 方法，无法批量取消或签剩余任务。
- 现有 `TaskActionResult` 没有 `canceledTaskIds` 字段；当前更稳妥的 A 线方案是将当前任务和被取消任务都写入 `archivedTasks`，不在 M6 A 线单独扩展公共 DTO。
- 条件分支已有单元测试和部分集成测试，但仍需补服务级事务回滚和幂等重放验收，避免只证明推进器局部行为。

## 3. 与 B、C 线的分工边界

### 3.1 与实习生B的边界

B 线 M6 负责会签：多任务全部完成、完成计数、最后一人推进、会签历史和回调。A 线不得把 `COUNTERSIGN` 的完成计数语义混入 `OR_SIGN`。

A 线可以修改共享运行时组件，但改动边界限定为：

- 在 `RuntimeNodeAdvancer` 中按 `OR_SIGN` 创建任务组和多条候选任务。
- 在 `DefaultProcessRuntimeService.approve` 普通审批路径中识别 `OR_SIGN` 任务组，并执行唯一推进。
- 复用 `HistoryTaskWriter`、`RuntimeOperationExecutor`、`CallbackService` 和 `RuntimeNodeAdvancer`，不复制 B 线动作编排框架。

A 线不得修改以下语义：

- `EnhancedTaskActionCoordinator.requireSerial` 对分组任务的失败关闭。
- 加签临时任务组使用 `TaskGroupTypeEnum.COUNTERSIGN` 且 `branchStateJson.purpose=ADD_SIGN` 的识别方式。
- B 线后续会签需要的 `TaskGroupRepository.incrementCompletedCount` 计数语义。

### 3.2 与实习生C的边界

C 线负责并行汇聚、管理员修复、超时处理、催办、异常告警、查询展示和回归测试总收口。当前代码已经有单层并行汇聚实现，但 M6 A 线不声明 C 线能力完成。

A 线需要向 C 线提供的稳定语义：

- 或签创建的多条活动任务共享同一个 `taskGroupId`，`TaskDTO.taskGroupId` 可用于查询层展示。
- 任一或签任务完成后，同组其余开放任务进入 `CANCELED` 并写历史轨迹，待办查询不再返回这些任务。
- 回调沿用现有事件模型，`archivedTasks` 包含完成和取消轨迹，`createdTasks` 只包含唯一推进产生的后续任务。
- 或签不新增查询 DTO，也不要求 C 线为 A 线新增专用查询接口。

## 4. 建议文件改动清单

生产代码预计修改：

- `platform-core/src/main/java/com/flowmind/platform/core/runtime/RuntimeNodeAdvancer.java`：按 `multiInstanceMode` 创建 `SINGLE` 或 `OR_SIGN` 用户任务。
- `platform-core/src/main/java/com/flowmind/platform/core/runtime/DefaultProcessRuntimeService.java`：在普通 `approve` 路径中识别或签任务组并执行唯一推进。
- `platform-core/src/main/java/com/flowmind/platform/persistence/repository/TaskGroupRepository.java`：新增或签任务组 CAS 完成方法。
- `platform-core/src/main/java/com/flowmind/platform/persistence/repository/ActiveTaskRepository.java`：新增按任务组读取开放任务的方法，必要时新增按任务组批量稳定读取方法。
- `platform-core/src/main/java/com/flowmind/platform/core/runtime/RuntimeErrorCodes.java`：如现有错误码不足，再补充或签上下文错误码；优先复用 `TASK_GROUP_CONCURRENT_MODIFIED`、`TASK_GROUP_STATUS_INVALID`、`GROUPED_TASK_ACTION_NOT_SUPPORTED`。
- `platform-core/src/main/java/com/flowmind/platform/core/task/HistoryTaskWriter.java`：优先不修改；若归档取消任务需要携带更明确 extraJson，再最小扩展 `HistoryArchiveCommand.extraJson` 使用点。

测试代码预计新增或修改：

- `platform-core/src/test/java/com/flowmind/platform/core/runtime/RuntimeNodeAdvancerTest.java`
- `platform-core/src/test/java/com/flowmind/platform/core/runtime/DefaultProcessRuntimeServiceTest.java`
- `platform-core/src/test/java/com/flowmind/platform/core/runtime/M6OrSignWorkflowIntegrationTest.java`
- `platform-core/src/test/java/com/flowmind/platform/core/runtime/M6OrSignConcurrencyIntegrationTest.java`
- `platform-core/src/test/java/com/flowmind/platform/core/runtime/M6ExclusiveGatewayRuntimeIntegrationTest.java`
- `platform-core/src/test/java/com/flowmind/platform/persistence/repository/TaskRepositoryIntegrationTest.java`

文档预计新增或更新：

- `doc/xyx（实习生A）/M6实现记录/m6-coding-plan.md`
- 后续编码完成后新增 `doc/xyx（实习生A）/M6实现记录/技术实现说明.md`
- 后续验收完成后新增 `doc/xyx（实习生A）/M6实现记录/阶段完成记录.md`

## 5. 微任务拆分

### M6-A.0 基线复核与边界冻结

开发内容：

- 复核《流程平台技术路线_v5.2》中 M6 分工：A 为条件分支、或签；B 为会签；C 为并行汇聚、管理员修复、超时、催办、异常告警。
- 复核《实习生A_完整技术文档》第 9、10 节：条件网关命中、默认出线、无匹配回滚、或签唯一推进权。
- 复核《实习生B/plan.md》中 M6 高级任务状态机：B 负责会签，A 提供条件/或签实现并参与联测。
- 确认当前 M5 记录中 `OR_SIGN/COUNTERSIGN` 仅完成配置冻结，运行时闭环留到 M6。

完成标准：

- 本文件固定 A/B/C 边界。
- 后续编码不接管会签、并行汇聚、提醒告警和分组增强动作。

### M6-A.1 条件分支运行时验收补强

开发内容：

- 保持 `RuntimeNodeAdvancer.selectExclusiveEdge` 的稳定排序语义：按 `DefinitionGraphIndex.getOutgoingEdges` 返回顺序依次计算非默认出线。
- 补服务级集成测试，覆盖 `DefaultProcessRuntimeService.approve` 经由排他网关进入后续用户任务、默认出线或无匹配失败。
- 验证表达式异常、无匹配且无默认出线时，活动任务完成、历史归档、后续任务创建、实例变量更新和幂等成功记录全部回滚。
- 验证相同 `operationId` 重试条件分支审批时返回首次结果，不重复创建后续任务或回调。

完成标准：

- 条件命中第一条、命中后续条、全部不命中走默认、无默认失败、表达式异常失败均有测试。
- 条件分支失败路径不会留下已完成活动任务、历史任务、后续任务或成功幂等结果。
- 不改变表达式语法范围，不引入脚本引擎。

### M6-A.2 或签任务创建

开发内容：

- 在 `RuntimeNodeAdvancer.createUserTask` 中按 `ProcessNodeDTO.multiInstanceMode` 分支：
  - `SINGLE`：沿用当前单任务创建逻辑。
  - `OR_SIGN`：创建一条 `process_task_group`，`group_type=OR_SIGN`，`group_status=ACTIVE`，`total_count=候选人数`，`completed_count=0`，`lock_version=0`。
- 为每个候选人创建一条 `process_active_task`：
  - `task_group_id` 指向同一个或签任务组；
  - `candidate_user_ids` 写入只包含该候选人的 JSON 数组；
  - `task_status=ACTIVE`，`lock_version=0`；
  - `branch_key` 仅在并行分支上下文中沿用传入值，非并行路径为空。
- 候选人列表沿用 `RuntimeRequestValidator.resolveApprovers` 的去重和排序结果，保证或签任务创建顺序稳定。
- 如果后续 B 线实现 `COUNTERSIGN`，A 线不在本任务中创建会签任务。

完成标准：

- `OR_SIGN` 节点解析出 N 个候选人时，创建 1 个任务组和 N 条活动任务。
- `RuntimeAdvanceResult.createdTasks` 返回 N 条任务，且每条任务的 `taskGroupId` 相同。
- `SINGLE` 逻辑测试保持不变。

### M6-A.3 或签唯一推进权

开发内容：

- 在 `DefaultProcessRuntimeService.handleTaskActionInTransaction` 中，在普通 `approve` 路径识别 `task.taskGroupId` 指向 `group_type=OR_SIGN` 的任务组。
- 或签任务只允许通过 `approve(ApproveTaskRequest)` 办理；`submitTask` 仅继续服务申请节点语义，不为或签新增专用入口。
- 当前任务先执行活动任务 CAS：`ACTIVE/CLAIMED + expectedTaskVersion -> COMPLETED`。
- 任务 CAS 成功后执行任务组 CAS：
  - 新增 `TaskGroupRepository.completeOrSignGroup(id, expectedLockVersion)`；
  - 仅允许 `group_status=ACTIVE` 时更新为 `COMPLETED`；
  - 同时设置 `completed_count=1`、`completed_at=datetime('now')`、`lock_version=lock_version+1`。
- 只有任务组 CAS 成功的请求获得唯一推进权。
- 获得推进权后读取同组其余开放任务，逐条按各自 `lock_version` 更新为 `CANCELED`。
- 当前完成任务和同组取消任务都写入历史任务，取消任务使用 `ActionTypeEnum.CANCEL` 或当前项目已有取消类语义，`comment` 明确为 or-sign canceled by winner。
- 只有获得推进权的请求调用 `RuntimeNodeAdvancer.advanceToNode` 创建后续任务或办结实例。

完成标准：

- 三人或签任一人审批后，流程只推进一次。
- 同组其他任务全部取消，不再出现在待办查询中。
- `TaskActionResult.archivedTasks` 至少包含当前完成任务和取消任务历史，`createdTasks` 只包含唯一推进产生的后续任务。
- 如果任务组 CAS 失败，当前事务回滚，不留下半完成活动任务。

### M6-A.4 幂等、历史和回调一致性

开发内容：

- 复用 `RuntimeOperationExecutor.begin/assertExecutable/markSuccess`，不得绕过现有运行时幂等记录。
- 或签首次成功后，`operation_result.result_json` 保存完整 `TaskActionResult`，相同 `operationId` 重试直接 replay。
- 回调复用 `DefaultProcessRuntimeService.publishTaskActionEvents` 或最小扩展内部参数，确保：
  - `TASK_COMPLETED` 事件包含完成和取消历史；
  - `TASK_CREATED` 事件只为唯一推进产生的后续任务创建；
  - 流程办结时仍发送 `PROCESS_COMPLETED`。
- 不新增回调事件类型，不新增 callback 表字段。

完成标准：

- 相同 `operationId` 重试或签审批不重复取消任务、不重复历史、不重复后续任务、不重复成功回调。
- 同号不同请求返回 `FLOW_OPERATION_ID_CONFLICT`。
- 回调失败仍不回滚主流程，沿用现有 callback outbox 语义。

### M6-A.5 并发与回滚测试

开发内容：

- 新增 SQLite 集成测试，使用两个事务/两个服务实例并发审批同一个或签任务组中的不同任务。
- 验证只有一个请求能完成任务组并创建后续任务；另一个请求返回任务或任务组并发冲突，不产生重复历史。
- 验证一个请求已经完成任务组并取消同组任务后，另一个用户再审批被取消任务时返回任务不可办理或并发冲突。
- 验证或签后进入结束节点时，实例完成前没有遗留开放任务或 ACTIVE 任务组。
- 验证或签后进入条件网关、普通用户任务、并行拆分网关均可复用现有推进链路。

完成标准：

- 并发测试断言数据库中后续任务数量为 1。
- 或签组最终 `group_status=COMPLETED`，开放活动任务数量符合预期。
- 历史任务包含 1 条完成历史和 N-1 条取消历史。

### M6-A.6 与 B/C 线联调保护

开发内容：

- 增加测试证明分组任务上的 `reject/withdraw/directSend/transfer/addSign` 仍被拒绝，错误码保持 `GROUPED_TASK_ACTION_NOT_SUPPORTED` 或当前冻结错误码。
- 增加测试证明加签临时任务组仍由 `EnhancedTaskActionCoordinator.isAddSignTask` 接管，A 线新增或签逻辑不误处理 `COUNTERSIGN + ADD_SIGN` 任务组。
- 与 C 线查询约定：或签任务取消后，待办列表不返回取消任务，历史轨迹可查询到完成和取消记录。
- 与 B 线会签约定：A 线新增 `completeOrSignGroup` 不替代 `incrementCompletedCount`。

完成标准：

- M5 增强动作测试不因 M6 或签改动而失效。
- 现有并行网关、加签、驳回、转办相关测试继续通过。
- A 线文档明确 `COUNTERSIGN` 仍留给 B 线。

### M6-A.7 文档与验收记录

开发内容：

- 编码完成后新增 `技术实现说明.md`，记录实际改动文件、关键事务顺序和错误码。
- 验收完成后新增 `阶段完成记录.md`，记录测试命令、结果、未完成项和跨线联测结论。
- 更新本计划中与实际实现不一致的文件名或任务顺序。

完成标准：

- 文档不把 B/C 线能力写成 A 线已完成。
- 验证命令在 `platform` 目录执行 `mvn -q test`。

## 6. 测试计划

单元测试：

- `RuntimeNodeAdvancerTest` 覆盖 `OR_SIGN` 创建任务组和多条任务。
- `TaskGroupRepositoryIntegrationTest` 或现有 Repository 测试覆盖 `completeOrSignGroup` 的 ACTIVE 条件、lock_version 条件和重复完成失败。
- `DefaultProcessRuntimeServiceTest` 覆盖或签单人成功、同组任务取消、结果 DTO、幂等 replay。

集成测试：

- `M6ExclusiveGatewayRuntimeIntegrationTest` 覆盖条件分支从服务入口到数据库回滚。
- `M6OrSignWorkflowIntegrationTest` 覆盖开始、申请节点、或签节点、后续节点、结束节点全链路。
- `M6OrSignConcurrencyIntegrationTest` 覆盖两个候选人并发审批同一或签组。

回归测试：

- M2 串行流程、并行网关、运行时幂等和并发测试继续通过。
- M5 增强动作测试继续通过，特别是加签临时任务和分组任务失败关闭。
- 查询测试验证取消任务不出现在待办列表，历史列表可追溯取消记录。

执行命令：

```powershell
cd platform
mvn -q test
```

## 7. 验收标准

- 条件分支可通过变量命中指定出线，按 `sort_order` 稳定选择第一条命中边。
- 条件分支无命中时走唯一默认出线；无默认出线时返回 `FLOW_GATEWAY_NO_MATCH` 并回滚事务。
- `OR_SIGN` 节点创建 1 个或签任务组和 N 条候选任务。
- 任一候选人完成或签任务后，任务组进入 `COMPLETED`，其余开放任务进入 `CANCELED`。
- 或签并发审批只允许一个请求获得推进权，只创建一组后续任务。
- 或签成功后可继续进入普通用户任务、条件网关、并行网关或结束节点。
- 相同 `operationId` 重试返回首次成功结果，不重复推进。
- 分组任务上的驳回、撤回、转办、加签、直送仍按当前策略拒绝。
- `platform` 目录执行 `mvn -q test` 通过。

## 8. 风险与处理

| 风险 | 处理方式 |
| --- | --- |
| 或签和会签共用 `process_task_group`，容易混淆完成语义 | 新增 `completeOrSignGroup`，只服务 `group_type=OR_SIGN`；保留 `incrementCompletedCount` 给会签/加签计数 |
| 或签取消同组任务后回调或历史重复 | 所有写入放在同一事务内，成功结果通过 `operationId` replay，不重复执行取消和推进 |
| 当前 `TaskActionResult` 没有 `canceledTaskIds` | A 线不扩公共 DTO；通过 `archivedTasks` 记录完成和取消轨迹。如产品强制要求字段，需作为跨线契约变更单独确认 |
| `EnhancedTaskActionCoordinator` 与或签任务组互相误判 | 加签组继续用 `COUNTERSIGN + branchStateJson.purpose=ADD_SIGN` 识别；或签只处理 `group_type=OR_SIGN` |
| 条件分支已部分实现，重复改动可能引入回归 | 条件分支以补测试和修缺陷为主，不重写表达式解析和路由结构 |
| 并发测试在 SQLite 上不稳定 | 复用现有 M2 并发测试写法，使用独立连接和有限等待，断言最终数据库状态 |

## 9. 建议执行顺序

1. 先补条件分支服务级集成测试，确认当前实现真实覆盖 M6 验收。
2. 新增 `TaskGroupRepository.completeOrSignGroup` 和同组任务读取方法，并完成 Repository 测试。
3. 修改 `RuntimeNodeAdvancer`，实现 `OR_SIGN` 创建任务组和多任务。
4. 修改 `DefaultProcessRuntimeService`，实现或签唯一推进、取消同组任务、历史归档和回调复用。
5. 补或签单流程、并发、幂等和回归测试。
6. 与 B 线确认会签和加签测试无回归，与 C 线确认查询展示语义。
7. 在 `platform` 目录运行 `mvn -q test` 并记录结果。
