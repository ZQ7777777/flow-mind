# 实习生 B：M1 流程定义发布校验与缓存计划

## 范围与决策

M1 的 B 线负责发布校验、校验期图索引、完整定义缓存及相关测试。

- 完整定义只通过 `ProcessDefinitionService#getDefinition(String definitionId)` 获取，输入为正式 `ProcessDefinitionDetailDTO`。
- 不新增读取 port、快照 DTO、节点/连线包装类型或第二套定义图模型。
- `saveGraph(String definitionId, SaveProcessGraphRequest request)` 是 A 负责的整图持久化写操作，不属于 B 的图索引。
- 发布校验增量增强现有 `core.validation.DefinitionModelValidator`。
- `DefinitionGraphIndex` 是 `core.validation` 内包级私有辅助类，仅服务单次校验。
- `ProcessDefinitionCache` 位于 `core.definition`，缓存正式详情 DTO 的深层防御性副本。
- `ProcessDefinitionService` 接口保留在 `api.service`；不新增与 `api` 平行的 `service` 包。

M1 不实现定义 CRUD、表单/附件持久化、发布/激活状态变更、实例启动、任务推进、表达式求值或并行运行。

依据优先级：需求文档 → 设计与接口文档 v4 → 技术路线 v5.2 → B 总计划。

## 目标结构

```text
platform-core/src/main/java/com/flowmind/platform
├── api/service/ProcessDefinitionService.java
└── core
    ├── definition/ProcessDefinitionCache.java
    └── validation
        ├── DefinitionModelValidator.java
        └── DefinitionGraphIndex.java   # 包级私有
```

调用关系：

```text
发布校验
ProcessDefinitionService.validateForPublish(definitionId)
  -> getDefinition(definitionId)
  -> DefinitionModelValidator.validate(detailDTO)
       -> DefinitionGraphIndex
  -> ValidationResult

定义写侧
ProcessDefinitionService / C 的扩展配置实现
  -> 数据库事务提交
  -> ProcessDefinitionCache.invalidate(definitionId)

运行时读侧（M2 接入）
ProcessRuntimeService
  -> ProcessDefinitionCache
  -> 未命中时 captureGeneration(definitionId)
  -> 调用 ProcessDefinitionService.getDefinition
  -> 校验 PUBLISHED + ACTIVE
  -> put(detailDTO, validationResult, generation)
```

`getDefinition` 和 `validateForPublish` 必须读取当前事实数据，不以运行时缓存替代数据库查询。

## 依赖与统一规则

| 依赖 | B 的处理 |
| --- | --- |
| A M1：`getDefinition` 真实实现和正式定义 DTO | 直接复用，不实现 `saveGraph` |
| C M1：详情 DTO 中的表单与附件配置 | 配置可读取后完成扩展校验 |
| A M3：发布、激活、停用、归档及事务后失效 | 不阻塞缓存组件；真实状态联测转入 M3 门禁 |
| B M2：`ProcessRuntimeService` 实现 | 接入缓存读侧和未命中加载 |

- Java 8、Spring Boot 2.7.18；禁止 Java 9+ API 和 `record`。
- 索引和校验器不得修改输入 DTO、访问 Repository 或执行状态变更。
- 校验问题按规则、`sortOrder` 和编码保持稳定顺序。
- 每个微任务必须有测试、Maven 验证、子 agent 只读审查和独立提交。
- 不夹带实习生 A/C 或用户的未提交修改。

## 微任务

### B1.1：清理错误读取层并构建只读图索引

删除已废弃的 `RuntimeDefinitionReadPort`、`RuntimeDefinitionInput`、`RuntimeNode`、
`RuntimeEdge`、`RuntimeDefinitionGraph`、独立 parser 及对应测试。随后在
`core.validation` 增加包级私有、`final` 的 `DefinitionGraphIndex`，直接使用
`ProcessDefinitionDetailDTO` 中的正式节点和连线，建立：

- 节点、连线编码索引；
- 入线、出线邻接表；
- 按 `sortOrder`、编码稳定排序的只读视图；
- 开始、结束和网关节点读取视图。

坐标仅保留在 DTO 中，不参与拓扑方向和排序。索引不复制领域类型，不访问 Service、缓存或数据库。

完成条件：

- 打乱输入顺序后索引和校验问题顺序稳定；
- `null` 集合、空集合和集合内 `null` 元素不会导致未捕获异常；
- 索引集合不可修改，输入 DTO 不被改写；
- 生产代码不存在上述废弃类型；
- 执行模块测试并通过子 agent 审查。

建议提交：`refactor: index process definition graph for validation`

