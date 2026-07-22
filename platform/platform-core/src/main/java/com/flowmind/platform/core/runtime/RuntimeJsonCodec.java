package com.flowmind.platform.core.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时结构化 JSON 的唯一编解码入口，支持变量、候选人、节点和分支状态。
 *
 * @author FlowMind
 * @since 2026-07-22
 */
public final class RuntimeJsonCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private RuntimeJsonCodec() {
    }

    /** 将运行时结构序列化为 JSON；空值保持为空。 */
    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to write runtime JSON", ex);
        }
    }

    /**
     * 将幂等记录中的成功结果恢复为指定 DTO 类型。
     *
     * @param json       已保存的 JSON 结果
     * @param targetType 目标 DTO 类型
     * @param <T>        目标 DTO 泛型
     * @return 反序列化后的 DTO
     */
    public static <T> T read(String json, Class<T> targetType) {
        if (isBlank(json) || targetType == null) {
            throw new IllegalArgumentException("Runtime result JSON and target type are required");
        }
        try {
            return OBJECT_MAPPER.readValue(json, targetType);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to read runtime result JSON", ex);
        }
    }

    /** 读取变量或分支状态对象；空值和空白值按空对象处理。 */
    public static Map<String, Object> readObjectMap(String json) {
        if (isBlank(json)) {
            return new LinkedHashMap<String, Object>();
        }
        JsonNode node = readTree(json);
        if (node == null || node.isNull()) {
            return new LinkedHashMap<String, Object>();
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Runtime JSON must be an object");
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to read runtime JSON object", ex);
        }
    }

    /** 读取候选人或当前节点编码数组；空值和空白值按空数组处理。 */
    public static List<String> readStringList(String json) {
        if (isBlank(json)) {
            return new ArrayList<String>();
        }
        JsonNode node = readTree(json);
        if (node == null || node.isNull()) {
            return new ArrayList<String>();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Runtime JSON must be a string array");
        }
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException("Runtime JSON array items must be strings");
            }
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<ArrayList<String>>() { });
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to read runtime JSON string array", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static JsonNode readTree(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to read runtime JSON", ex);
        }
    }
}
