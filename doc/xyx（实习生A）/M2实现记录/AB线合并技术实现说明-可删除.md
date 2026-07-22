# M2 运行时联调与 P1 修复技术实现说明

更新日期：2026-07-22

## 条件表达式 SPI

条件表达式契约以 `com.flowmind.platform.api.spi.ConditionExpressionEvaluator` 为唯一入口。删除 core 层的同名重复接口后，`SimpleConditionExpressionEvaluator` 直接实现 API SPI 并作为 Spring 组件提供给 `RuntimeNodeAdvancer`。

推进器继续只负责按有序出线逐条调用 SPI，不解释表达式。M2 的简单求值器支持数字、字符串、布尔值的单变量比较；复杂语法会以稳定的运行时配置错误拒绝。

## 审批请求构造

`RuntimeNodeAdvancer` 通过构造函数接收 `ApproverResolveRequestFactory`。进入用户任务时，推进器仅解析实例变量并将定义快照、实例信息和节点编码传给工厂；工厂通过 `RuntimeNodeConfigReader` 校验并解析审批规则 JSON，随后生成正式的 `ApproverResolveRequest` 交由 `ApproverResolver` 处理。

这样审批规则 JSON 的读取与校验只保留在一个实现点，`APPROVER_EXPRESSION` 仍仅透传给审批人解析 SPI，不会被条件网关求值器误用。

## 附件配置组激活

`ProcessDefinitionAttachmentConfigManager#activateDraftGroup` 从定义范围内读取 DRAFT 配置：

- 没有草稿附件配置时返回通过；
- 恰有一个配置组时，使用现有的组校验与激活逻辑；
- 存在多个或缺失组 ID 时返回 `FLOW_FROZEN_MODEL_ATTACHMENT_CONFIGURATION_INVALID`，拒绝随机选择。

组内读取已按 `definition_id + attachment_config_id` 收敛，避免相同业务组 ID 跨定义误参与校验。

`DefaultProcessDefinitionService#activate` 在停用旧版本、激活目标定义之前调用该方法。定义激活、附件组状态更新与生命周期幂等记录处于同一事务，任一步失败都会回滚；流程图缓存失效注册为提交后回调。成功后 `DefaultProcessRuntimeService` 从定义详情中的唯一 ACTIVE 组写入实例 `attachmentConfigId`，使后续附件校验固定在启动时的配置快照。

## 验证

```powershell
mvn -q -pl platform/platform-core -am test
```

结果：通过，294 项测试全部成功。
