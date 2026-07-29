# 实习生 B：M4 附件运行时管理编码计划

## 1. 基线与目标

以 `develop@0adc3d3` 为开发基线。M4 完成实例附件、任务附件、模板校验、权限控制、查询下载、逻辑删除，并接通 Runtime、Starter 和独立 REST。

直接复用现有能力，不重复开发：

- 保留现有 `AttachmentService`、附件 DTO/Request、`FileStorageProvider`、`AttachmentAccessProvider` 和失败关闭的 `AttachmentAccessGuard`。
- 保留 M1 的附件模板、定义附件配置及实例固化的 `attachment_config_id`。
- 保留 M2/M3 的运行时事务、任务 CAS、幂等执行器，以及 `startAndSubmit`、`submitTask` 已有附件调用点。
- 替换 Starter 的 `UnsupportedAttachmentService` 和独立应用的 `StandaloneAttachmentService`，不再保留第二套假实现。
- C 线负责的附件 Repository、文件存储 Mock 应先合入；B 只补充同一 Repository 所需方法，不新建平行实现。

开工前先恢复当前基线测试。现有定义激活回滚断言、`startAndSubmit` 实例 ID 断言、Spring 测试 Bean 冲突等失败使用独立修复提交处理，不计入 M4 交付。

## 2. 公共契约与数据约定

- 保持 `AttachmentService` 六个方法签名不变。
- `AttachmentUploadItem` 增加可选 `fieldCode`，补足元数据写入能力。
- `SaveInstanceAttachmentRequest` 增加必填 `sourceTaskId`、`expectedTaskVersion`。实例附件仍为 `ownerType=INSTANCE`，但使用现有 `process_attachment.task_id` 保存来源任务，以准确判断是否已经进入下一节点。
- `AttachmentQuery` 增加 `attachmentCode`、`fieldCode`；查询必须指定 `instanceId`，可叠加任务、归属类型和编码过滤，默认排除软删除记录。
- 明确 `AttachmentDTO.taskId` 对任务附件表示绑定任务，对实例附件表示上传来源任务。
- 新增附件域错误码：请求非法、附件不存在、必填缺失、格式不允许、文件过大、数量超限、来源任务失效、权限拒绝、存储失败。
- 新增内部操作类型 `ATTACHMENT_UPLOAD`、`ATTACHMENT_DELETE`，不扩展回调/历史使用的 `ActionTypeEnum`。通过 `003_m4_attachment_operations.sql` 更新操作幂等表 CHECK 约束并验证旧数据保留。
- 模板当前没有 MIME 白名单，因此 M4 只校验文件扩展名、实际字节长度和大小上限；`contentType` 仅保存和返回，不虚构 MIME 规则。

## 3. 分步实现

### M4.1 附件持久化能力

在唯一的附件 Repository 中提供：

- 按 ID读取附件，包括读取已删除记录。
- 按实例、任务、归属类型、附件编码、字段编码查询未删除附件，并稳定按上传时间和 ID 排序。
- 按 `instanceId + attachmentCode` 统计全部未删除实例/任务附件。
- 在来源任务仍为活动态且版本匹配时插入附件，防止上传与任务完成并发穿透。
- 在来源任务仍为活动态时原子软删除，写入 `deleted/deletedBy/deletedAt`。
- 在实例、定义硬删除前读取待清理的 `storageKey` 集合。

不修改已有附件表主体结构；实例附件通过既有 `task_id` 固化来源任务。

### M4.2 唯一附件服务实现

实现 `DefaultAttachmentService`，所有入口先通过 `CurrentUserProvider` 获取可信用户，并要求其与请求中的 `operatorUserId` 一致。

上传顺序固定为：

1. 校验请求、实例、来源任务、任务版本及实例归属。
2. 强制校验保存方法与 `ownerType` 一致，调用方不能伪造归属类型。
3. 组装完整 `AttachmentAccessRequest` 并调用 Guard；缺失、拒绝或异常全部返回权限拒绝，且不得访问文件存储。
4. 按实例固化的 `attachment_config_id` 查找附件配置及其冻结模板版本。
5. 校验附件编码、适用节点、文件名扩展名、内容非空、`sizeBytes == content.length`、模板大小上限和 `maxCount`。
6. 调用 `FileStorageProvider.store`，事务内写元数据和幂等成功结果。
7. 文件已保存但数据库回滚时，通过事务同步回调最佳努力删除该存储对象。

独立上传直接使用请求 `operationId`；Runtime 批量附件为每项生成稳定子操作号：

`父operationId:attachment:INSTANCE|TASK:列表下标`

相同操作号同请求只返回首次附件，相同操作号不同请求报幂等冲突。

### M4.3 必填校验与 Runtime 接入

