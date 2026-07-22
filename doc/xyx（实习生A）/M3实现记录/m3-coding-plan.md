# 实习生A：M3 流程定义生命周期编码计划

阶段：M3 查询与 Starter  
负责人：实习生A（徐雨新）  
更新日期：2026-07-22  
参考文档：《流程平台技术路线_v5.2》《流程平台设计与接口文档_v4》  
技术基线：Java 8、Spring Boot 2.7.18、Spring JDBC、SQLite

## 1. 目标与边界

根据技术路线 v5.2，M3 阶段 A 线负责流程定义版本治理，包括发布、激活、停用、归档和灰度发布。结合当前代码完成情况，本阶段先落地非灰度生命周期能力：

- 发布：草稿定义通过发布前校验后变为已发布未激活版本。
- 激活：已发布未激活的全量版本成为同流程编码下的默认运行版本。
- 停用：已激活的全量版本停止承接新实例，但不影响已绑定该定义的运行中实例。
- 归档：已发布未激活的全量版本进入归档终态，不能再次激活。
- 灰度：接口保留，M3 当前不实现灰度规则配置、灰度启用和灰度关闭。

A 线 M3 只修改 `platform-core` 的核心服务、Repository 和测试。`platform-starter` 自动装配、本地验收用例和集成测试属于 C 线任务，本计划不实现、不声明完成。

## 2. 与设计文档的一致性

本计划对齐设计文档 12.15 版本治理：

1. 草稿流程定义通过校验后可发布。
2. 已发布流程定义可激活，作为默认运行版本。
3. 已激活流程定义可停用，停用后不再承接新实例。
4. 已停用或不再使用的流程定义可归档，归档后不能再次激活，只能复制出新草稿。
5. 发布、激活、停用、归档均写审计日志。

灰度发布在设计文档中属于完整版本治理能力，但当前用户已明确“先不用实现灰度相关功能”，因此 M3 A 线只保留接口占位，后续灰度实现不得隐式复用全量激活逻辑。

## 3. 当前代码基线

当前可复用代码：

- `ProcessDefinitionService` 已冻结定义生命周期接口。
- `DefaultProcessDefinitionService` 已有创建、保存图、复制、删除、查询、发布前校验和缓存失效能力。
- `DefinitionModelValidator` 已覆盖发布前模型校验规则。
- `OperationIdempotencyService` 与 `process_operation_record.operation_id` 唯一约束已提供写操作幂等能力。
- `ProcessDefinitionCache` 已支持按 `definitionId` 精确失效。
- SQLite schema 已存在 `process_definition` 状态字段、归档字段、审计日志表和同流程全量激活唯一索引。

当前不复用、不新增内容：

- 不新增 starter 自动配置。
- 不新增 REST Controller。
- 不新增灰度规则解析器。
- 不新增多数据库适配。

## 4. 微任务拆分

### M3-A.1 Repository 生命周期原语

开发内容：

- 在 `ProcessDefinitionRepository` 中新增状态条件更新方法：
  - `publish(id, updatedBy)`；
  - `activateFull(id, updatedBy)`；
  - `deactivate(id, updatedBy)`；
  - `deactivateActiveFullByProcessCode(processCode, excludeDefinitionId, updatedBy)`；
  - `archive(id, archivedBy, archivedAt)`；
  - `insertAuditLog(...)`。
- 所有状态更新均带当前状态条件，使用受影响行数表达 CAS 结果。
- 激活只处理全量版本，即 `gray_status = OFF`。

完成标准：

- 发布只允许 `DRAFT + INACTIVE + OFF`。
- 激活只允许 `PUBLISHED + INACTIVE + OFF`。
- 停用只允许 `PUBLISHED + ACTIVE + OFF`。
- 归档只允许 `PUBLISHED + INACTIVE + OFF`。
- 审计日志可写入 `process_audit_log`。

### M3-A.2 Service 生命周期实现

开发内容：

- 在 `DefaultProcessDefinitionService` 中实现：
  - `publish(DefinitionOperationRequest)`；
  - `activate(DefinitionOperationRequest)`；
  - `deactivate(DefinitionOperationRequest)`；
  - `archive(DefinitionOperationRequest)`。
