# Flow Mind 流程平台 AI 开发指南

## 项目概述

Flow Mind 流程平台基于 Java 8、Spring Boot 2.7.18 和 Maven 多模块架构开发。平台提供通用流程能力，不承载具体业务逻辑。

## 开发环境

- 操作系统：Windows
- 终端：PowerShell
- 使用与 Windows 和 PowerShell 兼容的命令
- 默认测试命令：`mvn -q test`

## 开发规范

- 使用 Java 8，禁止使用 Java 9+ API 和语法
- 使用 Spring Boot 2.7.18，不引入 Spring Boot 3.x / Jakarta 依赖
- 使用 Spring JDBC 和 SQLite，不增加多数据库适配
- `platform-core` 是主体模块，包含公共契约、核心逻辑、持久化实现、REST 适配和本地 Mock 实现
- `platform-starter` 仅包含 Spring Boot 自动配置，并依赖 `platform-core`

## 代码风格

- 包名统一使用 `com.flowmind.platform`
- 类名使用 `PascalCase`
- 方法名和变量名使用 `camelCase`
- 枚举值使用 `UPPER_SNAKE_CASE`
- DTO 命名使用 `XxxDTO`、`XxxRequest`、`XxxResult`、`XxxQuery`
- 定义实体类、DTO、Request、Result、Query 等数据承载类时，使用 Lombok `@Data` 生成访问器
- 类使用 Javadoc 说明对象用途，并保留 `@author`、`@since`
- 每个字段必须使用中文 Javadoc 说明业务含义、约束或单位；状态、类型字段应列出典型取值，JSON 配置字段应明确标注其内容格式
- 数据承载类只承载数据，不包含业务判断、状态流转或持久化逻辑
- Controller 仅负责 HTTP 协议适配，不承载核心逻辑
- 为关键规则和复杂状态流转添加必要注释，重点说明设计意图和约束原因

## 测试要求

- 新增或修改生产代码时，必须同步新增或更新单元测试；修复缺陷时，必须补充能够复现该缺陷的回归测试
- 单元测试应覆盖正常路径、边界条件和异常路径，并断言可观察的行为与状态变化
- 单元测试应隔离被测对象；对不属于当前测试范围的依赖使用 Mock、Stub 或 Fake，避免依赖真实网络、系统时间、随机结果或共享环境
- 测试应相互独立、可重复执行，不依赖执行顺序或其他测试遗留的数据
- 测试名称应清晰表达测试条件和预期结果，断言应聚焦行为结果，避免依赖内部实现细节
- 需要集成测试的改动，应在单元测试基础上补充相应测试，不得以集成测试替代单元测试
- 完成代码变更后，在 `platform` 目录运行 `mvn -q test`；修改仓库根 Maven 结构时，还需在仓库根目录运行 `mvn -q test`

## 相关文档

- **需求文档**：`doc/rebuild-functional-requirements-optimized.md`
- **设计与接口文档**：`doc/流程平台设计与接口文档_v4.md`
- **技术路线（含阶段分工）**：`doc/流程平台技术路线_v5.2.md`

## 注意事项

- 保持代码简洁，避免过度设计
- 优先实现第一阶段验收所需的核心能力
- 不使用 Linux 专用命令示例，如 `rm -rf`、`cp -r`
