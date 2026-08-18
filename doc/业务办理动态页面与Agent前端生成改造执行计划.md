# Agent 前端表单生成与参考 Skill 改造执行计划

> 本文以 `doc/会议记录合并实现计划.md` 为权威契约，只覆盖 Agent 前端业务表单生成、生成 Skill 和 `business-base/frontend` 公共承接层。

## 1. 职责边界

- Agent 只生成业务字段组件、发起页薄包装、两个组件测试和生成路由注册，共五个文件。
- Agent 不生成 Java、Controller、Service、DTO、Repository、业务 API 客户端、附件逻辑或审批动作。
- `BusinessForm.vue` 只渲染字段、双向绑定、应用字段权限并暴露 `validate()`；组件内没有提交按钮和 HTTP 调用。
- `Apply.vue` 只把 `processCode` 和 `BusinessForm` 传给 `WorkflowStartShell`。
- 发起、退回申请节点重新提交和审批属于不同业务阶段，不强行共用同一 URL：
  - 发起：`POST /api/workflow/processes/{processCode}/start-submit`
  - 重新提交：`POST /api/workflow/tasks/{taskId}/submit`
  - 审批：`POST /api/workflow/tasks/{taskId}/approve` 等现有动作接口
- 附件、幂等键、加载状态、提交按钮和后端调用全部由 `business-base` 公共壳层承担。

## 2. 项目生成 Skill

项目 Skill 固定放置于：

```text
agent-web/skills/flowmind-business-generation/
├── SKILL.md
├── agents/openai.yaml
└── references/
    ├── backend-api-contract.md
    ├── generated-form-contract.md
    ├── requirement-template.md
    ├── entry-application-example.md
    └── quality-and-boundaries.md
```

约束如下：

- `SKILL.md` frontmatter 只包含 `name` 与 `description`，正文只保留核心执行顺序。
- 项目 Skill 是硬约束；`frontend-design` 仅可作为外部、可选的视觉参考，不复制进项目 Skill，也不进入必需 allowlist。
- 使用 `skill-creator` 生成 `agents/openai.yaml`，并使用其 `quick_validate.py` 校验 Skill。
- 引用最多一层，详细接口、组件契约、样例和质量规则分别存放在 `references/`。

## 3. 需求确认与留痕

`BusinessRequirement` 升级为 1.1：

- `entryDisplayName`
- `entryPageTitle`
- `nodeFieldPermissions[]`

读取 1.0 数据时执行确定性迁移：入口名称默认使用业务名称，页面标题默认使用“发起 + 业务名称”，申请节点按旧字段必填配置生成权限矩阵。确认需求时同时冻结规范化 JSON 和稳定 Markdown 文档。

`GenerationSkillRegistry` 负责：

- 只加载 allowlist 中的 `flowmind-business-generation`。
- 拒绝绝对路径、空路径、`.`、`..`、非 `SKILL.md/references/**` 文件和符号链接。
- 校验 frontmatter、UTF-8、文件大小和目录边界。
- 保存每个文件内容、文件 SHA-256 和 Skill 汇总 SHA-256，形成不可变快照。

Pi 提供只读 `read_generation_skill`。生成完成前必须读取所有必需 Skill 的 `SKILL.md`。重新生成、修复和 Reviewer 均从原生成记录读取同一份快照，不重新读取磁盘上的新版规则。

## 4. 前端专用生成契约

`GenerationTargetContract` 固定为：

```json
{
  "contractVersion": "2.0",
  "generationMode": "FRONTEND_FORM_ONLY"
}
```

旧 1.x 契约返回明确升级错误，不再兼容生成后端代码。每个业务的精确文件集合为：

```text
frontend/src/modules/generated/{business-kebab}/BusinessForm.vue
frontend/src/modules/generated/{business-kebab}/Apply.vue
frontend/src/modules/generated/{business-kebab}/__tests__/BusinessForm.spec.ts
frontend/src/modules/generated/{business-kebab}/__tests__/Apply.spec.ts
frontend/src/router/generated-routes.ts
```

