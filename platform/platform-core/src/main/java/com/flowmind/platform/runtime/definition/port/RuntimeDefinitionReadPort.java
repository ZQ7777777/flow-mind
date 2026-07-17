package com.flowmind.platform.runtime.definition.port;

/**
 * 运行时流程定义的最小只读加载端口。
 *
 * <p>端口只描述完整定义的读取边界，不暴露流程定义管理服务或持久化能力。</p>
 *
 * @author FlowMind
 * @since 2026-07-17
 */
public interface RuntimeDefinitionReadPort {

    /**
     * 按定义 ID 加载完整的运行时定义输入。
     *
     * <p>实现方应返回非空结果，并保留定义、节点、连线、表单字段和附件配置；
     * 定义不存在或读取失败时应向上抛出明确异常，不得用空输入代替失败。</p>
     *
     * @param definitionId 流程定义 ID
     * @return 完整且不可变的运行时定义输入
     */
    RuntimeDefinitionInput loadDefinition(String definitionId);
}