### B1.2：补齐基础发布校验

先用特征测试冻结 `DefinitionModelValidator` 已有行为，再增量补齐：

1. 恰好一个开始节点，至少一个结束节点；
2. 节点、连线编码分别唯一，连线两端存在；
3. 所有有效节点可从开始节点到达并能到达结束节点；
4. `START`、`USER_TASK` 恰好一条有效出线；
5. 用户任务配置审批人规则；
6. 网关不配置审批人规则或多人处理模式。

使用正式 `ValidationResult` 聚合全部问题。空白编码、断裂引用和空元素只产生校验问题，不抛出未捕获异常。

建议提交：`feat: validate publishable definition graphs`

### B1.3：补齐网关拓扑校验

- `EXCLUSIVE_GATEWAY` 至少两条出线、最多一条默认出线，非默认出线必须有条件表达式；
- `PARALLEL_SPLIT_GATEWAY` 至少两条无条件出线，并指向正确的配对汇聚网关；
- `PARALLEL_JOIN_GATEWAY` 至少两条入线、恰好一条出线，并与分支网关双向配对；
- 分支网关的每条路径都能到达配对汇聚网关；带环图必须有限结束。

M1 只校验配置和拓扑，不求值表达式或执行并行汇聚。

建议提交：`feat: validate gateway topology`

### B1.4：补齐表单与附件配置校验

在 C 的真实配置可通过 `getDefinition` 读取后，实现当前 DTO 可以判断的规则：

- 表单字段编码不重复；
- 同一 `attachmentConfigId` 内附件模板 ID、附件编码分别不重复；
- 附件适用节点存在；
- `required=true` 时 `minCount >= 1`，且非空的 `minCount <= maxCount`。

生效配置和被引用模板版本不可原地修改，需要比较持久化旧状态，由 C 的写事务负责，B 不重复实现。

建议提交：`feat: validate definition extension configuration`

### B1.5：实现完整定义缓存

在 `core.definition` 实现线程安全的 `ProcessDefinitionCache`：

- 键为定义 ID；版本保留在详情中，M2 运行时与实例快照核对，值为 `ProcessDefinitionDetailDTO` 深层防御性副本；
- 提供 `get`、`captureGeneration`、`put` 和按定义 ID 失效；令牌仅防止失效前的在途加载回写旧副本，不调用 Service 或 Repository；
- 写入和返回时复制 DTO、元素及嵌套集合；
- 草稿、未发布、未激活、校验失败或加载异常不得写入；
- 同一定义内容变化后失效；保存另一未激活草稿不影响当前激活定义；
- 失效发生在事务提交后，回滚不清除有效缓存；
- 同一定义的写入与失效串行化，失效返回后不得暴露旧加载结果；
- 不引入 Redis、Caffeine 或新的读取 port。

`ProcessDefinitionService` 写侧负责失效；`ProcessRuntimeService` 在 M2 负责缓存读取、未命中加载和状态门禁。M1 不提前实现运行时 Service。

完成条件：覆盖命中、缺失、版本隔离、按定义失效、状态门禁、深层复制、并发读写和在途旧加载。A 的真实状态生命周期联测转入 M3。

建议提交：`feat: cache active process definition details`

### B1.6：A/B/C 联合测试与收口

在 A/C 的 M1 实现合入后使用真实 `ProcessDefinitionService`：

1. `saveGraph` 保存的节点、连线、坐标和顺序能由 `getDefinition` 完整读取；
2. `validateForPublish` 对合法图通过，对错误图返回完整且稳定的问题；
3. C 的表单和附件配置参与发布校验；
4. 缓存通过正式 DTO 验证状态门禁、版本隔离、防御性复制和失效；
5. M2 缓存读侧、A M3 状态生命周期列入后续强制接入清单。

执行：

```powershell
mvn -q -pl platform/platform-core -am test
mvn -q test
```

子 agent 重点审查重复类型、Service 分层、校验稳定性、缓存复制和事务后失效时机。

建议提交：`test: verify M1 process definition integration`

## 验收门禁

- 唯一读取入口为 `getDefinition`，不存在 B 自建读取 port、快照 DTO 或平行 Service 包；
- `DefinitionGraphIndex` 仅为校验包内部辅助类；
- 发布校验覆盖基础结构、网关、表单和附件规则；
- `ProcessDefinitionCache` 不参与管理端事实查询，只保存已发布且激活的完整详情副本；
- M1 验证缓存组件和失效协作；M2 接入读侧，M3 接入真实状态生命周期；
- 模块测试、根测试和子 agent 审查均通过。
