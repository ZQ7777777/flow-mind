# Flow Mind 流程平台 AI 开发指令（to be completed）

## 项目概述
这是 Flow Mind 的 ① 流程平台，使用 Java 8 + Spring Boot 2.7.18 + Maven 多模块开发。平台负责流程定义、实例、任务、附件、回调、查询和管理动作；不负责具体业务逻辑。

## 开发环境
- 操作系统：Windows
- 终端：PowerShell
- 请使用 Windows 兼容的命令
- 构建命令：`mvn -q test`

## 开发规范
- 使用 Java 8，禁止使用 Java 9+ API 和语法
- 使用 Spring Boot 2.7.18，不引入 Spring Boot 3.x / Jakarta 依赖
- 使用 Spring JDBC + SQLite，不做多数据库适配
- `platform-core` 是流程平台主体模块，放 DTO、枚举、Service 接口、SPI 接口、核心流程逻辑、SQLite 表结构、Repository、REST 适配和本地 Mock 实现
- `platform-starter` 只放 Spring Boot 自动装配，依赖 `platform-core`


## 代码风格
- 包名统一使用 `com.flowmind.platform`
- 类名使用 PascalCase
- 方法名和变量名使用 camelCase
- 枚举值使用 UPPER_SNAKE_CASE
- DTO 命名使用 `XxxDTO`、`XxxRequest`、`XxxResult`、`XxxQuery`
- Controller 只做 HTTP 适配，不写核心流程逻辑
- 关键规则和复杂状态流转一定要有注释

## 测试要求
- 每个功能完成后运行 `mvn -q test`
- 修改根 Maven 结构后，在仓库根目录也运行 `mvn -q test`
- 测试流程定义、发布激活、实例启动、任务流转、历史轨迹和查询
- 涉及任务状态变化时，验证历史记录、审计或回调

## 注意事项
- 保持代码简洁，避免过度设计
- 优先实现第一阶段验收所需核心功能
- 平台代码不能出现入金、报销、请假等具体业务判断
- 文件内容不直接存 SQLite，附件内容走文件存储 SPI
- 查询接口必须分页
- 不提交 `target/`、`.sqlite`、`.db`、`.log`
- 不使用 Linux 专用命令示例，如 `rm -rf`、`cp -r`
