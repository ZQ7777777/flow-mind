# 实习生 C：M1 表单字段与附件模板 Vibe Coding 计划

## 产物与边界

本文件作为实习生 C 在 M1 阶段的专项编码计划。M1 只实现定义期扩展能力：表单字段、附件模板版本和流程定义附件配置。不得提前实现 M4 的文件存储、附件上传下载、附件元数据和运行期必填附件校验。

每个微任务完成后执行对应测试、进行只读代码审查

## 基线优先级

发生描述差异时按以下优先级执行：

1. `doc/流程平台设计与接口文档_v4.md`
2. `doc/流程平台技术路线_v5.2.md`
3. `doc/实习生C/实习生C开发文档.md`
4. `doc/实习生C/M0/技术实现说明.md`
5. 当前代码中的已冻结公共 DTO、Request、Service、SPI 和 DDL

M1 编码必须复用现有公共类型，不创建重复 DTO、重复枚举、重复 Service 或兼容别名。

## 目标结构

建议结构如下，实际包名可按代码评审后微调：

```text
platform-core/src/main/java/com/flowmind/platform
├── api/
│   ├── dto/                         # 复用已冻结公共 DTO
│   └── request/                     # 复用 SaveProcessGraphRequest
├── core/
│   └── definition/
│       └── extension/               # C 线定义扩展生命周期、校验和读取协作
├── persistence/
│   └── repository/                  # M1 三张表 Repository
└── core/validation/                 # 若复用现有校验包，可放扩展校验器

platform-core/src/test/java/com/flowmind/platform
├── persistence/repository/
├── core/definition/extension/
└── integration/
```

推荐数据流：

```text
SaveProcessGraphRequest.formFields ─────────┐
SaveProcessGraphRequest.attachmentConfigs ──┤
                                             ├─> C 扩展校验器 -> Repository -> SQLite
附件模板版本管理 ────────────────────────────┘

A 的定义生命周期 -> DefinitionExtensionLifecycle -> C 扩展数据
B 的定义快照加载 -> C 扩展读取端口 -> 表单字段 + 附件配置
```

## 统一编码规则

- Java 8、Spring Boot 2.7.18。
- 新增公开 API 类型使用 JavaBean，不使用 `record`、Java 9+ API 或新的重复公共类型。
- Repository 负责 SQL 访问，Service/生命周期组件负责事务编排和业务语义。
- DTO 面向调用方，Entity 面向数据库，不直接互相混用。
- JSON 字段必须通过统一工具或 Jackson 处理，不用手写字符串拼接。
- `allowed_extensions` 按 JSON 数组存储，对外使用 `List<String>`。
- 表单字段、附件模板和附件配置不包含具体业务判断。
- 外部缓存失效和跨线通知只能在数据库事务成功提交后发生。

## 微任务与编码提示词

### C1.1：冻结 M1 扩展契约和字段语义

目标：

- 明确 `ProcessFormFieldDTO`、`ProcessAttachmentConfigDTO`、`ProcessAttachmentTemplateDTO` 的职责。
- 统一附件模板扩展名存储格式。
- 明确模板版本、配置组状态、配置组激活语义。
- 建立 DTO/Entity 与 DDL 字段映射清单。

提示词：

“请在不修改生产逻辑的前提下，核对 C 线 M1 的 DTO、Entity 和 DDL 字段语义。明确 `ProcessAttachmentConfigDTO` 用于保存定义附件配置，`ProcessAttachmentTemplateDTO` 用于详情查询合并展示模板版本和定义配置。统一 `allowed_extensions` 为 JSON 数组存储、DTO 为 `List<String>`。不得创建重复 DTO 或提前实现运行期附件能力。”

测试与完成条件：

- DTO/Entity/DDL 字段语义无冲突记录。
- 若补充契约测试，只覆盖字段表达能力。
- 执行 `mvn -q -pl platform/platform-core -am test`。

建议提交：`docs: clarify M1 definition extension contracts`

### C1.2：实现 M1 Repository 基础

目标：

- 新增表单字段、附件模板和定义附件配置 Repository。
- 使用 Spring JDBC 或项目既有持久化方式。
- 保持 SQL 与 SQLite DDL 一致。
- 不在 Repository 中实现 A/B 状态机。

提示词：

“为 C 线 M1 新增 `ProcessFormFieldRepository`、`ProcessAttachmentTemplateRepository` 和 `ProcessDefinitionAttachmentConfigRepository`。Repository 只负责 SQLite 表访问和实体映射，提供保存、查询、复制、删除、激活所需的最小方法。不得访问 Controller，不实现流程定义 CRUD，不实现运行期附件元数据。”