- `checkRequiredAttachments` 按实例固化配置读取当前节点适用项，忽略已软删除附件。
- `required=true` 时数量必须达到 `minCount`；所有配置同时检查不得超过 `maxCount`。
- 同一附件编码下，实例附件和任务附件共同参与数量统计。
- 结果中的缺失编码和错误信息按配置 `sortOrder` 稳定返回。
- 校验入口同样验证可信用户，并以 `VIEW` 动作经过附件授权 Guard。
- 改造 `startAndSubmit`：创建申请任务后，将其 ID/版本写入实例附件保存请求；上传和必填校验仍发生在申请任务 CAS 完成前。
- 改造 `submitTask`：批量任务附件使用稳定子操作号；保存、校验、任务 CAS、历史归档、下一节点和父操作成功结果继续位于同一事务。
- 附件失败必须回滚实例、任务、历史、回调、附件元数据和幂等记录；外部文件通过回滚补偿清理。
- 不重写运行时主状态机、定义加载器或现有任务权限校验。

### M4.4 查询、下载与删除

- 查询前使用 `VIEW` 授权；下载读取元数据后使用 `DOWNLOAD` 授权，再调用 `FileStorageProvider.load`。
- 下载响应以平台元数据为准，文件 SPI 只提供内容；已删除附件不可查看或下载。
- 删除前使用 `DELETE` 授权，并通过 Repository 原子确认来源任务仍为 `ACTIVE/CLAIMED`。
- 来源任务已经完成、取消或不存在时拒绝删除，从而落实“未进入下一节点前可删除”。
- 删除只软删除元数据，保留文件名、存储键、上传人和删除信息，不破坏历史轨迹。
- 数据库提交后最佳努力调用 `FileStorageProvider.delete`；失败只记录日志，不撤销软删除。再次删除已软删除记录时视为幂等成功，并再次尝试清理外部文件。
- `deleteInstance` 和 `deleteDefinition` 在物理清理附件元数据前收集存储键，事务提交后统一最佳努力删除文件；不得把外部存储调用放进数据库级联删除过程。

### M4.5 Starter 与 REST 接通

- Starter 使用 `@ConditionalOnMissingBean` 装配真实附件 Service、唯一附件 Repository和事务管理器，允许宿主覆盖。
- Starter 未提供 `AttachmentAccessProvider` 时继续默认拒绝全部访问；不能因开启 Mock 而隐式放行。
- 将现有内存文件存储从 Starter 私有嵌套类整理为可复用 Mock，Starter 与独立应用共用，不复制实现。
- 独立应用显式装配仅用于本地调试的授权 Mock；生产后备策略仍为拒绝。
- 新增附件 REST 薄适配，落实设计文档中的五个路径：实例上传、任务上传、列表查询、下载和删除。
- REST 继续使用现有 JSON DTO，`byte[]` 按 Base64 传输；路径 ID 覆盖请求中的对应 ID，冲突时返回请求非法。
- 附件异常分别映射为 HTTP 400、403、404、409 和存储故障的 502。
- 暂不向 `ProcessInstanceDetailDTO` 再复制附件列表；详情页通过统一附件查询接口获取，避免第二套聚合逻辑。

## 4. 测试与验收

- 契约测试：新增字段、错误码、操作类型、JSON/Base64 序列化和 Java 8 兼容。
- Repository 集成测试：过滤与排序、软删除、来源任务门禁、最大数量、任务完成并发、旧库迁移。
- 权限测试：允许、拒绝、SPI 缺失、SPI 异常；拒绝路径断言文件存储和数据库均无调用。
- 上传测试：实例/任务主路径、实例与任务不匹配、版本冲突、非法扩展名、伪造大小、超限、禁用模板历史兼容。
- 必填测试：缺失、达到 `minCount`、超过 `maxCount`、删除后重新缺失、非适用节点不校验。
- 事务测试：文件保存后数据库失败触发补偿；父 Runtime 回滚不留附件；成功重放不重复写元数据。
- 删除测试：活动来源任务可删、进入下一节点后拒绝、元数据保留、文件删除失败不回滚、重复删除重试文件清理。
- Runtime 集成测试：入金申请银行回单作为实例附件保存；缺失回单时不完成申请任务；任务附件可独立上传并在任务完成前查询。
- 装配测试：Starter 可注入真实 `AttachmentService`，宿主覆盖有效，缺失授权 SPI 默认拒绝；独立 REST 完成上传—查询—下载—删除闭环。
- 最终在 `platform` 和仓库根目录分别执行 `mvn -q test`，全部通过后更新实习生 B 的技术实现说明和阶段完成记录。

## 5. 已确认边界

- B 负责完整接通 Runtime、Starter 和独立 REST；C 的 Repository、文件存储 SPI 与 Mock 作为依赖复用。
- 删除门禁采用“固化来源任务”方案。
- 元数据软删除是权威结果，外部文件在事务提交后最佳努力删除。
- 附件审计仍归 M5：M4 保留 `operationId`、附件 ID、操作者和删除信息，不新建第二套审计 Writer。
- 消息推送、提醒、已阅、委托和高级任务动作不进入本计划。