`BusinessForm.vue` 契约：

- props：`modelValue`、`fields`、`fieldPermissions`、`mode`、`disabled`
- emit：`update:modelValue`
- expose：`validate(): Promise<boolean> | boolean`

`generated-routes.ts` 同时维护发起路由和 `processCode → BusinessForm` 异步组件表；更新时必须保留其他业务已有条目。

## 5. business-base 公共壳层

`WorkflowStartShell.vue` 负责：

1. 加载 `GET /api/workflow/processes/{processCode}/start-context`。
2. 向生成表单传入字段、节点字段权限、模式和禁用状态。
3. 调用表单 `validate()`，并校验公共附件规则。
4. 生成 `Idempotency-Key`，抑制重复点击。
5. 以 multipart 调用统一 `start-submit`：
   - `payload` JSON 包含 `definitionId`、`definitionVersion`、`variables`。
   - 每组附件以 `attachmentCode` 作为 part 名称。

通用详情页按 `processCode` 解析相同的 `BusinessForm`：

- 待办且允许 `SUBMIT` 时按当前节点字段权限编辑。
- 已办、我发起的和普通实例详情使用只读模式。
- 没有注册生成组件时回退到现有通用变量表单。
- 审批按钮、意见、附件、流程图和任务动作继续由通用详情页负责。

历史 `/api/generated/entry-application/submit` 仅由后端兼容保留，新生成代码不得调用。

## 6. Prompt、Fake Pi、门禁与报告

- Prompt 只保留业务身份、精确文件集、组件契约、公共提交边界和工具协议；详细规则从 Skill 快照读取。
- Fake Pi 同样读取必需 Skill，并只生成前端五文件。
- 静态门禁检查精确文件集合、Vue/TypeScript 语法、需求字段、权限契约和路由保留，并拦截：
  - Java 和后端目录
  - `/api/generated/**/submit`
  - platform 直连
  - 业务表单中的 HTTP、提交按钮、附件、审批动作和应用壳
- 质量门禁只执行目标前端的 typecheck、test 和 build；后端编译、后端测试在报告中标记为不适用。
- 生成报告包含 Skill 名称和 SHA-256、需求 Markdown、页面路由、表单文件、公共接口、入口配置建议和发布提示；`backendRestartRequired` 固定为 `false`。
- Agent Web 静态预览优先展示 `BusinessForm.vue`，并验证其中不存在提交动作。

## 7. 测试与验收

| 范围 | 验收内容 |
| --- | --- |
| Skill | `quick_validate.py`、allowlist、路径越界、读取门禁、快照内容与哈希 |
| 需求 | 1.0 → 1.1 迁移、完整节点权限矩阵、稳定 Markdown |
| 生成 | 精确五文件、无后端/API 文件、旧路由保留、表单无 submit |
| 公共发起页 | 上下文加载、字段校验失败不提交、附件规则、multipart、定义版本、幂等键 |
| 详情页 | 注册组件解析、通用回退、编辑/只读模式和节点字段权限 |
| 静态门禁 | 业务专用 submit、platform 直连、附件、审批按钮、后端文件均被拦截 |
| 自动化 | Agent Web typecheck/test/build/E2E；business-base frontend typecheck/test/build |

回归样例至少包含入金申请和第二个字段结构不同的流程。两个业务共用公共提交壳，业务字段组件和路由注册互不覆盖。

## 8. 发布边界

- 新后端聚合接口尚未落地时，前端以类型、Mock 和通用 API 客户端完成集成，不修改 platform 或业务后端实现。
- 生成完成后给出入口配置建议，默认 `enabled=false`；前端发布成功后再启用入口。
- 本次只改 `agent-web` 与 `business-base/frontend` 公共承接层，生产发布只需要更新前端资源，`backendRestartRequired=false`。