测试与完成条件：

- 每个 Repository 有独立 SQLite 集成测试。
- 表单字段可保存、查询、复制、删除。
- 附件模板可按编码查询最大版本并保存新版本。
- 附件配置可按定义和配置组查询。
- 执行 `mvn -q -pl platform/platform-core -am test`。

建议提交：`feat: add definition extension repositories`

### C1.3：实现表单字段保存、查询、复制和校验

目标：

- 实现表单字段校验器。
- 提供按定义替换保存能力。
- 查询按 `sortOrder, fieldCode` 稳定排序。
- 复制定义时生成新 ID 并绑定新定义。

提示词：

“实现 C 线 M1 的表单字段定义能力。校验字段编码、名称、字段类型、控件类型、必填标志、排序和 `validationRule` JSON；同一定义内 `fieldCode` 唯一。实现按定义替换保存、查询、复制和删除。不得保存用户填写值，用户变量仍由 B 线运行期写入实例变量。”

测试与完成条件：

- 合法表单字段可保存并稳定查询。
- 重复 `fieldCode` 失败。
- 非法 `validationRule` JSON 失败。
- 复制后新字段 ID 不等于源字段 ID，且 `definitionId` 为目标定义。
- 删除定义草稿扩展时字段被清理。

建议提交：`feat: manage process form field definitions`

### C1.4：实现附件模板版本化管理

目标：

- 按 `attachmentCode` 自动生成递增 `templateVersion`。
- 规范化 `allowedExtensions`。
- 支持模板启停。
- 已被 ACTIVE 配置引用的模板版本不可破坏性修改。

提示词：

“实现全局附件模板版本化管理。创建模板时按相同 `attachmentCode` 的最大版本号加一生成 `templateVersion`；扩展名统一小写、去除前导点、去重并以 JSON 数组保存；`maxSizeBytes` 必须大于 0。模板禁用后不得被新的流程定义附件配置引用；已被 ACTIVE 配置引用的模板版本不得原地修改格式和大小，规则变化应新建版本。”

测试与完成条件：

- 同一附件编码连续创建得到版本 1、2、3。
- 扩展名 `.PDF`、`jpg`、`png` 被规范为 `pdf/jpg/png`。
- `maxSizeBytes <= 0` 失败。
- 禁用模板不能被新配置引用。
- 被 ACTIVE 配置引用的模板版本修改规则失败。

建议提交：`feat: version attachment templates`

### C1.5：实现流程定义附件配置组

目标：

- 保存定义附件配置草稿组。
- 校验模板、数量规则、节点引用和组内唯一性。
- 激活配置组，并下线旧 ACTIVE 配置组。

提示词：

“实现流程定义附件配置组能力。配置项引用全局附件模板版本，保存 `required`、`minCount`、`maxCount`、`applicableNodeCodes` 和排序。校验模板存在且启用、适用节点存在、数量规则合法、同一配置组内附件编码和模板绑定不重复。同一定义同一时刻只能有一个 ACTIVE 配置组；激活新配置组时旧 ACTIVE 变为 INACTIVE。”

测试与完成条件：

- 草稿配置组可保存并查询。
- `required=true` 且 `minCount=0` 失败。
- `maxCount < minCount` 失败。
- 适用节点不存在失败。
- 同一组重复附件编码或模板 ID 失败。
- 激活第二个配置组后第一个 ACTIVE 变为 INACTIVE。

建议提交：`feat: manage definition attachment configurations`

### C1.6：接入 A 的流程定义生命周期

目标：

- 提供 `DefinitionExtensionLifecycle` 生产实现。
- A 的定义保存、详情、复制、删除、发布校验和激活能调用 C 扩展能力。
- 保证扩展数据与定义数据同事务提交。

提示词：

“将 C 线 M1 扩展能力接入 A 线流程定义生命周期。实现或复用 `DefinitionExtensionLifecycle`，支持复制扩展数据和删除草稿扩展数据。让 `saveGraph` 可以同事务保存节点、连线、表单字段和附件配置；`getDefinition` 返回完整表单和附件配置；`copyDefinition` 复制扩展数据；`validateForPublish` 合并 C 的扩展校验；`activate` 激活唯一附件配置组。不得在 C 线重复实现 A 的流程定义 CRUD。”

测试与完成条件：

- `saveGraph` 保存节点、连线和扩展数据同事务成功。
- 扩展保存失败时定义结构不应部分提交。
- `getDefinition` 返回表单字段和附件配置合并视图。
- `copyDefinition` 后扩展数据绑定新定义。
- `deleteDefinition` 清理草稿扩展数据。
- `validateForPublish` 能返回 C 扩展配置问题。

