# 实习生 C：M4 文件存储、附件元数据、消息与回调 Mock Vibe Coding 计划

## 产物与边界

本文件作为实习生 C 在 M4 阶段的专项编码计划。M4 只实现附件运行时的底座能力：文件存储 SPI 契约、附件元数据 Repository、本地文件存储 Mock、消息推送 SPI 与记录型 Mock、流程回调 SPI 与记录型 Mock、Starter 和独立应用的装配支撑，以及与 B 线附件运行时服务的联调。

M4 不实现以下内容：

- B 线附件运行时主服务：上传、下载、查询、删除、必填校验和附件权限调用编排。
- B 线运行时主状态机：实例启动、任务提交、审批推进、终止、删除、跳转、强制办结。
- A 线组织架构 SPI、审批人解析 SPI 和任务创建审批人解析。
- M5 审计日志、已阅、委托代办动作、认领/取消认领动作、催办提醒。
- M6 超时扫描、异常告警、并行汇聚和管理员修复。
- 真实 OSS、对象存储、短信、企微、邮件或业务系统消息实现。
- 真实流程回调投递系统实现。

C 线 M4 必须避免创建第二套附件元数据访问逻辑。B 线的 `DefaultAttachmentService` 应只依赖唯一 `ProcessAttachmentRepository`、`FileStorageProvider`、`AttachmentAccessGuard`、`CurrentUserProvider` 和幂等执行器。

每个微任务完成后执行对应测试，并做一次只读自审。不得创建重复 DTO、重复枚举、重复 SPI 或 Starter 专用业务实现。

## 基线优先级

发生描述差异时按以下优先级执行：

1. 当前代码中的公共 DTO、Request、Service、SPI、DDL。
2. `doc/实习生C/M4/执行计划.md`。
3. `doc/实习生B/m4-coding-plan.md`。
4. `doc/xyx（实习生A）/M4实现记录/m4-coding-plan.md`。
5. `doc/流程平台设计与接口文档_v4.md`。
6. `doc/流程平台技术路线_v5.2.md`。
7. `doc/实习生C/实习生C开发文档.md`。

当前代码中已经存在并必须复用：

- `FileStorageProvider`
- `MessagePublisher`
- `WorkflowCallbackHandler`
- `AttachmentAccessProvider`
- `AttachmentAccessGuard`
- `AttachmentService`
- `DefaultAttachmentService`
- `ProcessAttachmentRepository`
- `ProcessAttachmentEntity`
- `InMemoryFileStorageProvider`
- `ProcessDefinitionAttachmentConfigRepository`
- `ProcessAttachmentTemplateRepository`
- `PlatformAutoConfiguration`
- `PlatformStandaloneConfiguration`

## 目标结构

目标结构优先沿用现有包。Mock 放在 `platform-core.mock`，Repository 放在 `persistence.repository`，附件主服务继续由 B 线维护在 `core.attachment`。

```text
platform-core/src/main/java/com/flowmind/platform
├── api/
│   ├── dto/
│   │   ├── FileContent.java
│   │   ├── StoredFile.java
│   │   └── ProcessMessage.java
│   ├── request/
│   │   └── StoreFileRequest.java
│   └── spi/
│       ├── FileStorageProvider.java
│       ├── MessagePublisher.java
│       └── WorkflowCallbackHandler.java
├── core/
│   ├── attachment/
│   │   └── DefaultAttachmentService.java        # B 线主实现，C 只配合底座
│   └── security/
│       └── AttachmentAccessGuard.java
├── mock/
│   ├── InMemoryFileStorageProvider.java
│   ├── RecordingMessagePublisher.java
│   └── RecordingWorkflowCallbackHandler.java
├── persistence/
│   ├── entity/
│   │   └── ProcessAttachmentEntity.java
│   └── repository/
│       └── ProcessAttachmentRepository.java
└── web/
    └── PlatformStandaloneConfiguration.java

platform-starter/src/main/java/com/flowmind/platform/starter
└── PlatformAutoConfiguration.java

platform-core/src/test/java/com/flowmind/platform
├── api/spi/
├── mock/
├── persistence/repository/
└── core/attachment/

platform-starter/src/test/java/com/flowmind/platform/starter
```

推荐数据流：

```text
B 线附件上传请求
  -> DefaultAttachmentService
  -> CurrentUserProvider 获取可信用户
  -> AttachmentAccessGuard 失败关闭授权
  -> ProcessDefinitionAttachmentConfigRepository / ProcessAttachmentTemplateRepository 校验模板
  -> FileStorageProvider.store 保存文件内容
  -> ProcessAttachmentRepository 写 process_attachment 元数据
  -> RuntimeOperationExecutor 保存幂等成功结果
```

