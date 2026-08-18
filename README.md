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

## 一键启动与停止

在仓库根目录使用 PowerShell 执行：

```powershell
.\scripts\start.ps1
```

启动脚本会依次执行以下操作：

1. 停止占用 `8081`、`3100`、`5173`、`5174` 的旧开发服务，确保正在运行的 JVM 不会继续加载旧 JAR。
2. 在仓库根目录执行 `mvn -U --no-transfer-progress -DskipTests clean install`，重新构建整个 Maven reactor，并将最新的 `platform-core`、`platform-starter` 和 Business Base SNAPSHOT 安装到仓库内的 `.m2/repository`。
3. 调用 `agent-web/scripts/Start-AgentWeb.ps1`，构建共享契约并启动 Business Base 后端、Agent 后端和 Agent 前端。
4. 在 `business-base/frontend` 下启动 Business Base 前端，并等待两个前端端口就绪。

启动完成后可访问：

- Agent Web：`http://127.0.0.1:5173`
- Business Base：`http://127.0.0.1:5174`

只预览脚本将执行的动作，不停止、构建或启动任何进程：

```powershell
.\scripts\start.ps1 -WhatIf
```

如果 Maven 源码和依赖没有变化，可以跳过全量 Maven 更新以更快启动：

```powershell
.\scripts\start.ps1 -SkipMavenUpdate
```

也可以使用等价的短参数 `-Fast`。快速模式仍会完整重启项目，但会直接使用 `.m2/repository` 中已有的 Maven 包；修改过 `platform`、Business Base 后端或 Maven 依赖后，请使用不带该参数的默认启动方式，避免加载旧包。

停止整个项目：

```powershell
.\scripts\end.ps1
```

`end.ps1` 默认会要求确认；需要跳过确认时执行：

```powershell
.\scripts\end.ps1 -Force
```

首次运行前请确保已安装 Java 8、Maven、Node.js `>= 22.19.0` 和 npm，并已分别在 `agent-web`、`business-base/frontend` 安装 npm 依赖。真实对话模式还需按 [Agent Web 运行说明](agent-web/README.md) 配置 `agent-web/.env`。

## Business Base 业务入口发布边界

Business Base 后端首次部署业务大厅入口配置和通用发起接口时，需要重新构建并重启后端。启动过程中会幂等创建 `business_entry_config` 表，不需要手工执行 SQLite 脚本。

功能部署完成后，各类变更按以下边界处理：

- 新增或修改流程定义的业务入口配置：保存配置并刷新业务大厅，不需要重启后端。
- 仅新增或修改 Agent 生成的前端表单页：重新构建并发布 Business Base 前端，不需要重启后端。
- 修改 `/api/workflow/process-entry-links`、`/api/workflow/processes/{processCode}/start-context`、`start-submit` 或其他通用后端契约：重新构建并重启 Business Base 后端，并同步验证前端调用。
- 生产和测试环境首期不支持后端运行时插件热加载；历史 `/api/generated/entry-application/submit` 接口继续作为入金样例兼容入口。
