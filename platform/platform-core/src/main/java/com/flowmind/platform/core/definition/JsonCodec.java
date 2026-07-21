package com.flowmind.platform.core.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程定义核心模块内部 JSON 编解码工具，集中处理幂等结果和附件节点编码数组。
 *
 * @author Yuxin Xu
 * @since 2026-07-20
 */
final class JsonCodec {

    private static final String RESULT_DEFINITION_ID = "definitionId";
    private static final String RESULT_DELETED = "deleted";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonCodec() {
    }

    /**
     * 生成定义操作成功结果 JSON。
     *
     * @param definitionId 流程定义 ID，后续幂等重放时会从该字段恢复业务返回值
     * @return 幂等记录 result_json 字段内容
     */
    static String definitionResult(String definitionId) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(RESULT_DEFINITION_ID, definitionId);
        return write(result);
    }

    /**
     * 生成定义删除成功结果 JSON。
     *
     * @param definitionId 被删除的流程定义 ID，后续幂等重放时会作为删除目标返回
     * @return 幂等记录 result_json 字段内容
     */
    static String deleteResult(String definitionId) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(RESULT_DEFINITION_ID, definitionId);
        result.put(RESULT_DELETED, Boolean.TRUE);
        return write(result);
    }

    /**
     * 从幂等结果 JSON 中读取流程定义 ID。
     *
     * @param resultJson 幂等记录 result_json 字段内容
     * @return resultJson 中的 definitionId，字段不存在或 JSON 无法解析时返回 null
     */
    static String extractDefinitionId(String resultJson) {
        if (resultJson == null || resultJson.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(resultJson);
            JsonNode definitionId = root.get(RESULT_DEFINITION_ID);
            return definitionId == null || definitionId.isNull() ? null : definitionId.asText();
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    /**
     * 将字符串列表写为 JSON 字符串数组。
     *
     * @param values 字符串列表；null 表示数据库字段也保持 null
     * @return JSON 字符串数组，列表元素为 null 时按历史行为写为空字符串
     */
    static String toJsonStringArray(List<String> values) {
        if (values == null) {
            return null;
        }
        List<String> normalized = new ArrayList<String>(values.size());
        for (String value : values) {
            normalized.add(value == null ? "" : value);
        }
        return write(normalized);
    }

    /**
     * 读取 JSON 字符串数组。
     *
     * @param json 数据库字段内容；非数组历史值按单个节点编码兼容
     * @return 解析后的字符串列表，空值返回空列表
     */
    static List<String> parseStringArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<String>();
        }
        String trimmed = json.trim();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(trimmed);
            if (!root.isArray()) {
                return new ArrayList<String>(Collections.singletonList(trimmed));
            }
            List<String> values = new ArrayList<String>(root.size());
            for (JsonNode node : root) {
                values.add(node.isNull() ? "null" : node.asText());
            }
            return values;
        } catch (JsonProcessingException ex) {
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
                return new ArrayList<String>(Collections.singletonList(trimmed));
            }
            throw new IllegalArgumentException("json string array is invalid", ex);
        }
    }

    private static String write(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("json write failed", ex);
        }
    }
}
