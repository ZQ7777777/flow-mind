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

## 确定性验收模式

自动化测试使用 `NODE_ENV=test` 下的受限 fake Pi 和 Mock HTTP 平台。若只需本地演示 UI，
可显式设置 `AGENT_FAKE_PI=true`；该开关不得用于生产或真实人工验收。
