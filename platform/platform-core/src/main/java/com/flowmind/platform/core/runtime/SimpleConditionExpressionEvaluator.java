package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.spi.ConditionExpressionEvaluator;
import com.flowmind.platform.core.definition.ConditionExpressionSyntaxValidator;
import com.flowmind.platform.core.definition.ConditionExpressionSyntaxValidator.ParsedCondition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 简单条件表达式求值器。
 *
 * <p>仅支持单变量简单比较，不支持脚本、函数、括号或逻辑组合。语法解析与发布期冻结校验共用同一入口。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-22
 */
@Component
public class SimpleConditionExpressionEvaluator implements ConditionExpressionEvaluator {

    /**
     * 判断简单条件表达式是否命中当前变量集。
     *
     * @param expression 条件表达式，限定为单变量简单比较
     * @param variables 当前流程变量
     * @return 条件成立时返回 true
     */
    @Override
    public boolean evaluate(String expression, Map<String, Object> variables) {
        ParsedCondition parsed = parseExpression(expression);
        String variableName = parsed.getVariableName();
        String operator = parsed.getOperator();
        String expectedLiteral = parsed.getExpectedLiteral();
        if (variables == null || !variables.containsKey(variableName)) {
            throw invalid("condition variable does not exist: " + variableName);
        }
        Object actual = variables.get(variableName);
        if (actual instanceof Number) {
            return compareNumber(new BigDecimal(actual.toString()), operator, parseNumber(expectedLiteral));
        }
        if (actual instanceof Boolean) {
            return compareBoolean((Boolean) actual, operator, parseBoolean(expectedLiteral));
        }
        if (actual instanceof String) {
            return compareString((String) actual, operator, parseString(expectedLiteral));
        }
        throw invalid("condition variable type is not supported: " + variableName);
    }

    /**
     * 解析平台支持的单变量比较表达式。
     *
     * @param expression 条件表达式
     * @return 已解析的表达式结构
     */
    private ParsedCondition parseExpression(String expression) {
        try {
            return ConditionExpressionSyntaxValidator.parse(expression);
        } catch (IllegalArgumentException ex) {
            throw invalid(ex.getMessage(), ex);
        }
    }

    /**
     * 比较数字变量与数字字面量。
     *
     * @param actual 实际变量值
     * @param operator 比较运算符
     * @param expected 期望字面量值
     * @return 比较结果
     */
    private boolean compareNumber(BigDecimal actual, String operator, BigDecimal expected) {
        int compared = actual.compareTo(expected);
        if (">".equals(operator)) {
            return compared > 0;
        }
        if (">=".equals(operator)) {
            return compared >= 0;
        }
        if ("<".equals(operator)) {
            return compared < 0;
        }
        if ("<=".equals(operator)) {
            return compared <= 0;
        }
        if ("==".equals(operator)) {
            return compared == 0;
        }
        if ("!=".equals(operator)) {
            return compared != 0;
        }
        throw invalid("condition number operator is invalid: " + operator);
    }

    /**
     * 比较布尔变量与布尔字面量。
     *
     * @param actual 实际变量值
     * @param operator 比较运算符
     * @param expected 期望字面量值
     * @return 比较结果
     */
    private boolean compareBoolean(Boolean actual, String operator, Boolean expected) {
        if ("==".equals(operator)) {
            return actual.equals(expected);
        }
        if ("!=".equals(operator)) {
            return !actual.equals(expected);
        }
        throw invalid("condition boolean operator is invalid: " + operator);
    }

    /**
     * 比较字符串变量与字符串字面量。
     *
     * @param actual 实际变量值
     * @param operator 比较运算符
     * @param expected 期望字面量值
     * @return 比较结果
     */
    private boolean compareString(String actual, String operator, String expected) {
        if ("==".equals(operator)) {
            return actual.equals(expected);
        }
        if ("!=".equals(operator)) {
            return !actual.equals(expected);
        }
        throw invalid("condition string operator is invalid: " + operator);
    }

    /**
     * 将表达式右侧字面量解析为数字。
     *
     * @param literal 表达式右侧字面量
     * @return 数字值
     */
    private BigDecimal parseNumber(String literal) {
        try {
            return new BigDecimal(literal);
        } catch (NumberFormatException ex) {
            throw invalid("condition number literal is invalid: " + literal, ex);
        }
    }

    /**
     * 将表达式右侧字面量解析为布尔值。
     *
     * @param literal 表达式右侧字面量
     * @return 布尔值
     */
    private Boolean parseBoolean(String literal) {
        if ("true".equals(literal)) {
            return Boolean.TRUE;
        }
        if ("false".equals(literal)) {
            return Boolean.FALSE;
        }
        throw invalid("condition boolean literal is invalid: " + literal);
    }

    /**
     * 将表达式右侧字面量解析为字符串。
     *
     * @param literal 表达式右侧字面量，必须使用双引号包裹
     * @return 字符串值
     */
    private String parseString(String literal) {
        if (literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"")) {
            return literal.substring(1, literal.length() - 1);
        }
        throw invalid("condition string literal must be quoted: " + literal);
    }

    /**
     * 构造条件表达式非法异常。
     *
     * @param message 错误信息
     * @return 运行时配置异常
     */
    private RuntimeConfigurationException invalid(String message) {
        return new RuntimeConfigurationException(RuntimeErrorCodes.CONDITION_EXPRESSION_INVALID, message);
    }

    /**
     * 构造带根因的条件表达式非法异常。
     *
     * @param message 错误信息
     * @param cause 根因异常
     * @return 运行时配置异常
     */
    private RuntimeConfigurationException invalid(String message, Throwable cause) {
        return new RuntimeConfigurationException(RuntimeErrorCodes.CONDITION_EXPRESSION_INVALID, message, cause);
    }
}
