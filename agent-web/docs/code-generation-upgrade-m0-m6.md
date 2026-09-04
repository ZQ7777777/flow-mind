# Agent Web 代码生成升级方案（M0–M6）

## 1. 决策摘要

升级目标以 typecheck、build、test 全部通过为硬指标，以业务断言和项目规范为质量指标。MVP 正式支持标准业务模块的新建；已有模块修改进入隐藏回归评测，但不在第一版宣称正式支持。

RAG 是上下文层的增强能力，不是首个里程碑。固定规则和 API 契约必须确定性注入，TypeScript 组件与 API 签名应精确提取，只有历史成功样例和经版本过滤的业务知识适合检索。

目标生成链路为：

```text
自然语言需求
  -> Requirement IR（Schema + 歧义门禁）
  -> 确定性骨架 / 受限模型生成
  -> 独立验收断言
  -> typecheck / test / build / Reviewer
  -> 最多三轮 Repair 或人工 override
```

## 2. M0 基线与可观测性

### 已交付

- `shared/src/evaluation.ts`：Requirement IR 0.1、评测任务和验收断言契约及运行时 Schema。
- `evals/v1/catalog.ts`：30 个冻结任务，分为 20 个开发任务、5 个隐藏回归任务、5 个隐藏对抗任务。
- `evals/v1/runner.ts`：目录完整性检查和离线计分核心。
- `evals/v1/validate.ts`、`score.ts`：不调用模型的命令行入口。
- Runner 单元测试及根工作区命令。

### 暂未执行

真实模型基线需要模型凭据、时间和费用。本 M0 交付不发起真实调用；旧链路的首轮通过率仍是待测值，不能在文档中预设。

项目决策进一步禁止后续阶段使用真实模型或产生模型费用。因此旧链路真实成功率永久标记为 `NOT_MEASURED`，开发阶段只运行 Fake Pi、Schema、契约、生成规范和质量门禁等确定性检查。Codex 负责实现与代码检查，不作为评测集中的被测生成模型。未来若解除该约束，真实基线必须作为单独、有明确费用授权的活动重新批准。

### 指标定义

| 指标 | 定义 | 发布用途 |
|---|---|---|
| generation success rate | 正常完成生成协议的尝试数 / 总尝试数 | 识别协议或工具失败 |
| technical gate pass rate | 最终通过静态校验、typecheck、test、build 的尝试数 / 总尝试数 | 硬门禁 |
| critical assertion pass rate | 通过的关键业务断言 / 全部关键业务断言 | 必须为 100% |
| non-critical assertion pass rate | 通过的非关键断言 / 全部非关键断言 | 发布候选不低于 95% |
| first-pass pass rate | Repair 前已通过全部硬门禁的任务数 / 总任务数 | 衡量生成质量 |
| final pass rate | Repair 结束后通过全部硬门禁的任务数 / 总任务数 | 衡量闭环能力 |
| average repair rounds | Repair 总轮数 / 尝试数 | 越低越好 |
| P95 duration | 单任务端到端耗时的第 95 百分位 | 发布前设置上限 |
| token usage | 输入、输出 token 总数及单任务均值 | 成本比较 |

发布候选使用每个任务三次运行的数据。开发期使用 5 个 smoke tasks，里程碑对全部任务运行一次。具体首轮提升幅度、P95 延迟和 token 上限在旧链路基线产生后冻结。

### 失败分类

每个失败记录一个首要类别，次生诊断通过关联 ID 保存，避免重复计数：

| 类别 | 含义 |
|---|---|
| `REQUIREMENT_AMBIGUITY` | 需求存在未解决的重大歧义 |
| `IR_SCHEMA_INVALID` | IR 不满足结构契约 |
| `IR_REFERENCE_INVALID` | IR 引用了不存在的字段、查询或计算依赖 |
| `CONTEXT_REQUIRED_MISSING` | 未读取任务必需的规范或契约 |
| `CONTEXT_IRRELEVANT` | 检索结果与任务无关并污染生成 |
| `CONTEXT_STALE` | 命中了过期版本知识 |
| `GENERATION_PROTOCOL_INVALID` | 文件报告或工具调用协议不完整 |
| `GENERATED_FILE_SET_INVALID` | 文件缺失、越界或修改受保护文件 |
| `STATIC_VALIDATION_FAILED` | 确定性源码检查失败 |
| `TYPECHECK_FAILED` | TypeScript 类型检查失败 |
| `TEST_FAILED` | 自动化测试失败 |
| `BUILD_FAILED` | 前端构建失败 |
| `BUSINESS_ASSERTION_FAILED` | 独立业务验收未通过 |
| `REVIEW_BLOCKING` | Reviewer 发现高严重度语义问题 |
| `REPAIR_NO_EFFECT` | Repair 未解决目标诊断或引入回归 |
| `INFRASTRUCTURE_FAILED` | 缓存、进程、依赖或环境失败 |

运行记录至少包含 strategy/model 版本、Prompt/context hash、检索 query 与命中、token、耗时、Repair 轮数和各门禁结果。不得将 API Key、完整 Prompt 或完整生成代码写入普通日志。

## 3. Requirement IR 0.1

IR 是代码生成与验收的唯一行为事实源。自然语言和检索材料只能形成或解释 IR，不能绕过 IR 给最终代码增加字段、API、流程动作或业务规则。

IR 0.1 包含：业务身份、字段、分区、参考数据、数据查询、计算、核查、提交流程、歧义和来源引用。影响字段、规则、API、权限或流程行为的开放歧义使用 `BLOCKING`；布局或文案类歧义可标为 `DEFAULTABLE`。只有不存在开放 `BLOCKING` 歧义时才允许生成。

