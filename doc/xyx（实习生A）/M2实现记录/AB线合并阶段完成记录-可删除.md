# M2 运行时联调与 P1 修复阶段完成记录

更新日期：2026-07-22  
范围：将 `feature/m2-b` 快进至 `origin/develop` 后，完成 M2 A/B 线运行时联调与附件配置激活闭环修复。

## 完成项

| 项目 | 状态 | 交付与验收依据 |
| --- | --- | --- |
| 条件表达式 SPI 统一 | 完成 | 删除 `core.runtime.ConditionExpressionEvaluator`；`SimpleConditionExpressionEvaluator` 直接实现既有 `api.spi.ConditionExpressionEvaluator`。`RuntimeNodeAdvancer` 可由 Spring 注入该正式实现。 |
| 排他网关真实求值联调 | 完成 | 新增测试以 `SimpleConditionExpressionEvaluator` 驱动 `RuntimeNodeAdvancer`，验证实例变量 `amount > 100000` 命中正确出线。 |
| 审批请求工厂接入 | 完成 | `RuntimeNodeAdvancer` 构造注入 `ApproverResolveRequestFactory`，用户任务不再手写解析 `approverRuleConfig` 或构造 `ApproverResolveRequest`。 |
| 定义激活同步附件配置组 | 完成 | `DefaultProcessDefinitionService#activate` 在定义状态切换前激活唯一 DRAFT 附件配置组；多个草稿组会拒绝激活，无附件配置仍允许激活。 |
| 附件配置事务一致性 | 完成 | 激活定义失败时，已执行的附件组状态更新随同一事务回滚；新实例可从 ACTIVE 组冻结 `attachmentConfigId`。 |
| 代码审查 | 完成 | 独立子 agent 审查发现的附件组更新行数与跨线回归测试问题均已修复。 |

## 验证记录

执行：

```powershell
mvn -q -pl platform/platform-core -am test
```

结果：通过，`platform-core` 共执行 294 项测试，无失败、无错误、无跳过。

重点覆盖：

- 真实 SPI 条件求值器驱动 B 线排他网关；
- 审批请求由工厂解析配置并交给 `ApproverResolver`；
- 唯一草稿附件组激活、多个草稿组拒绝激活；
- 保存图、发布、激活后由真实定义加载器选择定义，运行时启动实例冻结该配置组 ID；
- 定义状态切换失败时附件组与幂等记录均回滚。

## 仍不在本阶段范围内

- 灰度发布与关闭灰度仍明确抛出 `UnsupportedOperationException`；
- M2 第五步的事务 / Outbox 完整联调不在本次 P1 修复内。
