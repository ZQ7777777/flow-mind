# Flow Mind

基于对话方式实现的业务办理全流程自动化构建平台。

当前仓库已按流程平台技术路线配置第一阶段后端工程骨架：

- Java 8
- Spring Boot 2.7.18
- Maven 多模块，流程平台代码位于 `platform/`
- Spring JDBC
- SQLite 文件数据库
- JUnit 5 / Spring Boot Test

Agent Web M0-M2 实现位于 `agent-web/`，提供需求对话、结构化需求门禁、流程平台草稿落地、流程预览、发布和激活能力。运行说明见 [agent-web/README.md](agent-web/README.md)。

本地环境配置说明见 [doc/本地技术环境配置.md](doc/本地技术环境配置.md)。
