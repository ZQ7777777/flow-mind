# M6 RAG 影子发布与回退

## 发布状态机

M6 只控制检索结果，不改变 M2 确定性生成器、质量门禁或语料准入规则。发布按以下顺序推进：

1. `SHADOW`：BM25 是唯一实际输出；Hybrid 在相同过滤候选集上计算，结果只进入审计。
2. `CANARY`：按 `policyVersion:generationId` 的 SHA-256 稳定分桶选择实际输出，比例由 `AGENT_RAG_CANARY_PERCENT` 控制；另一条链路继续作为影子。
3. `HYBRID_DEFAULT`：Hybrid 成为实际输出，BM25 继续作为影子对照。
4. `BM25_ONLY` 或 `AGENT_RAG_FORCE_BM25=true`：只执行 BM25，不计算向量影子，是故障回退路径。

每个 generation 固化 policy version、发布模式、实际模式、影子模式、0–99 分桶和强制回退状态。改变环境配置不会让同一个 generation 在重试过程中漂移；新 generation 按新策略分配。

## 观测

`agent_rag_shadow_observation` 保存实际/影子 top-k key、交集、双方零结果状态和排序耗时。`GET /api/agent/management/rag-shadow-metrics` 按 generation 固化的 policy version 和 release mode 分组，只返回聚合数据，不暴露冻结源码，包括：

- observation 数量；
- 影子消除实际零结果的次数；
- 影子引入零结果的次数；
- top-k 平均交集；
- 实际与影子平均本地排序耗时。

影子 key 不出现在 Agent 工具响应中，因此不能被模型读取或影响当次生成。

## 离线发布验收

冻结任务集中 25 个可生成任务全部以 `BM25 selected + Hybrid shadow` 执行；5 个需求阻断任务不进入检索。

| 指标 | 结果 | 门限 |
| --- | ---: | ---: |
| observations | 25 | 25 |
| Hybrid 消除 BM25 零结果 | 7 | > 0 |
| Hybrid 相对 BM25 的零结果回退 | 0 | 0 |
| BM25 平均本地排序耗时 | < 50 ms | < 50 ms |
| Hybrid 平均本地排序耗时 | < 50 ms | < 50 ms |
| M2 技术/关键业务门禁 | 30/30 | 不回退 |
| 模型调用/费用 | 0 / 0 | 0 / 0 |

该结果证明发布机制和离线检索指标达到门限，不代表真实生产生成成功率。受费用约束，真实模型和真实生产流量均未执行，标记为 `NOT_MEASURED`。

## 操作建议

- 首次部署保持默认 `SHADOW`，积累足够 observation 后再判断。
- 只有在影子回退为 0、延迟满足门限、关键业务断言不下降时进入 `CANARY`。
- 灰度建议从 10% 开始；提升比例时更改 policy version，使新分桶快照可独立审计。
- 出现质量或延迟异常时设置 `AGENT_RAG_FORCE_BM25=true` 并重启后端；随后创建或恢复的生成使用 BM25-only 路径。
