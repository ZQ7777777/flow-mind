# 实习生A：M4 组织架构 SPI 与审批人解析 SPI 编码计划

阶段：M4 SPI 与附件  
负责人：实习生A（徐雨新）  
更新日期：2026-07-27  
参考文档：《流程平台技术路线_v5.2》《流程平台设计与接口文档_v4》  
技术基线：Java 8、Spring Boot 2.7.18、Spring JDBC、SQLite

## 1. 目标与边界

根据技术路线 v5.2，M4 阶段 A 线只负责组织架构 SPI 和审批人解析 SPI。目标是在不引入具体业务组织数据的前提下，让流程运行时可以通过平台公共 SPI 解析用户任务的候选审批人，并为 B、C 两线的附件能力集成提供稳定的审批人边界。

A 线 M4 交付范围：

- 完善 `OrganizationProvider` 组织架构 SPI 的契约边界。
- 完善 `ApproverResolver` 审批人解析 SPI 的请求、返回和异常语义。
- 提供基于 `OrganizationProvider` 的审批人解析核心实现。
- 将审批人解析接入运行时任务创建路径。
- 补充组织架构 SPI 和审批人解析 SPI 的单元测试、运行时回归测试。

A 线 M4 不交付以下内容：

- 不实现实例附件、任务附件、附件上传、附件下载、附件删除和附件权限校验，这些属于 B 线。
- 不新增附件元数据表、附件 Repository、文件存储 SPI 实现、消息推送 SPI 和生产 Mock 实现，这些属于 C 线。
- 不实现 M5 的节点监听配置、条件表达式配置、会签、或签、驳回、直送等增强动作。
- 不引入真实组织架构系统、真实网络调用、多数据库适配或 Spring Boot 3.x / Jakarta 依赖。

## 2. 与 B、C 线的分工边界

### 2.1 与实习生B的边界

B 线负责附件运行时管理，包括实例附件、任务附件和附件权限校验。A 线只提供“某个节点应由哪些候选审批人办理”的解析结果，不判断附件是否可见、可下载、可删除，也不修改附件操作请求和附件服务。

A 线可以在测试中构造任务候选人，但不得实现附件权限矩阵，不得新增附件运行时状态流转。

### 2.2 与实习生C的边界

C 线负责文件存储 SPI、附件元数据、消息推送 SPI 和 Mock 实现。A 线可以定义和使用组织架构 SPI、审批人解析 SPI，但不得在 `platform-core/src/main/java/com/flowmind/platform/mock` 中新增本地组织 Mock 或审批人 Mock。

A 线测试如需组织数据，应在 `src/test/java` 内使用 test-scope Fake 或 Stub，不能将测试数据实现放入生产 `mock` 包。

## 3. 当前代码基线

当前可复用代码：

- `platform-core/src/main/java/com/flowmind/platform/api/spi/OrganizationProvider.java` 已定义部门、用户、角色相关查询方法。
- `platform-core/src/main/java/com/flowmind/platform/api/spi/ApproverResolver.java` 已定义审批人解析入口。
- `platform-core/src/main/java/com/flowmind/platform/api/request/ApproverResolveRequest.java` 已承载流程定义、实例、节点、审批规则、发起人和变量信息。
- `platform-core/src/main/java/com/flowmind/platform/core/runtime/ApproverResolveRequestFactory.java` 已负责从节点配置构造审批人解析请求。
- `ApproverRuleTypeEnum` 已包含 `USER`、`STARTER`、`DEPARTMENT`、`ROLE`、`ROLE_IN_DEPARTMENT`、`APPROVER_EXPRESSION`。

当前需要谨慎处理的点：

- `APPROVER_EXPRESSION` 属于表达式能力；后续按《实习生A_完整技术文档》第 7 节补齐受限表达式解析，但不得允许任意代码执行。
- 审批人解析结果为空时，不能创建无候选人的活动任务。
- SPI 契约属于公共 API，不能暴露 persistence 包类型。
- 所有新增生产代码必须兼容 Java 8。
- 本计划仅补齐非 Mock 能力；本地 Mock 组织数据仍按第 2.2 节边界处理，不纳入 A 线后续编码任务。

## 4. 微任务拆分

### M4-A.1 组织架构 SPI 契约校验

开发内容：

- 检查 `OrganizationProvider` 当前方法是否满足 M4 审批人解析需求。
- 明确以下方法的输入输出语义：
  - `listDepartments()`；
  - `listUsersByDepartment(String departmentId)`；
  - `listUsersByRole(String roleCode)`；
  - `listUsersByRoleAndDepartment(String roleCode, String departmentId)`；
  - `findUser(String userId)`；
  - `findDepartment(String departmentId)`。
- 约定 SPI 返回空集合表示未找到可用用户，不能返回 `null`。
- 约定 `findUser`、`findDepartment` 返回 `Optional.empty()` 表示不存在。

完成标准：

