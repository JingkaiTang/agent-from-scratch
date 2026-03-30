package com.github.agent.demo00.tool.impl;

import com.github.agent.demo00.tool.Tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟天气查询工具 —— 演示带参数的工具 + 模拟外部 API。
 * (真实项目中这里会调真实天气 API)
 */
public class WeatherTool implements Tool {

    @Override
    public String name() {
        return "get_weather";
    }

    @Override
    public String description() {
        return "查询指定城市的当前天气情况。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "city", Map.of(
                        "type", "string",
                        "description", "城市名称，例如 '上海'、'北京'"
                )
        ));
        schema.put("required", List.of("city"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String city = (String) arguments.get("city");
        if (city == null || city.isBlank()) {
            return "错误: 请指定城市名称";
        }

        // 模拟天气数据（真实项目替换为 API 调用）
        Map<String, String> mockWeather = Map.of(
                "北京", "晴，气温 22°C，湿度 35%，北风 3 级",
                "上海", "多云转小雨，气温 18°C，湿度 75%，东南风 2 级",
                "广州", "阴，气温 26°C，湿度 80%，微风",
                "深圳", "多云，气温 27°C，湿度 70%，南风 2 级",
                "杭州", "小雨，气温 16°C，湿度 85%，东风 3 级"
        );

        String weather = mockWeather.get(city);
        if (weather != null) {
            return city + "天气: " + weather;
        }
        return city + "天气: 晴到多云，气温 20°C，湿度 50%（模拟数据）";
    }
}
