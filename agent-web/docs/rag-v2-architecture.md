# Flow Mind RAG v2 与一次性模型预算

## 目标

RAG v2 将检索对象从“成功生成的代码文件”扩展为经过审核的生成案例。案例同时保存结构化需求、
验收条件、确定性 Recipe、代码和测试证据，以及它们之间可验证的对应关系。默认生成策略仍为
`DETERMINISTIC_IR_V1`；RAG 只能选择版本化 Recipe，不能把自由文本代码注入确定性渲染器。

首期只证明检索、追踪、来源解释、确定性生成和成本治理能力。`PI_LEGACY` 的真实生成质量保持
`NOT_MEASURED`，不得用离线检索指标代替真实模型 A/B 结论。

## 数据模型

- `agent_rag_case`：不可变案例版本、owner/project/contract 隔离、可见性、晋级证据和 supersedes。
- `agent_rag_node`：Requirement element、Acceptance criterion、Capability、Recipe、Artifact、Symbol、Test。
- `agent_rag_edge`：`decomposes_to`、`requires`、`implemented_by`、`generated_from`、`verified_by`、`supported_by`。
- `agent_rag_chunk`：结构化需求或符号级代码/测试证据，保存路径、行号、Hash 和本地向量。
- `agent_rag_recipe`：由代码审查和测试保护的确定性 Recipe；案例只是证据，不会自动产生任意执行逻辑。
- `agent_rag_index_version`：pending 构建后在同一事务中原子切为 active，旧版本转 retired。
- `agent_generation_knowledge_use`：记录默认生成观察或采用的案例、Recipe、查询条件与证据节点。

旧 `agent_rag_document` 继续作为 `PI_LEGACY` 兼容语料。无法恢复需求、验证报告和 Hash 的历史代码不会
被伪造成 v2 关系。

## 摄取与晋级

晋级要求当前 generation revision 的硬门禁全部通过、所有派生验收条件都有 PASSED 证据、人工确认和
真实写入均完成。晋级事务执行：

1. 冻结 Requirement 和 Requirement IR。
2. 派生能力、验收节点和确定性 Recipe。
3. 按文件和可识别符号生成带源码范围的 chunk。
4. 用代码中真实出现的标识符建立需求到产物关系，用测试产物建立验证关系。
5. 构建 pending 索引并原子激活；同业务旧案例标记 superseded。

模型推断关系不在首期实现范围内。

## 检索

查询先执行 owner、project、contract、capability 和 active 状态过滤。首期在硬过滤后的本地小语料中最多
扫描 2000 个 chunk：FTS/BM25 构成关键词召回分支，256 维确定性本地向量构成同义语义分支，再用 RRF
融合；不会因为 FTS 零命中而跳过向量候选。追踪和影响分析额外对命中的图节点做一跳关系扩展。搜索只
返回 key 和来源元数据，正文读取必须绑定同一 retrieval、同一 owner，并通过 SHA-256 校验和读取审计。

## 十元硬预算

`agent_model_budget_authorization` 保存逐次、限时、按 purpose 和 scope 的人工授权；
`agent_model_budget_reservation` 在真实模型调用前按最大输出和版本化人民币单价预留最坏费用。

- 总预算默认 `10` 元且不会因重启重置。
- 缺少单价、价格版本、授权或余额时 fail-closed。
- provider 未返回可信 usage 时 reservation 标记为 `UNKNOWN`，预留金额不释放，避免不确定扣费被重试放大。
- 账本只保存 Prompt Hash，不复制需求或代码正文。
- Fake Pi、离线评测、RAG 摄取、Embedding、路由和重排不消耗模型预算。
- 模型自动压缩关闭；Reviewer、Legacy Generator 和 Repair 必须分别授权。

## 发布与回退

- `AGENT_RAG_V2_MODE=OFF`：不观察或选择 v2 Recipe。
- `AGENT_RAG_V2_MODE=SHADOW`：记录候选 Recipe，继续使用内建确定性 Recipe。
- `AGENT_RAG_V2_MODE=DEFAULT`：使用兼容的 active Recipe；不兼容 Recipe 立即失败，不做静默降级。

旧 BM25/Hybrid 发布参数继续控制 `PI_LEGACY` 文档检索。两套开关独立，便于回滚。

## 后续非目标

首期不引入 pgvector、Redis Stream、图数据库、收费 Embedding、LLM query rewrite、LLM reranker、
LLM 关系抽取、多实例调度或跨组织知识共享市场。只有离线 golden set 证明本地召回不足后，才评估小型
本地多语言 Embedding；任何付费实验仍须经过预算授权。