建议提交：`feat: integrate definition extensions with lifecycle`

### C1.7：提供 B 的完整定义扩展读取和缓存失效协作

目标：

- 提供 B 可消费的扩展读取端口或适配实现。
- 提供 `DefinitionCacheInvalidator` 调用点。
- C 扩展配置变更提交后失效 B 的定义快照缓存。

提示词：

“为 B 线运行时定义快照提供 C 扩展读取能力。读取结果必须包含表单字段、附件配置组、模板编码、模板版本、允许格式、大小限制、必填、数量和适用节点。提供定义缓存失效协作点，C 扩展配置事务成功提交后按 `definitionId` 失效缓存；事务回滚不得误删当前有效快照。不得复制 B 的运行时快照类型或缓存实现。”

测试与完成条件：

- B 读取端口能拿到完整表单字段和附件配置。
- 配置变更成功提交后触发缓存失效。
- 保存失败或事务回滚时不触发失效。
- 不出现重复 DTO、重复 Repository 或重复 Service。

建议提交：`feat: expose definition extensions for runtime snapshots`

### C1.8：入金申请样例、联合测试和收口文档

目标：

- 用入金申请样例验证 M1 定义期能力。
- 补齐 M1 测试文档和技术实现说明。
- 运行模块和根测试。

提示词：

“为 C 线 M1 补齐入金申请样例测试和阶段收口文档。样例只作为测试数据：表单字段包含申请人、入金金额、入金账号；附件模板为银行回单，允许 `pdf/jpg/png`，最大 `10MB`；定义附件配置要求申请节点适用且必填。测试 `getDefinition` 完整返回、发布校验通过、复制定义扩展数据完整、激活配置组唯一。不得在生产代码硬编码入金业务判断。”

测试与完成条件：

- 执行 `mvn -q -pl platform/platform-core -am test`。
- 执行 `mvn -q test`。
- M1 技术实现说明记录已落地能力和边界。
- M1 测试文档记录命令、覆盖点和结果。
- 子 agent 只读审查无阻塞问题。

建议提交：`test: verify M1 definition extension integration`

## 测试夹具矩阵

| 夹具 | 内容 | 预期 |
| --- | --- | --- |
| 基础表单字段 | 申请人、金额、账号 | 保存成功，稳定排序查询 |
| 重复字段编码 | 同一定义两个 `amount` | 校验失败 |
| 非法字段 JSON | `validationRule` 不是 JSON | 校验失败 |
| 银行回单模板 | `receipt`，`pdf/jpg/png`，`10MB` | 创建版本 1 |
| 模板版本递增 | 同一 `receipt` 再创建 | 创建版本 2 |
| 禁用模板引用 | 新配置引用 DISABLED 模板 | 校验失败 |
| 必填附件配置 | `required=true`，`minCount=1`，申请节点 | 保存并可激活 |
| 非法数量 | `maxCount < minCount` | 校验失败 |
| 未知适用节点 | `applicableNodeCodes=["missing"]` | 校验失败 |
| 激活唯一性 | 同一定义激活两个配置组 | 最新 ACTIVE，旧组 INACTIVE |
| 复制定义 | 源定义有字段和附件配置 | 目标定义有新 ID 的扩展数据 |
| 查询详情 | 定义含节点、连线、字段、附件配置 | `ProcessDefinitionDetailDTO` 完整返回 |

## 合并门禁

- M1 不包含 M4 附件上传、下载、文件存储或附件元数据能力。
- M1 不保存用户实际表单值。
- 表单字段和附件配置与 A 的定义生命周期同事务。
- 附件模板版本不可被已生效引用破坏。
- 同一定义同一时刻只有一个 ACTIVE 附件配置组。
- B 能读取完整定义扩展配置。
- 配置变更后缓存失效发生在事务成功提交后。
- 无重复公共类型、无生产临时兼容类型、无具体业务硬编码。
- 每个微任务有测试、审查和独立提交。
- 模块测试与根测试通过。

## 推荐执行顺序

1. 完成 C1.1 契约整理。
3. 完成 C1.2 Repository 基础。
4. 完成 C1.3 表单字段能力。
5. 完成 C1.4 附件模板版本化。
6. 完成 C1.5 定义附件配置组。
7. 等 A 生命周期挂点稳定后完成 C1.6。
8. 等 B 定义读取和缓存失效端口稳定后完成 C1.7。
9. 完成 C1.8 样例、联合测试和文档。
10. 通过 PR 合入 `develop`，再进入 M2/M3 相关工作。