M0 中 IR 仅用于评测，不替换现有 `BusinessRequirement`。M1 需明确二者的转换、兼容和版本升级策略。

## 4. 后续里程碑

### M1：Requirement IR 接入

- 建立 `BusinessRequirement -> Requirement IR` 的确定性规范化器。（已完成）
- 在生成边界重新校验 IR，并把 IR 作为生成 Prompt 的唯一行为事实源。（已完成）
- 从 IR 独立派生基础验收断言。（已完成）
- 对重大歧义阻断，对默认型歧义记录明确决议。（重大歧义阻断已完成；默认型交互留待需求采集 UI 后续迭代）

验收：IR 可追溯、可版本化；相同确认需求产生等价 IR；重大歧义不会进入代码生成。

### M2：确定性生成

- 用代码生成器负责固定 import、页面骨架、共享 Shell、只读 API 和测试装配。（已完成）
- Requirement IR 直接生成字段、分区、动态参考数据、级联参数、数据查询、计算和核查逻辑。（已完成）
- `DETERMINISTIC_IR_V1` 是非测试环境默认生成策略；`PI_LEGACY` 仅保留为显式回退策略。（已完成）
- 生成记录持久化 strategy version，确定性路径不创建 Pi Session，也不使用模型配置。（已完成）
- 冻结任务集离线验证覆盖 25 个可生成任务和 5 个应阻断任务。（已完成）

验收结果：30/30 任务得到预期门禁结果；25 个可生成任务的文件集合、源码语法、行为映射和安全边界通过静态质量门禁，5 个对抗任务被 IR 门禁阻断。项目 typecheck、build 和 M2 定向测试通过。真实模型指标仍为 `NOT_MEASURED`，本阶段模型调用数和模型费用均为 0。

### M3：上下文路由与 Reviewer 门禁

- 按动态数据、多选、级联、计算、核查等 IR 特征建立必读上下文清单。（已完成）
- 上下文快照记录能力标签、必读原因和不可变 hash；每次必读读取均持久化时间戳证据。（已完成）
- 使用 TypeScript Compiler API 提取组件 props、共享类型和只读 API 签名，快照不包含函数实现体。（已完成）
- `BLOCKING` Reviewer finding 进入写入门禁，只有当前 revision、带充分原因的人工 override 才能放行。（已完成）

验收结果：IR 能力路由、接口抽取、必读证据、旧快照兼容和 Reviewer override 审计均有离线测试覆盖。M3 验证只使用 Fake Pi，不调用真实 Reviewer 模型，模型调用数和费用均为 0。

### M4：RAG MVP

- 检索库只收录技术门禁、业务验收和人工审核均通过的样例。（已完成）
- 先使用版本/业务能力元数据过滤，再做全文或 BM25 排序。（已完成）
- 工具先返回 key、摘要、版本、hash 和分数，模型按 key 读取冻结原文。（已完成）
- 记录 query、过滤条件、候选集和最终读取项。（已完成）

验收结果：受“不得调用项目真实模型或产生模型费用”的约束，本阶段只执行离线代理 A/B。冻结任务集的 25 个可生成任务中，“可获得经过版本/能力过滤的已审核上下文”由无 RAG 的 0/25 提升为 BM25 的 18/25；评测任务、生成答案和 golden code 均未进入语料。M2 的 30/30 门禁结果保持不变，因此关键断言未回退。剩余 7 个词法漏召回作为 M5 启用本地向量增强前的证据。真实同模型/Prompt 成对 A/B 为 `NOT_MEASURED`；模型调用数和费用均为 0。

### M5：本地向量增强

- 仅当 M4 证实存在稳定的语义漏召回时启用。（已完成）
- 在 `Retriever` 接口后增加本地 embedding 与向量召回，与全文结果融合排序。（已完成）
- embedding 模型、切块器和索引版本全部进入快照。（已完成）

验收结果：冻结任务集上的已审核上下文可用率由 M4 BM25 的 18/25 提升到本地向量与 BM25 的 RRF 融合结果 25/25。本地 `LOCAL_SEMANTIC_HASH_V1` 使用 256 维确定性特征向量和版本化切块器，不下载模型、不联网、不新增外部数据接收方。M2 的 30/30 关键门禁结果保持不变。真实模型生成成功率仍为 `NOT_MEASURED`；模型调用数和费用均为 0。

### M6：影子发布

- 每次 generation 记录 strategy version。（已完成）
- 新旧链路对同类任务影子比较，先小范围启用，再设为默认。（已完成）
- 保留快速回退旧策略的能力。（已完成）

验收结果：离线 SHADOW 模式在冻结任务集的 25 个可生成任务上保持 BM25 控制输出不变，同时记录 Hybrid 的 7 次零结果改善和 0 次零结果回退；控制组与候选组本地平均排序耗时均低于 50 ms 的测试门限。稳定分桶、10% 灰度、Hybrid 默认启用和 BM25 kill switch 均有确定性测试。M2 的 30/30 技术与关键业务门禁继续通过，模型调用数和费用为 0。真实生产流量发布及真实模型成功率仍为 `NOT_MEASURED`。

## 5. 明确非目标

- M0 不执行真实模型调用，不承诺尚未测量的成功率提升。
- 不索引整个仓库，不把评测标准答案放入检索语料。
- 不新增外部 embedding 服务、Redis、消息队列或分布式调度。
- 不生成运行期待办、审批详情、审批动作或流程轨迹页面。
- 不在本方案内自动创建 Git 分支、提交或推送。
