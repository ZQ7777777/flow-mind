# 实习生 B：M0 契约冻结 Vibe Coding 计划

## 产物与边界

新建 `doc/实习生B/m0-vibe-coding-plan.md`，作为 M0 专项计划；不修改现有总计划。

B 仅创建运行时 DTO、动作结果 DTO、幂等字段与 DTO 契约测试。不得实现 Repository、SQLite 表结构、流程运行逻辑、SPI、Starter 自动装配、`WorkflowEvent` 或共享状态枚举。

所有 DTO 使用 Java 8 JavaBean：无 Lombok、无 `record`、无 Java 9+ API。

M0 不实现流程流转、持久化或 SPI 等业务功能，但 DTO 是后续 A/C 模块依赖的公开契约。因此，B 必须编写并持续维护 **DTO 契约单元测试**。该测试验证继承关系、字段/泛型类型、JavaBean 无参构造与 getter/setter，以及结果对象的表达能力；不验证流程流转、数据库写入或实际幂等执行。

“请求夹具”仅指测试使用的标准有效请求/结果样例，并非替代运行时参数校验。夹具中的 `operationId` 必须为非空值、任务级夹具必须提供 `expectedTaskVersion`；M0 不新增 Bean Validation、Service 校验或业务校验逻辑。

## 并行与等待

B **无需等待 A/C 才能开工**，先按 v4 文档编写不依赖共享类型的请求基类、动作请求和结果骨架，并在每个微任务完成时同步委派 AI 补充或更新对应的 DTO 契约单元测试。

但以下是**合并前强制等待点**：

| 依赖方 | 必须提供 | B 的处理 |
|---|---|---|
| A | `api.enums` 中的状态/动作枚举，以及实例、任务、历史模型字段确认 | B 在 A 合并前不创建替代枚举；结果 DTO 中相关字段待 A 合并后改为正式枚举类型。 |
| C | `WorkflowEvent`、SPI、Starter Service 接口及幂等测试基线 | B 在 C 合并前不创建替代事件/SPI；仅准备请求夹具，待 C 合并后完成编译与联合测试。 |

B 在自己的分支持续小提交；A/C 合并后统一 rebase。若字段或包名不一致，以 v4 设计文档为准，修正 B 分支，不在主干保留临时类型。

## 微任务与编码提示词

1. **B0.1：请求基类**

   创建 `OperationRequest`（`operationId`）与 `TaskOperationRequest`（`taskId`、`expectedTaskVersion`、`operatorUserId`、`comment`）。

   提示词：  
   “在 `platform-core` 创建 Java 8 DTO 基类。所有修改请求继承 `OperationRequest`，任务级请求继承 `TaskOperationRequest`。只写字段、无参构造、getter/setter；不新增枚举、SPI、持久化或业务逻辑。”

   测试与完成条件：  
   AI 同步创建/更新基类契约单元测试，验证 JavaBean 无参构造、`operationId` 继承，以及任务基类的 `taskId`、`expectedTaskVersion`、`operatorUserId`、`comment` 属性。执行 `mvn -q -pl platform/platform-core -am test`；通过后才可提交 B0.1。

2. **B0.2：运行时请求 DTO**

   创建并统一继承基类：`StartProcessRequest`、`UpdateVariablesRequest`、`SubmitTaskRequest`、审批/驳回/退回/撤回/直送/转办/加签/认领/取消认领请求、终止/删除实例请求、跳转/强制办结请求与 `RemindTaskRequest`。

   固定字段：启动请求含流程编码、业务键、标题、发起人、部门、变量；任务提交含变量与附件；节点定向动作含目标节点；转办含目标用户；加签含加签用户；实例操作含实例 ID。`JumpNodeRequest` 仅为实例级操作，不携带任务版本。

   提示词：  
   “按 v4 第 6、7 节创建运行时请求 DTO。所有状态修改请求必须可取得 `operationId`；仅任务级请求可取得 `expectedTaskVersion`。不得把入金等业务字段写入 DTO。”

   测试与完成条件：  
   AI 同步将全部请求类加入参数化契约测试：断言每个修改请求继承或声明 `operationId`，每个任务级修改请求继承 `TaskOperationRequest` 并具备 `expectedTaskVersion`，`JumpNodeRequest` 不具备任务版本。为每类请求提供有效夹具；执行 `mvn -q -pl platform/platform-core -am test` 后提交。

