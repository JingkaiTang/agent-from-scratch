package com.github.agent.demo00.tool.impl;

import com.github.agent.demo00.tool.Tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计算器工具 —— 演示最基础的工具调用。
 * 支持简单的四则运算。
 */
public class CalculatorTool implements Tool {

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String description() {
        return "计算数学表达式，支持加减乘除和括号。输入一个数学表达式字符串，返回计算结果。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "expression", Map.of(
                        "type", "string",
                        "description", "数学表达式，例如 '(3 + 5) * 2'"
                )
        ));
        schema.put("required", List.of("expression"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String expression = (String) arguments.get("expression");
        if (expression == null || expression.isBlank()) {
            return "错误: 表达式不能为空";
        }
        try {
            double result = evaluate(expression);
            // 整数结果去掉小数点
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        } catch (Exception e) {
            return "计算失败: " + e.getMessage();
        }
    }

    /**
     * 简易递归下降解析器，处理 +, -, *, /, 括号。
     */
    private double evaluate(String expr) {
        return new Parser(expr.replaceAll("\\s+", "")).parseExpression();
    }

    private static class Parser {
        private final String input;
        private int pos;

        Parser(String input) {
            this.input = input;
            this.pos = 0;
        }

        double parseExpression() {
            double result = parseTerm();
            while (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                char op = input.charAt(pos++);
                double term = parseTerm();
                result = op == '+' ? result + term : result - term;
            }
            return result;
        }

        double parseTerm() {
            double result = parseFactor();
            while (pos < input.length() && (input.charAt(pos) == '*' || input.charAt(pos) == '/')) {
                char op = input.charAt(pos++);
                double factor = parseFactor();
                result = op == '*' ? result * factor : result / factor;
            }
            return result;
        }

        double parseFactor() {
            if (pos < input.length() && input.charAt(pos) == '(') {
                pos++; // skip '('
                double result = parseExpression();
                pos++; // skip ')'
                return result;
            }
            // 解析数字（包括负数）
            int start = pos;
            if (pos < input.length() && input.charAt(pos) == '-') pos++;
            while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
                pos++;
            }
            return Double.parseDouble(input.substring(start, pos));
        }
    }
}
