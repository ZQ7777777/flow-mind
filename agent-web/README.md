# Flow Mind Agent Web（M0-M2）

本工程实现从自然语言需求采集到流程定义发布激活的开发期工作台，范围截止
`doc/agent-web-tech-design.md` 16.4 第 7 步。

## 环境

- Node.js `>= 22.19.0`
- 可访问的 Flow Mind 流程平台独立服务
- 真实对话模式需要 `PI_MODEL=provider/model` 和对应 Provider 服务端凭据

复制 `.env.example` 中的配置到进程环境。`targetRoot` 在 M0-M2 可以留空。

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

`scripts` 子目录中的 PowerShell 脚本会分别打开流程平台、Agent 后端和前端的终端窗口；已占用
`8080`、`3100` 或 `5173` 的服务会被保留，不会被脚本自动结束。

真实对话的配置会从 `agent-web/.env` 自动读取。先复制 `.env.example` 为 `.env`，并仅在 `.env`
中填写 API Key；`.env` 不应提交到仓库。

```powershell
cd E:\resume_project\flow-mind\agent-web
Copy-Item .env.example .env
# 编辑 .env，将 OPENAI_API_KEY= 替换为你的 OpenAI API Key
.\scripts\Start-AgentWeb.ps1
```

若流程平台已经单独启动，可跳过它：

```powershell
.\scripts\Start-AgentWeb.ps1 -SkipPlatform
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

## 确定性验收模式

自动化测试使用 `NODE_ENV=test` 下的受限 fake Pi 和 Mock HTTP 平台。若只需本地演示 UI，
可显式设置 `AGENT_FAKE_PI=true`；该开关不得用于生产或真实人工验收。