3. **B0.3：运行时结果 DTO**

   创建 `ProcessInstanceDTO`、`TaskDTO`、`HistoryTaskDTO`、`TaskActionResult`、`OperationResult` 与 `AttachmentUploadItem`。

   - `ProcessInstanceDTO` 返回实例基本信息、流程变量和 `createdTasks`。
   - `TaskDTO` 返回任务标识、节点、候选/办理/委托信息、任务组与分支、`taskVersion`。
   - `HistoryTaskDTO` 返回归档动作、意见、变量快照与办理时间。
   - `TaskActionResult` 返回 `operationId`、实例、`archivedTasks`、`createdTasks`、`replayed`。
   - `OperationResult` 返回操作号、目标类型/ID、删除状态、`replayed`。
   - `AttachmentUploadItem` 使用 `byte[] content` 承载上传内容。

   执行顺序：  
   先完成不依赖 A 枚举的字段、集合、无参构造与访问器，并为其补充测试；枚举字段作为 B0.3 的等待子项，待 A 合并正式枚举后再补齐。不得为等待字段创建临时 `String`、重复枚举或兼容别名。

   提示词：  
   “创建运行时结果 DTO，严格按 v4 的结果和回调字段命名。先完成不依赖枚举的字段；仅在 A 已合并正式枚举后补齐对应枚举字段。不得创建临时 String、重复枚举或兼容别名。”

   测试与完成条件：  
   AI 同步测试每个结果 DTO 的 JavaBean 属性与类型，特别验证 `ProcessInstanceDTO.createdTasks`、`TaskDTO.taskVersion`、`TaskActionResult.operationId/archivedTasks/createdTasks/replayed`、`OperationResult` 的删除与重放字段，以及 `AttachmentUploadItem.content` 为 `byte[]`。无枚举依赖部分测试通过后可提交；A 合并后的枚举字段与测试补齐后，再执行 `mvn -q -pl platform/platform-core -am test`。

4. **B0.4：DTO 契约测试与夹具**

   汇总、去重 B0.1～B0.3 已同步创建的请求夹具和参数化契约单元测试。断言所有修改请求都可取得 `operationId`，所有任务级修改请求都可取得 `expectedTaskVersion`；使用有效夹具验证前者非空、后者已赋值；验证结果对象可表达归档任务、新建任务与幂等重放。

   提示词：  
   “只测试 DTO 契约，不实现流程逻辑。以请求类清单为测试数据源，完整覆盖启动、变量更新、提交、审批、驳回、退回、撤回、直送、转办、加签、认领、取消认领、终止、删除、跳转、强制办结、催办请求的幂等字段与任务版本字段。”

   测试与完成条件：  
   确认没有任何请求类只存在于生产代码而未进入参数化测试；执行 `mvn -q -pl platform/platform-core -am test` 后提交 B0.4。

5. **B0.5：与 A/C 对齐并冻结**

   A/C 合并后，B rebase 并完成：正式枚举类型替换、`WorkflowEvent` 使用 B 的任务/历史 DTO、Starter Service 接口编译、C 的幂等测试夹具接入。

   提示词：  
   “不得新增兼容别名或重复类型。修正 B DTO 的包名、字段名和泛型，使其与 A 枚举、C 事件/SPI/Service 接口一次编译通过；补齐因对齐产生的契约测试。”

   测试与完成条件：  
   对齐产生的每项字段或泛型调整必须同步更新契约测试；先执行 `mvn -q -pl platform/platform-core -am test`，再在 rebase 完成后执行根目录 `mvn -q test`。两项均通过才可冻结。

## 验收与合并门禁

- 每个微任务完成后，相关 DTO 契约单元测试随代码提交，并已通过 `mvn -q -pl platform/platform-core -am test`；B0.5 rebase 后根目录 `mvn -q test` 通过，根模块和 `platform-core` 均可编译。
- 所有运行时状态修改请求有 `operationId`；任务级动作有 `expectedTaskVersion`。
- `TaskDTO.taskVersion` 与后续乐观锁字段语义一致。
- `TaskActionResult` 与 `WorkflowEvent` 都能表达归档任务、新建任务和操作号。
- 同一操作号相同请求可表达 `replayed=true`；同号不同请求由 C 的幂等基线断言为冲突。
- 无重复枚举、SPI、事件或 Starter 接口；B 代码不包含流程流转、SQL 或具体业务判断。
