package com.flowmind.platform.api.spi;

import java.util.Map;

/**
 * 条件网关表达式求值 SPI。
 *
 * <p>实现方负责表达式语法、变量读取范围及沙箱控制；运行时推进器仅按流程定义确定的顺序
 * 调用该接口，不解释或拼接表达式。</p>
 *
 * @author FlowMind
 * @since 2026-07-22
 */
public interface ConditionExpressionEvaluator {

    /**
     * 判断条件表达式在当前流程变量快照下是否成立。
     *
     * @param expression 流程定义中保存的非空条件表达式
     * @param variables 当前流程变量快照，只读使用
     * @return 条件是否成立
     */
    boolean evaluate(String expression, Map<String, Object> variables);
}
