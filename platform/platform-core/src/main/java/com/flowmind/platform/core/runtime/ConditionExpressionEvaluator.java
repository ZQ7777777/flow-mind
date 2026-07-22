package com.flowmind.platform.core.runtime;

import java.util.Map;

/**
 * 条件表达式求值端口。
 *
 * <p>M2 用于排他网关连线条件判断，只要求返回 true 或 false。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-22
 */
public interface ConditionExpressionEvaluator {

    /**
     * 基于流程变量判断条件表达式是否命中。
     *
     * @param expression 条件表达式，M2 限定为简单比较
     * @param variables 当前流程变量，JSON 对象语义
     * @return 条件命中时返回 true，否则返回 false
     */
    boolean evaluate(String expression, Map<String, Object> variables);
}