- 每个写操作均校验 `definitionId`、`operatorUserId`、`operationId`。
- 每个写操作均接入 `OperationIdempotencyService.beginOrReplay`。
- 同号同请求成功后重放首次结果，同号不同请求返回幂等冲突。
- 操作成功后写审计日志、标记幂等成功并在事务提交后失效流程图缓存。

完成标准：

- 发布前调用 `validateForPublish`，校验失败时不发布。
- 激活新版本前停用同流程编码下其他全量激活版本。
- 停用后新实例不再按全量激活版本选择命中该定义；已有实例仍按自身绑定定义继续。
- 归档后不可再次激活。
- 灰度状态不是 `OFF` 时拒绝生命周期操作。

### M3-A.3 并发与状态一致性

开发内容：

- 复用幂等表唯一键处理相同 `operationId` 的并发重试。
- 使用 Repository 条件更新处理同一定义的并发发布、停用和归档。
- 依赖 SQLite 部分唯一索引 `uk_process_definition_active_full` 约束同一 `process_code` 只能有一个 `PUBLISHED + ACTIVE + OFF` 全量版本。

完成标准：

- 两个请求同时发布同一定义时，最多一个状态转换成功。
- 两个请求同时停用或归档同一定义时，最多一个状态转换成功。
- 并发激活同一流程编码的不同定义时，数据库最终不会出现两个全量激活版本。

说明：

- 当前没有定义级 `lock_version`，不同 `operationId` 并发激活同一流程编码的不同版本时，语义是最终只有一个 active，不保证先激活者保持 active。
- 若后续要严格串行化版本治理，应引入定义级乐观锁或流程编码级锁。

### M3-A.4 灰度接口保留

开发内容：

- `enableGray` 和 `disableGray` 保留接口签名。
- 当前阶段固定抛出 `UnsupportedOperationException`，异常信息说明灰度不在本次 M3 实现范围。

完成标准：

- 调用灰度接口不会静默成功。
- 测试覆盖灰度接口仍显式不可用。

### M3-A.5 测试

测试内容：

- `DefinitionRepositoryIntegrationTest`
  - 覆盖发布、激活、停用、归档状态更新。
  - 覆盖生命周期审计日志写入。
  - 覆盖激活新版本时停用旧全量版本。
- `DefaultProcessDefinitionServiceTest`
  - 覆盖发布成功和幂等重放。
  - 覆盖发布校验失败。
  - 覆盖激活新版本、停用旧版本和缓存失效。
  - 覆盖停用后新实例选择不再命中该定义。
  - 覆盖归档后不能再次激活。
  - 覆盖灰度接口仍显式未实现。

默认验证命令：

```powershell
mvn -q test
```

执行目录为 `platform`。

## 5. 验收清单

- [x] 生命周期操作不引入业务概念。
- [x] 发布前复用现有流程定义校验。
- [x] 发布、激活、停用、归档均要求 `operationId`。
- [x] 生命周期操作成功后写入 `process_operation_record`。
- [x] 生命周期操作成功后写入 `process_audit_log`。
- [x] 生命周期操作成功后失效 `ProcessDefinitionCache`。
- [x] 同一流程编码最终只保留一个全量激活版本。
- [x] 灰度接口保留但不实现。
- [x] `platform-starter` 自动装配不纳入 A 线完成范围。
- [ ] 后续由 C 线补 starter 自动装配和本地验收用例。
- [ ] 后续灰度发布由 A 线或统一版本治理阶段单独实现。

## 6. 风险与后续建议

| 风险 | 当前处理 | 后续建议 |
| --- | --- | --- |
| 发布前校验与状态更新之间存在并发编辑窗口 | 发布 update 带状态条件，校验仍基于读取时快照 | 如需强一致，补定义级 `lock_version` |
| 并发激活不同版本存在后写覆盖先写语义 | 唯一索引保证不会出现两个全量 active | 如需强串行，补流程编码级锁 |
| 灰度未实现 | 接口显式抛未实现异常 | 后续补灰度规则 JSON 校验、命中选择和关闭语义 |
| Starter 未装配默认定义服务 | A 线不处理，避免越界 | C 线按技术路线统一实现 starter 自动装配 |