- SPI 方法不依赖平台持久化实体。
- SPI Javadoc 明确空值、空集合和典型使用场景。
- 公共 SPI 契约测试覆盖组织架构 SPI 不暴露 persistence 类型。

### M4-A.2 审批人解析请求契约完善

开发内容：

- 检查 `ApproverResolveRequest` 是否能完整表达节点审批规则。
- 补充 `multiInstanceMode` 字段，取值为 `SINGLE`、`OR_SIGN`、`COUNTERSIGN`，由 `ApproverResolveRequestFactory` 从节点配置写入。
- 明确 `approverRuleConfig` 的 JSON 语义：
  - `USER` 使用 `userIds`；
  - `DEPARTMENT` 使用 `departmentId`；
  - `ROLE` 使用 `roleCode`；
  - `ROLE_IN_DEPARTMENT` 使用 `roleCode` 和 `departmentId`，如部门缺省可使用发起人部门；
  - `STARTER` 使用 `starterUserId`；
  - `APPROVER_EXPRESSION` 使用 `expression`，只能读取白名单上下文。
- 保持请求对象只承载数据，不放入业务判断。

完成标准：

- 请求字段 Javadoc 能说明业务含义和配置格式。
- `multiInstanceMode` 在请求对象、构造器、访问器和工厂中完整透传。
- 缺少必要配置时，后续解析器能返回明确业务异常。
- 不新增附件、文件、消息相关字段。

### M4-A.3 默认审批人解析实现

开发内容：

- 新增基于 `OrganizationProvider` 的审批人解析实现，建议命名为 `DefaultApproverResolver` 或沿用现有运行时命名约定。
- 支持以下规则：
  - `USER`：按配置的用户 ID 查询用户，并过滤不存在用户；
  - `STARTER`：按发起人用户 ID 查询用户；
  - `DEPARTMENT`：按部门 ID 查询该部门用户；
  - `ROLE`：按角色编码查询用户；
  - `ROLE_IN_DEPARTMENT`：按角色编码和部门 ID 查询用户；
  - `APPROVER_EXPRESSION`：实现受限表达式解析，只允许读取 `starterUserId`、`starterDeptId`、`variables` 等白名单上下文，并在表达式解析后通过 `OrganizationProvider` 查询用户。
- 对解析结果按用户 ID 去重，并按用户 ID 稳定排序。
- 校验并保留多人模式语义：`OR_SIGN`、`COUNTERSIGN` 返回全部审批人；`SINGLE` 遇到多个审批人时按冻结规则确定稳定结果，不能随机选人。
- 将空结果、非法规则、组织数据异常、表达式非法统一映射为 `FLOW_APPROVER_RESOLVE_FAILED` 对应的运行时错误码。

完成标准：

- 正常规则能返回 `List<UserDTO>`。
- `APPROVER_EXPRESSION` 能在受限上下文内解析，不执行任意代码。
- 配置缺失、规则未知、组织 SPI 返回空审批人时抛出可观察业务异常。
- 实现中不硬编码本地组织数据。
- 实现中不调用附件、文件、消息 SPI。

### M4-A.4 运行时任务创建接入

开发内容：

- 检查 M2/M3 的任务创建链路，定位活动任务生成前的审批人解析位置。
- 使用 `ApproverResolveRequestFactory` 构造请求。
- 调用 `ApproverResolver` 获取候选审批人。
- 将候选审批人的用户 ID 和名称写入活动任务上下文或任务实体字段。
- 按 `multiInstanceMode` 处理解析结果：`SINGLE` 使用稳定单人候选，`OR_SIGN`、`COUNTERSIGN` 保留全部候选人。
- 审批人解析失败时，中断本次流转并保持已有幂等、事务和乐观锁规则。

完成标准：

- 启动流程或办理任务进入用户任务节点时，候选审批人来自 SPI 解析结果。
- 审批人为空时不创建无候选人的活动任务。
- `OR_SIGN`、`COUNTERSIGN` 不因运行时校验被拒绝，候选人列表完整传递给后续任务创建逻辑。
- 不修改 B 线流程实例删除、终止和附件运行时逻辑。
- 不绕过已有 `operationId` 幂等和任务版本校验。

### M4-A.5 单元测试

开发内容：

- 为默认审批人解析实现新增单元测试。
- 使用 test-scope Fake `OrganizationProvider`，不要新增生产 Mock。
- 覆盖以下场景：
  - 指定用户解析成功；
  - 发起人解析成功；
  - 指定部门解析成功；
  - 指定角色解析成功；
  - 指定部门内角色解析成功；
  - 重复用户去重；
  - 按用户 ID 稳定排序；
  - `SINGLE` 多人结果按冻结规则稳定处理；
  - `OR_SIGN`、`COUNTERSIGN` 保留全部审批人；
  - 必填配置缺失；
  - 组织 SPI 返回空用户；
  - `APPROVER_EXPRESSION` 使用允许上下文解析成功；
  - `APPROVER_EXPRESSION` 访问未授权上下文或非法表达式时失败。

