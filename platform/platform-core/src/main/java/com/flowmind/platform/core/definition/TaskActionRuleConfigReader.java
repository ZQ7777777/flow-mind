package com.flowmind.platform.core.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从节点 listenerConfig 中读取 M5 任务动作规则。
 *
 * <p>会签、或签在 M5 只冻结配置，不在此处驱动运行时任务组闭环。增强动作规则继续承载在
 * listenerConfig.taskActionRules 中，不新增独立字段或表结构。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-27
 */
public class TaskActionRuleConfigReader {

    public static final String TASK_ACTION_RULES_KEY = "taskActionRules";
    public static final String REJECT_KEY = "reject";
    public static final String DIRECT_SEND_KEY = "directSend";
    public static final String TARGET_MODE_REJECT_SOURCE = "REJECT_SOURCE";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 读取任务动作规则配置。
     *
     * @param listenerConfig 节点监听器 JSON 配置，taskActionRules 为其中的增强任务动作规则对象
     * @return 结构化后的任务动作规则，空配置返回全量禁用的默认对象
     */
    public TaskActionRules read(String listenerConfig) {
        Map<String, Object> listener = readObject(listenerConfig, "listenerConfig");
        Object rawRules = listener.get(TASK_ACTION_RULES_KEY);
        TaskActionRules result = new TaskActionRules();
        if (rawRules == null) {
            return result;
        }
        Map<String, Object> rules = asObject(rawRules, TASK_ACTION_RULES_KEY);
        readRejectRule(result, asOptionalObject(rules.get(REJECT_KEY), TASK_ACTION_RULES_KEY + ".reject"));
        readDirectSendRule(result, asOptionalObject(rules.get(DIRECT_SEND_KEY),
                TASK_ACTION_RULES_KEY + ".directSend"));
        return result;
    }

    private void readRejectRule(TaskActionRules result, Map<String, Object> reject) {
        if (reject == null) {
            return;
        }
        result.setRejectEnabled(readBoolean(reject, "enabled", TASK_ACTION_RULES_KEY + ".reject.enabled"));
        result.setRejectTargetNodeCodes(readStringList(reject.get("targetNodeCodes"),
                TASK_ACTION_RULES_KEY + ".reject.targetNodeCodes"));
        if (result.isRejectEnabled() && result.getRejectTargetNodeCodes().isEmpty()) {
            throw invalid("reject targetNodeCodes are required when reject is enabled");
        }
    }

    private void readDirectSendRule(TaskActionRules result, Map<String, Object> directSend) {
        if (directSend == null) {
            return;
        }
        result.setDirectSendEnabled(readBoolean(directSend, "enabled",
                TASK_ACTION_RULES_KEY + ".directSend.enabled"));
        result.setDirectSendTargetMode(readOptionalText(directSend.get("targetMode"),
                TASK_ACTION_RULES_KEY + ".directSend.targetMode"));
        if (result.getDirectSendTargetMode() != null
                && !TARGET_MODE_REJECT_SOURCE.equals(result.getDirectSendTargetMode())) {
            throw invalid("directSend targetMode only supports REJECT_SOURCE in M5");
        }
        if (result.isDirectSendEnabled() && result.getDirectSendTargetMode() == null) {
            throw invalid("directSend targetMode is required when directSend is enabled");
        }
    }

    private Map<String, Object> readObject(String json, String path) {
        if (isBlank(json)) {
            return new LinkedHashMap<String, Object>();
        }
        JsonNode node;
        try {
            node = OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            throw invalid(path + " must be valid JSON", ex);
        }
        if (node == null || node.isNull()) {
            return new LinkedHashMap<String, Object>();
        }
        if (!node.isObject()) {
            throw invalid(path + " must be a JSON object");
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException ex) {
            throw invalid(path + " cannot be read as object", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asObject(Object value, String path) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw invalid(path + " must be a JSON object");
    }

    private Map<String, Object> asOptionalObject(Object value, String path) {
        if (value == null) {
            return null;
        }
        return asObject(value, path);
    }

    private boolean readBoolean(Map<String, Object> object, String key, String path) {
        Object value = object.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        throw invalid(path + " must be boolean");
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(Object value, String path) {
        if (value == null) {
            return new ArrayList<String>();
        }
        if (!(value instanceof List)) {
            throw invalid(path + " must be a string array");
        }
        Set<String> unique = new LinkedHashSet<String>();
        for (Object item : (List<Object>) value) {
            if (!(item instanceof String) || isBlank((String) item)) {
                throw invalid(path + " must contain non-blank strings");
            }
            if (!unique.add(((String) item).trim())) {
                throw invalid(path + " must not contain duplicate node codes");
            }
        }
        return new ArrayList<String>(unique);
    }

    private String readOptionalText(Object value, String path) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String) || isBlank((String) value)) {
            throw invalid(path + " must be a non-blank string");
        }
        return ((String) value).trim();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * M5 任务动作规则配置。
     *
     * @author Yuxin Xu
     * @since 2026-07-27
     */
    @Data
    public static class TaskActionRules {
        /**
         * 驳回动作是否启用，true 表示允许按 targetNodeCodes 驳回到指定用户任务节点。
         */
        private boolean rejectEnabled;
        /**
         * 驳回允许到达的目标节点编码列表，JSON 内容来自 taskActionRules.reject.targetNodeCodes。
         */
        private List<String> rejectTargetNodeCodes = new ArrayList<String>();
        /**
         * 直送动作是否启用，true 表示允许从驳回后的节点直送回驳回来源节点。
         */
        private boolean directSendEnabled;
        /**
         * 直送目标模式，M5 仅支持 REJECT_SOURCE。
         */
        private String directSendTargetMode;
    }
}