附件查询、下载、删除数据流：

```text
查询
  -> AttachmentAccessGuard(VIEW)
  -> ProcessAttachmentRepository.queryActive
  -> AttachmentDTO

下载
  -> ProcessAttachmentRepository.findById
  -> AttachmentAccessGuard(DOWNLOAD)
  -> FileStorageProvider.load
  -> AttachmentDownloadDTO

删除
  -> ProcessAttachmentRepository.findById
  -> AttachmentAccessGuard(DELETE)
  -> ProcessAttachmentRepository.softDeleteWhenTaskOpen
  -> 事务提交后 FileStorageProvider.delete 最佳努力清理
```

## 统一编码规则

- Java 8、Spring Boot 2.7.18。
- 新增公开 API 类型使用 JavaBean，不使用 `record`、Java 9+ API 或新的重复公共类型。
- 文件内容只经 `FileStorageProvider` 保存，数据库只保存 `storageKey` 和附件元数据。
- `process_attachment.task_id` 对任务附件表示绑定任务，对实例附件表示上传来源任务。
- Repository 负责 SQL 访问，Service 负责权限、模板、事务和幂等编排。
- 不在 `DefaultAttachmentService` 里直接拼附件元数据 SQL。
- 查询默认排除 `deleted = 1`，需要读取已删除记录时必须显式说明用途。
- 附件列表排序使用 `uploaded_at ASC, id ASC`，保持稳定。
- 软删除保留文件名、存储键、上传人、删除人和删除时间。
- 外部文件删除失败不得回滚已经提交的元数据软删除。
- `AttachmentAccessProvider` 缺失、拒绝或异常都必须失败关闭。
- 启用文件 Mock 不代表附件授权默认放行。
- Mock 能力只能用于本地验收和测试，生产宿主应提供真实 SPI Bean。
- Starter 和独立应用复用同一套 core Mock，不维护两套实现。
- `MessagePublisher` 只负责消息通知 Mock；`WorkflowCallbackHandler` 才负责流程回调事件 Mock。

## 微任务与编码提示词

### C4.1：对齐 M4 当前代码基线和职责边界

目标：

- 核对 C 线 M4 执行计划与当前代码中的 DTO、SPI、Repository、DDL、Starter 装配是否一致。
- 明确 C 线只补底座能力，不重写 B 线附件主服务。
- 记录现有测试基线。

提示词：

“请只读审查 C 线 M4 执行计划和当前代码。确认 `FileStorageProvider`、`MessagePublisher`、`WorkflowCallbackHandler`、`ProcessAttachmentRepository`、`InMemoryFileStorageProvider`、`AttachmentAccessGuard`、`DefaultAttachmentService` 和 `PlatformAutoConfiguration` 已存在。列出 C 线 M4 需要补齐的 Repository、Mock、Starter 装配和测试缺口。不得重写 `DefaultAttachmentService`，不得新增第二套附件 Repository。”

测试与完成条件：

- 形成缺口清单。
- 明确当前 `RecordingMessagePublisher` 是否仍是 Starter 私有内部类。
- 明确当前 `WorkflowCallbackHandler` 是否仍是不可断言空实现。
- 明确 `process_attachment` 字段是否满足 B 线 M4 计划。
- 执行并记录 `mvn -q -pl platform/platform-core -am test` 的基线结果。

建议提交：`docs: align C M4 attachment foundation gaps`

### C4.2：收口文件存储 SPI 契约

目标：

- 固定 `FileStorageProvider.store/load/delete` 的语义。
- 确认 `StoreFileRequest`、`StoredFile`、`FileContent` 足够支撑上传、下载和删除补偿。
- 公共 SPI 不暴露持久化实现类型。

提示词：

“请为 M4 文件存储 SPI 补齐契约说明和测试。`FileStorageProvider.store(StoreFileRequest)` 返回可持久化的 `storageKey`；`load(storageKey)` 返回文件内容；`delete(storageKey)` 用于事务提交后的最佳努力清理。公共 SPI 不得依赖 `persistence` 包类型，不绑定真实 OSS 或文件系统实现。”

实现要点：

- `store` 入参应包含文件名、内容类型、大小和字节内容。
- `StoredFile.storageKey` 是后续读取和删除的唯一凭据。
- 下载响应中的文件名、内容类型、大小优先使用平台元数据。
- `load` 找不到文件时允许抛明确异常，由上层映射。
- `delete` 不要求强事务语义。