完成标准：

- 测试名称清晰表达条件和预期。
- 测试不依赖真实网络、真实组织系统或共享数据。
- 测试覆盖正常路径、边界条件和异常路径。
- 回归测试覆盖当前发现的非 Mock 缺口：`multiInstanceMode` 透传、表达式解析、稳定排序、多人模式保留。

### M4-A.6 运行时回归测试

开发内容：

- 在运行时测试中构造包含用户任务的最小流程定义。
- 注入 Fake `ApproverResolver` 或 Fake `OrganizationProvider`。
- 验证任务创建后的 `candidateUserIds` 和审批人名称来自解析结果。
- 验证解析为空或异常时，流程不会生成错误活动任务。
- 增加 `OR_SIGN`、`COUNTERSIGN` 到达用户任务节点的回归用例，确认运行时不会因非 `SINGLE` 模式提前失败。

完成标准：

- 串行流程仍可跑通到审批节点。
- 审批人解析失败路径有明确断言。
- 多人审批模式下候选人列表与解析结果一致。
- 不依赖 B/C 附件功能完成状态。

### M4-A.7 非 Mock 待补齐项清单

以下事项来自对照《实习生A_完整技术文档》第 7 节后的差距审查，后续开发按本清单补齐；本清单不包含本地 Mock 组织数据任务。

- 在 `ApproverResolveRequest` 中补充 `multiInstanceMode`，并由 `ApproverResolveRequestFactory` 从节点配置完整写入。
- 补齐 `UserDTO` 的部门、角色、有效状态等公共返回字段，或明确提供等价公共字段，确保 `resolveApprovers` 返回结果满足第 7.2 的最小用户信息要求。
- 调整 `DefaultApproverResolver` 去重逻辑，避免重新构造只含用户 ID 和名称的 `UserDTO` 导致部门、角色、有效状态丢失。
- 将审批人结果从“保持 SPI 返回顺序”调整为“按用户 ID 去重并稳定排序”，并补充排序回归测试。
- 实现 `APPROVER_EXPRESSION` 的受限解析能力：仅允许读取白名单上下文，解析结果必须继续通过 `OrganizationProvider` 查询用户，禁止脚本、反射、类加载、系统属性、文件、网络等任意代码执行能力。
- 统一异常语义：结果为空、规则非法、组织数据异常、表达式非法时，对运行时调用方表现为 `FLOW_APPROVER_RESOLVE_FAILED`，避免暴露为条件表达式错误或节点配置错误。
- 放开运行时 `SINGLE` 之外的审批人解析入口校验，使 `OR_SIGN`、`COUNTERSIGN` 可保留全部审批人并交给后续运行时任务组逻辑处理。
- 补充单元测试和运行时回归测试，覆盖上述非 Mock 缺口。

## 5. 集成注意事项

- 如果 C 线后续提供本地 Mock 组织数据，A 线只消费 `OrganizationProvider`，不关心 Mock 数据来源。
- 如果 B 线附件权限需要判断当前用户是否为候选人或办理人，应通过已有任务查询结果或任务字段读取，不要求 A 线提供附件权限 API。
- 如果审批人规则配置格式需要调整，必须同步 B、C 两线，避免流程定义样例和验收用例不一致。
- 如果运行时已有审批人解析入口，不新增重复入口，应优先接入现有 `ApproverResolveRequestFactory` 和 `ApproverResolver`。

## 6. 验收标准

- `OrganizationProvider` 和 `ApproverResolver` 契约清晰，且不暴露持久化实现类型。
- 用户任务节点能通过审批人规则解析出候选审批人。
- `ApproverResolveRequest` 包含并透传 `multiInstanceMode`。
- `USER`、`STARTER`、`DEPARTMENT`、`ROLE`、`ROLE_IN_DEPARTMENT`、`APPROVER_EXPRESSION` 均有单元测试覆盖。
- `APPROVER_EXPRESSION` 只能读取允许上下文，不允许任意代码执行。
- 解析结果按用户 ID 去重并稳定排序，且保留用户部门、角色、有效状态等公共字段。
- `SINGLE`、`OR_SIGN`、`COUNTERSIGN` 的审批人结果处理符合冻结规则和完整技术文档第 7 节要求。
- 审批人为空、配置缺失、组织数据不存在时，不创建无候选人的活动任务。
- 运行 `platform` 目录下的 `mvn -q test` 通过。

## 7. 建议执行顺序

1. 先补契约 Javadoc 和公共 SPI 契约测试。
2. 补齐 `ApproverResolveRequest.multiInstanceMode`、`UserDTO` 公共字段和对应契约测试。
3. 再完善默认审批人解析器的表达式、排序、异常映射和多人模式处理。
4. 然后接入运行时任务创建链路，放开 `OR_SIGN`、`COUNTERSIGN` 的解析入口。
5. 最后补运行时回归测试并执行 `mvn -q test`。
