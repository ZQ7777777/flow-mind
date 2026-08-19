# Flow Mind Agent Web AI 开发指南

## 项目概述

Flow Mind Agent Web 基于 Node.js 22、TypeScript、NestJS、Vue 3 和 npm workspaces 开发。它是面向业务人员的开发期工作台，负责通过多轮对话沉淀结构化需求，调用 Flow Mind 流程平台创建、校验、发布并激活流程定义，并按技术设计逐步实现代码生成和质量闭环；它不参与业务运行期的后续审批流转。

## 开发环境

- 操作系统：Windows
- 终端：PowerShell
- Node.js：`>= 22.19.0`
- 包管理器：npm，依赖版本以根目录 `package-lock.json` 为准
- 使用与 Windows 和 PowerShell 兼容的命令
- 默认测试命令：`npm test`

## 开发规范

- 工程使用 npm workspaces，所有安装、构建、类型检查和完整测试命令均从 `agent-web` 根目录执行
- `shared` 维护前后端共享的领域类型、状态、请求响应契约和 Schema，不在前后端重复定义跨边界契约
- `backend` 使用 NestJS 和 TypeScript，负责身份上下文、会话状态机、Pi 集成、流程平台适配、SQLite 持久化及确定性工作流编排
- `frontend` 使用 Vue 3、TypeScript、Vite、Pinia 和 Element Plus；组件负责展示与交互，跨组件业务状态和异步操作集中在 Pinia Store，HTTP 与 SSE 访问集中在 API 层
- TypeScript 必须保持 `strict`；Node 侧使用 ES2022、NodeNext 和 ESM，后端与 `shared` 的相对导入保留编译后可用的 `.js` 扩展
- Pi 依赖必须精确锁定为 `@earendil-works/pi-coding-agent@0.82.1`，升级前必须核对兼容性并更新 lockfile 和相关测试
- Controller 仅负责 HTTP/SSE 协议适配、参数提取和响应，不承载核心状态流转、持久化或平台编排逻辑
- 工作流状态迁移必须由后端确定性执行；模型不得直接调用流程平台修改接口，也不得绕过结构化需求、流程或代码人工门禁
- 所有会话、流程和生成任务必须按 owner 隔离；修改操作必须遵守 `row_version`/generation revision 乐观锁，确认、重试、覆盖和写入操作必须保持幂等
- SQLite 保持 WAL、busy timeout 和外键约束；关联状态更新必须使用事务，禁止引入 Redis、消息队列、分布式锁或多实例调度能力
- API 错误统一返回稳定的错误码、消息、请求 ID 及必要上下文；不得把 API Key、完整 prompt、完整生成代码或敏感凭据写入日志
- 禁止提交 `.env`、API Key、SQLite 文件、Pi Session、测试产物或本地运行数据
- 默认仅监听 `127.0.0.1`；当前 Mock 用户头不构成正式认证，不得将其作为共享网络或生产部署的身份方案

## 代码风格

- 类型、接口、类、NestJS Provider 和 Vue 组件使用 `PascalCase`
- 函数、方法、变量、组合式函数和 Pinia Store 使用 `camelCase`
- 常量和有限状态值使用 `UPPER_SNAKE_CASE`；文件名沿用所在模块现有风格，测试文件使用 `*.spec.ts`
- 优先使用明确的领域类型、字面量联合和泛型，避免无约束的 `any`、重复类型断言和跨层传递未校验的 `unknown`
- 跨前后端的契约变更必须先更新 `shared`，并同步更新运行时 Schema、调用方、实现方及契约测试
- 数据承载接口只描述数据；业务判断、状态迁移、幂等、权限和持久化逻辑放在对应后端服务中
- Vue 组件避免直接散落 HTTP、SSE、并发控制和工作流状态判断；复用逻辑应放入 API 层、Store 或独立工具模块
- 异步操作必须处理失败、取消和资源释放；SSE、定时器、数据库连接及其他长生命周期资源不得泄漏
- 为关键门禁、状态迁移、Saga 补偿、幂等策略、路径安全和安全边界添加必要注释，重点说明设计意图和约束原因
- 保持现有代码格式，使用双引号、分号和尾随逗号，不进行与当前任务无关的大范围格式化

## 测试要求

- 新增或修改生产代码时，必须同步新增或更新单元测试；修复缺陷时，必须补充能够复现该缺陷的回归测试
- 后端测试应覆盖状态机、Schema 和完整性校验、会话所有权、乐观锁、幂等、事务、平台映射、失败恢复及错误契约
- 前端测试应覆盖 API/SSE 处理、Pinia Store 状态变化、组件交互、加载与错误状态，不依赖组件内部实现细节
- 单元测试应隔离被测对象；使用 Fake Pi、Mock HTTP 平台、临时 SQLite 数据库和可控输入，禁止依赖真实模型、真实平台、外部网络、真实凭据或共享运行数据
- 自动化测试必须保持确定性；`NODE_ENV=test` 下使用受限 Fake Pi，`AGENT_FAKE_PI=true` 仅可用于测试或本地 UI 演示，不得用于生产或真实人工验收
- 测试应相互独立、可重复执行，不依赖执行顺序，并在结束后释放服务、端口、文件句柄、定时器和临时目录
- 完成代码变更后，在 `agent-web` 目录依次运行 `npm run typecheck`、`npm test` 和 `npm run build`
- 修改 REST/SSE 契约、前后端联动、流程平台集成或关键用户路径时，还必须运行 `npm run test:e2e`

## 相关文档
- **Agent Web 代码生成重设计**：`../doc/业务办理动态页面与Agent前端生成改造执行计划.md`
- **需求文档**：`../doc/rebuild-functional-requirements-optimized.md`
- **Agent Web 技术设计（含阶段分工）**：`../doc/agent-web-tech-design.md`
- **Agent Web 原始技术设计（原始版本，不包括自定义压缩、自动测试验证等新增功能，不冲突的实现细节可以参考该文档）**：`../doc/origin-agent-web-tech-design.md`
- **本地运行说明**：`README.md`

## 注意事项

- 保持代码简洁，避免过度设计，并按照技术设计中的 M0-M6 实施顺序推进
- 当前实现范围和可运行能力以 `README.md` 及现有测试为准；不得把设计中的后续阶段能力误写成已经完成
- 严格保持职责边界：Agent Web 负责开发期需求、流程定义和代码生成闭环，不生成待办、审批详情、审批动作、流程轨迹或 `apply` 后的流程编排
- Agent Web 对目标工程只允许在指定工作区内暂存、预览、备份和确认写入，不得自动创建 Git 分支、提交、推送或 Pull Request
- 不手工编辑 `dist`、`node_modules`、测试报告、SQLite 数据库或其他生成产物
- 不使用 Linux 专用命令示例，如 `rm -rf`、`cp -r`
