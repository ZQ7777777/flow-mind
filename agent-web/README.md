# Flow Mind Agent Web

## Code-generation evaluation

The frozen M0 evaluation suite and upgrade plan are documented in [`evals/README.md`](evals/README.md) and [`docs/code-generation-upgrade-m0-m6.md`](docs/code-generation-upgrade-m0-m6.md). The evaluation commands validate and score offline artifacts; they do not call a real model.

本工程实现从自然语言需求采集、流程定义发布激活，到受限暂存区中的用户自定义流程前端代码、测试生成和人工代码预览。代码生成采用 `GenerationTargetContract 2.1` 的 `FRONTEND_ONLY` 模式，不再生成 Java 或业务专用提交接口。

## 环境

- Node.js `>= 22.19.0`
- 可启动的 `business-base` 目标工程
- 真实对话模式需要 `PI_MODEL=provider/model` 和对应 Provider 服务端凭据

复制 `.env.example` 中的配置到进程环境。`targetRoot` 在 M0-M2 可以留空，M3 启动生成时可补填，
但必须位于 `AGENT_ALLOWED_TARGET_ROOTS` 下并通过 `.flowmind/generation-target.json` 预检。

代码生成默认使用 `DETERMINISTIC_IR_V1`，从已校验的 Requirement IR 直接产出标准模块，不创建 Pi Session、
不调用生成模型。`PI_LEGACY` 只用于明确授权后的兼容性回退；开发和评测继续使用 Fake Pi 与离线门禁，
不会读取项目中的真实模型凭据或产生模型费用。

RAG 默认以 `SHADOW` 模式发布：实际结果继续使用 BM25，同时离线计算并审计 Hybrid 候选结果。验证后可将
`AGENT_RAG_RELEASE_MODE` 设为 `CANARY`，并以 `AGENT_RAG_CANARY_PERCENT` 控制稳定分桶比例；确认后再切换
为 `HYBRID_DEFAULT`。紧急回退设置 `AGENT_RAG_FORCE_BM25=true`，新 generation 会立即固化为 BM25 且停止
影子向量计算。每个 generation 都记录策略版本、发布模式、0–99 分桶和回退状态；聚合差异可从
`GET /api/agent/management/rag-shadow-metrics` 查看。

生成上下文会按 IR 的多选、动态数据、级联、查询、计算和核查能力生成必读清单，并冻结共享组件 props、
workflow 类型和只读 API 的 TypeScript 签名。质量报告中的 `BLOCKING` Reviewer finding 会阻止写入；
人工放行必须绑定当前 revision 并留下操作者和原因。

## M3 目标工程前置条件

目标③工程必须提供 2.1 契约声明的前端构建脚本、`WorkflowStartShell`、共享 workflow 类型、路由注册文件和金标参考文件。缺少任一前置件时会拒绝生成；1.x 与 2.0 契约会返回明确升级错误。

每次生成固定产出 `BusinessForm.vue`、`Apply.vue`、两个组件测试和 `generated-routes.ts` 共五个文件；当需求含动态参考数据或 `frontendBehavior.dataQueries` 时，再产出只读业务 API 客户端和相邻测试，共七个文件。`Apply.vue` 只组合固定 `processCode`、`BusinessForm` 与 `WorkflowStartShell`，公共壳负责发起、附件、幂等和成功状态。

生成前会冻结项目 Skill、原始“仓单、国债（解）质押申请”流程文档及当前 sample 金标源码的内容和 SHA-256。重新生成、修复和 Reviewer 始终复用原快照，避免参考文件更新改变进行中的生成结果。
产物保存在 `AGENT_DATA_DIR/staging/{sessionId}/{generationId}`，可在代码树中编辑和查看 diff，
不会写入真实目标工程。质量阶段只执行前端 typecheck、test 和 build；后端 compile/test 在报告中标记为不适用。Reviewer、自动修复和最终写入继续使用同一上下文快照。

## 开发

```powershell
npm install
npm run build
npm run test
npm run dev:backend
```

另开一个终端运行：

```powershell
npm run dev:frontend
```

浏览器访问 `http://127.0.0.1:5173`。生产构建后由 NestJS 在
`http://127.0.0.1:3100` 同源托管前端。

## Windows 一键启动与停止

`scripts` 子目录中的 PowerShell 脚本会分别打开 Business Base、Agent 后端和前端的终端窗口；已占用
`8081`、`3100` 或 `5173` 的服务会先接受服务身份检查，通过后保留运行。

真实对话的配置会从 `agent-web/.env` 自动读取。先复制 `.env.example` 为 `.env`，并仅在 `.env`
中填写 API Key；`.env` 不应提交到仓库。

```powershell
cd D:\flow-platform\flow-mind\agent-web
Copy-Item .env.example .env
# 编辑 .env，将 OPENAI_API_KEY= 替换为你的 OpenAI API Key
.\scripts\Start-AgentWeb.ps1
```

启动脚本会先重新构建 `@flowmind/agent-contracts`，因此不需要手工更新 `shared/dist`。共享契约
构建成功后，脚本会启动 Agent 后端并等待 `http://127.0.0.1:3100/health/live` 返回 `UP`，随后
才启动前端，避免前端首次加载用户列表时后端尚未就绪。

若 Business Base 已经单独启动，可跳过它：

```powershell
.\scripts\Start-AgentWeb.ps1 -SkipBusinessBase
```

仅查看将执行的动作而不启动服务：

```powershell
.\scripts\Start-AgentWeb.ps1 -WhatIf
```

关闭服务时运行下列脚本，确认后它会结束监听开发端口的完整进程树：

```powershell
.\scripts\Stop-AgentWeb.ps1
```

不需要确认时可使用 `-Force`。启动完成后访问 `http://127.0.0.1:5173`；可通过
`http://127.0.0.1:3100/health/ready` 检查模型认证是否成功。

Agent Web 使用 Business Base 的管理员 Session。访问页面后请使用管理员账号登录（本地 Mock 数据默认
`admin01` / `123456`）；普通用户会收到 403，无法访问工作台。Agent 后端会把该 Session 转发给
Business Base 内嵌的 `/api/platform/**`，流程定义因此写入 `./data/business-flow-local.db`，不再写入
独立 Platform 的数据库。
启动脚本会显式设置 `FLOW_MIND_PLATFORM_SQLITE_PATH` 为仓库根目录下的该文件，避免 Maven 与 IDE
工作目录不同导致生成第二份同名数据库。

如果共享契约构建失败，脚本会在打开任何服务窗口前停止，请先根据当前窗口中的 npm 错误修复
依赖或 TypeScript 编译问题。如果后端在 60 秒内未通过存活检查，前端不会启动；请查看 Agent
后端 PowerShell 窗口中的启动异常。如果 3100 端口已被其他程序占用且存活检查不通过，先停止
该冲突进程，再重新运行启动脚本。

## 确定性验收模式

自动化测试使用 `NODE_ENV=test` 下的受限 fake Pi 和 Mock HTTP 平台。若只需本地演示 UI，
可显式设置 `AGENT_FAKE_PI=true`；该开关不得用于生产或真实人工验收。
