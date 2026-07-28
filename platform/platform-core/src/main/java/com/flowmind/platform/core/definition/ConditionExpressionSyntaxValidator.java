package com.flowmind.platform.core.definition;

import lombok.Data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条件表达式冻结语法校验器。
 *
 * <p>M5 仅冻结排他网关非默认出线使用的单变量简单比较表达式，不支持脚本、函数、括号或逻辑组合。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-27
 */
public final class ConditionExpressionSyntaxValidator {

    private static final Pattern SIMPLE_COMPARISON = Pattern.compile(
            "^\\s*([A-Za-z][A-Za-z0-9_]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+?)\\s*$");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private ConditionExpressionSyntaxValidator() {
    }

    /**
     * 解析并校验 M5 支持的条件表达式。
     *
     * @param expression 条件表达式，格式为变量名、比较运算符和右侧字面量
     * @return 已解析的表达式结构
     */
    public static ParsedCondition parse(String expression) {
        if (isBlank(expression)) {
            throw invalid("condition expression must not be blank");
        }
        if (expression.contains("&&") || expression.contains("||")
                || expression.indexOf('(') >= 0 || expression.indexOf(')') >= 0) {
            throw invalid("condition expression only supports simple comparison in M5");
        }
        Matcher matcher = SIMPLE_COMPARISON.matcher(expression);
        if (!matcher.matches()) {
            throw invalid("condition expression syntax is invalid");
        }
        String expectedLiteral = matcher.group(3).trim();
        if (isBlank(expectedLiteral)) {
            throw invalid("condition expression literal must not be blank");
        }
        validateLiteral(matcher.group(2), expectedLiteral);
        return new ParsedCondition(matcher.group(1), matcher.group(2), expectedLiteral);
    }

    private static void validateLiteral(String operator, String literal) {
        if (NUMBER_LITERAL.matcher(literal).matches()) {
            return;
        }
        if ("true".equals(literal) || "false".equals(literal) || isStringLiteral(literal)) {
            if ("==".equals(operator) || "!=".equals(operator)) {
                return;
            }
            throw invalid("condition expression literal only supports equality operator");
        }
        throw invalid("condition expression literal syntax is invalid");
    }

    private static boolean isStringLiteral(String literal) {
        return literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"");
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 条件表达式解析结果。
     *
     * @author Yuxin Xu
     * @since 2026-07-27
     */
    @Data
    public static final class ParsedCondition {
        /**
         * 左侧变量名，必须以英文字母开头，仅包含英文字母、数字和下划线。
         */
        private final String variableName;
        /**
         * 比较运算符，典型取值为 ==、!=、>=、<=、>、<。
         */
        private final String operator;
        /**
         * 右侧字面量文本，运行时会按变量实际类型解析为数字、布尔值或双引号字符串。
         */
        private final String expectedLiteral;
    }
}