测试与完成条件：

- SPI 契约测试通过。
- 反射测试确认 SPI 方法参数和返回值不属于 `persistence` 包。
- 现有附件服务编译不受影响。

建议提交：`test: cover file storage spi contract`

### C4.3：补齐附件元数据 Repository 能力

目标：

- `ProcessAttachmentRepository` 成为唯一附件元数据访问入口。
- 提供 B 线附件服务所需的原子门禁、稳定查询和存储键收集能力。

提示词：

“请补齐 `ProcessAttachmentRepository` 的 M4 能力。保留 `findById`、`queryActive`、`countActiveByInstanceAndCode`、`insertWhenTaskOpenAndWithinLimit`、`softDeleteWhenTaskOpen`。新增或核查按实例、按定义实例集合收集 `storage_key` 的方法。查询默认排除软删除并按 `uploaded_at ASC, id ASC` 排序。禁止在附件 Service 中绕过 Repository 写 SQL。”

实现要点：

- `findById` 可读取已软删除记录，供删除幂等和清理判断。
- `queryActive(AttachmentQuery)` 支持 `instanceId/taskId/ownerType/attachmentCode/fieldCode`。
- `countActiveByInstanceAndCode` 统计同一实例下未删除的实例附件和任务附件。
- `insertWhenTaskOpenAndWithinLimit` 在一条 SQL 内确认来源任务仍为 `ACTIVE/CLAIMED`。
- `expectedTaskVersion` 为 `null` 时可不校验版本；非空时必须匹配 `lock_version`。
- `softDeleteWhenTaskOpen` 只在来源任务仍活动时软删除。
- 收集 `storage_key` 时不要在 Repository 内调用 `FileStorageProvider.delete`。

测试与完成条件：

- 按 ID 读取测试通过。
- 组合过滤和排序测试通过。
- 软删除后 `queryActive` 不返回该附件。
- 数量统计同时覆盖实例附件和任务附件。
- 来源任务完成后不能写入或删除。
- 版本不匹配不能写入。
- 实例删除和定义删除前可收集待清理 `storage_key`。

建议提交：`feat: complete attachment metadata repository`

### C4.4：完善内存文件存储 Mock

目标：

- `InMemoryFileStorageProvider` 支撑本地上传、下载、删除和回滚补偿测试。
- Mock 行为可预测、线程安全、可重复删除。

提示词：

“请完善 `InMemoryFileStorageProvider`。`store` 生成唯一 `mock://` 存储键并保存文件内容；`load` 返回保存时的内容快照；`delete` 支持重复调用。保存和读取字节数组时做防御性拷贝，避免调用方修改原数组影响已存文件。补充并发保存、读取缺失文件、重复删除和删除后不可读取测试。”

实现要点：

- 内部使用线程安全 Map。
- `store` 保存 `FileContent` 时复制 `byte[]`。
- `load` 返回新的 `FileContent` 或至少复制其内容数组。
- `delete` 对不存在的 `storageKey` 不抛异常。
- 缺失文件读取抛 `IllegalArgumentException` 或统一存储异常，测试固定当前语义。

测试与完成条件：

- 保存后可读取相同内容。
- 修改原始上传数组不影响已存内容。
- 修改读取到的数组不影响再次读取结果。
- 删除后再次读取失败。
- 重复删除不失败。
- 并发保存生成不同 `storageKey`。

建议提交：`test: harden in memory file storage mock`

### C4.5：抽出记录型消息与流程回调 Mock

目标：

- `MessagePublisher` 有可复用的本地记录型 Mock。
- `WorkflowCallbackHandler` 有可复用的本地记录型 Mock。
- Starter 和独立应用不各自维护重复消息或回调 Mock。

提示词：

“请将记录型消息发布器整理为 `platform-core.mock.RecordingMessagePublisher`，并新增 `platform-core.mock.RecordingWorkflowCallbackHandler`。前者实现 `MessagePublisher`，线程安全记录 `ProcessMessage`，当 `createdAt` 为空时补当前时间，并提供可用于测试断言的消息快照方法；后者实现 `WorkflowCallbackHandler`，线程安全记录 `WorkflowEvent`，当 `occurredAt` 为空时补当前时间，并提供可用于测试断言的事件快照方法。Starter 和独立应用改为复用这两个 core mock，删除或停止使用 Starter 私有内部重复实现和不可断言空回调。”

实现要点：

- 类放在 `com.flowmind.platform.mock`。
- 使用线程安全 List 或同步保护。
- 对外返回只读快照，避免测试误改内部状态。
- 不在 Mock 中吞掉调用方需要感知的生产异常；Mock 自身正常记录即可。
- `PlatformAutoConfiguration` 引用 core mock 类。
- `MessagePublisher` 和 `WorkflowCallbackHandler` 是两个不同 SPI，不要用 `ProcessMessage` 代替流程回调事件。

测试与完成条件：

- 发布消息后可查询到记录。
- `createdAt` 为空时被补齐。
- 多次发布顺序稳定。
- 并发发布不丢消息。
- Starter 不再依赖私有内部 `RecordingMessagePublisher`。
- 处理回调事件后可查询到记录。
- `occurredAt` 为空时被补齐。
- 并发处理回调事件不丢事件。
- Starter 不再使用不可断言空回调作为默认回调 Mock。

建议提交：`feat: add reusable recording message and callback mocks`

### C4.6：收口 Starter 与独立应用装配

目标：

- Starter 和独立 REST 应用复用同一套 core 能力。
- 宿主可覆盖文件存储、消息推送、流程回调、附件授权和当前用户。
- Mock 关闭时不注册本地文件、消息和流程回调 Mock。

提示词：

“请收口 M4 的 Starter 和独立应用装配。`FileStorageProvider`、`MessagePublisher` 与 `WorkflowCallbackHandler` 使用 `@ConditionalOnMissingBean` 和 `flow-mind.platform.mock.enabled` 控制；宿主提供 Bean 时让位；Mock 关闭时不装配本地文件、消息和流程回调 Mock。`AttachmentAccessProvider` 默认拒绝全部访问，不能因启用文件 Mock 而放行。`AttachmentService` 默认装配真实 `DefaultAttachmentService`，依赖唯一 `ProcessAttachmentRepository`。”

实现要点：

- `flow-mind.platform.enabled=false` 时不装配平台 Bean。
- `flow-mind.platform.mock.enabled=false` 时本地文件 Mock、消息 Mock、回调 Mock、委托 Mock、当前用户 Mock 均按既有策略关闭。
- 缺失 `CurrentUserProvider` 时查询和附件写动作应有清晰错误。
- `AttachmentAccessGuard` 只依赖 `AttachmentAccessProvider`。
- 独立应用 `PlatformStandaloneConfiguration` 使用 `InMemoryFileStorageProvider`、`RecordingMessagePublisher` 和 `RecordingWorkflowCallbackHandler`。
- 不保留 `UnsupportedAttachmentService` 作为默认附件 Service。

测试与完成条件：

- 默认上下文能注入真实 `AttachmentService`。
- 默认上下文能注入 `FileStorageProvider`、`MessagePublisher` 和 `WorkflowCallbackHandler` Mock。
- 宿主自定义 `FileStorageProvider` 覆盖默认 Mock。
- 宿主自定义 `MessagePublisher` 覆盖默认 Mock。
- `mock.enabled=false` 时不装配本地文件、消息和流程回调 Mock。
- 默认附件授权拒绝。

建议提交：`feat: wire M4 spi mocks in starter`

### C4.7：配合 B 线附件服务联调

目标：

- 验证 C 线底座支撑 B 线附件运行时闭环。
- 固定权限拒绝、事务回滚、软删除和文件清理边界。

提示词：

“请基于 B 线 `DefaultAttachmentService` 做 M4 联调测试。使用 C 线 `ProcessAttachmentRepository`、`InMemoryFileStorageProvider`、`AttachmentAccessGuard` 和附件模板/配置 Repository，覆盖实例附件上传、任务附件上传、查询、下载、软删除、必填校验、格式/大小/数量限制、权限拒绝和来源任务完成后拒绝删除。不得在测试中绕开 Repository 直接伪造附件服务行为。”

实现要点：

- 权限允许场景使用测试 `AttachmentAccessProvider`。
- 权限拒绝和 SPI 异常场景断言 `FileStorageProvider` 未被调用。
- 上传成功后数据库有元数据，文件 Mock 有内容。
- 数据库写入失败时外部文件通过回滚补偿清理。
- 软删除后查询不返回，下载不可用。
- 来源任务已经完成、取消或不存在时拒绝删除。
- 删除元数据提交后再最佳努力删除外部文件。

测试与完成条件：

- 实例附件上传可查询可下载。
- 任务附件上传绑定正确任务。
- 缺失必填附件时任务提交前校验失败。
- 非法扩展名、伪造大小、超限不留下元数据。
- 重复删除已删除附件按约定幂等处理。
- B 线附件主路径使用 C 线 Repository 和 SPI。

建议提交：`test: integrate attachment runtime with C foundation`

### C4.8：实例/定义删除后的文件清理联调

目标：

- 确认实例删除、定义删除时附件元数据与外部文件清理边界正确。
- 文件删除失败不回滚数据库删除。

提示词：

“请补充实例删除和定义删除下的附件文件清理测试。删除前通过 `ProcessAttachmentRepository` 或对应删除 Repository 收集 `storage_key`，数据库事务内删除实例/定义及附件元数据，事务提交后调用 `FileStorageProvider.delete` 最佳努力清理。模拟文件删除失败时，数据库删除结果不回滚，只记录或忽略外部清理失败。”

实现要点：

- 不在数据库级联删除 SQL 中调用外部文件存储。
- 删除前收集 `storage_key`，删除后统一清理。
- 删除失败不影响元数据删除结果。
- 重复清理同一 `storageKey` 不失败。

测试与完成条件：

- 删除实例后附件元数据被删除或按既有删除策略清理。
- 对应文件 Mock 被删除。
- 文件删除失败时实例删除仍成功。
- 定义删除清理其所有实例附件的存储键。

建议提交：`test: cover attachment file cleanup on deletion`

### C4.9：文档收口和阶段记录

目标：

- 更新 M4 技术实现说明、测试文档和阶段完成记录。
- 记录实际新增类、方法、装配规则和测试结果。
- 明确真实存储、真实消息和真实流程回调实现由宿主接入。

提示词：

“请根据实际代码交付更新 C 线 M4 文档。记录 `FileStorageProvider`、`ProcessAttachmentRepository`、`InMemoryFileStorageProvider`、`RecordingMessagePublisher`、`RecordingWorkflowCallbackHandler`、Starter 装配和与 B 线附件服务联调结果。说明平台只保存附件元数据，文件内容由宿主文件存储 SPI 负责；消息通知和流程回调分别由宿主提供真实 SPI 实现。记录 Maven 测试命令和结果，明确审计、提醒、告警留到 M5/M6。”

测试与完成条件：

- 文档中的类名、方法名、配置项和表字段与代码一致。
- 完成记录包含测试命令和结果。
- 未实现能力明确列为后续阶段。

建议提交：`docs: record C M4 attachment foundation delivery`

## 推荐执行顺序

1. C4.1 对齐 M4 基线和缺口。
2. C4.2 收口文件存储 SPI 契约。
3. C4.3 补齐附件元数据 Repository。
4. C4.4 完善内存文件存储 Mock。
5. C4.5 抽出记录型消息与流程回调 Mock。
6. C4.6 收口 Starter 与独立应用装配。
7. C4.7 配合 B 线附件服务联调。
8. C4.8 删除后的文件清理联调。
9. C4.9 文档收口和阶段记录。

## 最小验收清单

- `FileStorageProvider` 契约测试通过。
- `MessagePublisher` 契约测试通过。
- `ProcessAttachmentRepository` 可读取、查询、计数、写入、软删除和收集 `storage_key`。
- 附件查询默认排除软删除并稳定排序。
- 来源任务完成后不能继续上传或删除附件。
- `InMemoryFileStorageProvider` 做防御性拷贝，支持重复删除。
- `RecordingMessagePublisher` 位于 `platform-core.mock`，Starter 和独立应用可复用。
- `RecordingWorkflowCallbackHandler` 位于 `platform-core.mock`，Starter 和独立应用可复用。
- Starter 默认装配真实 `AttachmentService`，宿主可覆盖文件存储、消息发布和流程回调。
- 默认 `AttachmentAccessProvider` 拒绝全部访问。
- 权限拒绝时不会访问文件存储。
- 实例附件和任务附件上传、查询、下载、删除闭环通过。
- 必填附件校验可被 B 线运行时调用并生效。
- 实例/定义删除后外部文件按事务提交后最佳努力清理。
- 审计日志、已阅、催办、提醒、告警未被夹带实现。

## 建议提交顺序

1. `docs: align C M4 attachment foundation gaps`
2. `test: cover file storage spi contract`
3. `feat: complete attachment metadata repository`
4. `test: harden in memory file storage mock`
5. `feat: add reusable recording message and callback mocks`
6. `feat: wire M4 spi mocks in starter`
7. `test: integrate attachment runtime with C foundation`
8. `test: cover attachment file cleanup on deletion`
9. `docs: record C M4 attachment foundation delivery`

## 建议验证命令

```powershell
mvn -q -pl platform/platform-core -am test
mvn -q -pl platform/platform-starter -am test
mvn -q test
```
