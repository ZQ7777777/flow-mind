package com.flowmind.platform.core.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 节点监听配置冻结校验器。
 *
 * <p>M5 只冻结 listenerConfig 的可发布结构、事件白名单和处理器标识，不执行监听器。
 * taskActionRules 继续由 {@link TaskActionRuleConfigReader} 在同一 listenerConfig 根对象中读取。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-27
 */
public class NodeListenerConfigReader {

    public static final String LISTENERS_KEY = "listeners";

    private static final String TASK_ACTION_RULES_KEY = TaskActionRuleConfigReader.TASK_ACTION_RULES_KEY;
    private static final String EVENT_TYPE_KEY = "eventType";
    private static final String EVENT_ALIAS_KEY = "event";
    private static final String HANDLER_ID_KEY = "handlerId";
    private static final String HANDLER_ALIAS_KEY = "handler";
    private static final String FAILURE_STRATEGY_KEY = "failureStrategy";
    private static final String PARAMS_KEY = "params";
    private static final String ON_CREATE_KEY = "onCreate";
    private static final String ON_COMPLETE_KEY = "onComplete";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> SUPPORTED_EVENT_TYPES = supportedEventTypes();
    private static final Set<String> SUPPORTED_FAILURE_STRATEGIES = supportedFailureStrategies();
    private static final Set<String> SUPPORTED_ROOT_KEYS = supportedRootKeys();
    private static final Set<String> SUPPORTED_LISTENER_KEYS = supportedListenerKeys();

    private final Set<String> registeredHandlerIds;

    public NodeListenerConfigReader() {
        this(defaultRegisteredHandlerIds());
    }

    public NodeListenerConfigReader(Set<String> registeredHandlerIds) {
        if (registeredHandlerIds == null || registeredHandlerIds.isEmpty()) {
            throw invalid("registered listener handlers must not be empty");
        }
        this.registeredHandlerIds = Collections.unmodifiableSet(new LinkedHashSet<String>(registeredHandlerIds));
    }

    /**
     * 校验节点 listenerConfig 是否符合 M5 发布冻结范围。
     *
     * @param listenerConfig 节点监听 JSON 配置，根节点必须是对象
     */
    public void validate(String listenerConfig) {
        JsonNode root = readRootObject(listenerConfig);
        if (root == null) {
            return;
        }
        validateRootKeys(root);
        validateListeners(root.get(LISTENERS_KEY));
        validateLegacyHandler(root, ON_CREATE_KEY);
        validateLegacyHandler(root, ON_COMPLETE_KEY);
    }

    private JsonNode readRootObject(String listenerConfig) {
        if (isBlank(listenerConfig)) {
            return null;
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(listenerConfig);
        } catch (JsonProcessingException ex) {
            throw invalid("listenerConfig must be valid JSON", ex);
        }
        if (root == null || root.isNull()) {
            return null;
        }
        if (!root.isObject()) {
            throw invalid("listenerConfig must be a JSON object");
        }
        return root;
    }

    private void validateRootKeys(JsonNode root) {
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!SUPPORTED_ROOT_KEYS.contains(fieldName)) {
                throw invalid("listenerConfig contains unsupported key: " + fieldName);
            }
        }
    }

    private void validateListeners(JsonNode listeners) {
        if (listeners == null || listeners.isNull()) {
            return;
        }
        if (!listeners.isArray()) {
            throw invalid("listenerConfig.listeners must be an array");
        }
        for (int index = 0; index < listeners.size(); index++) {
            validateListener(listeners.get(index), "listenerConfig.listeners[" + index + "]");
        }
    }

    private void validateListener(JsonNode listener, String path) {
        if (listener == null || !listener.isObject()) {
            throw invalid(path + " must be a JSON object");
        }
        validateListenerKeys(listener, path);
        String eventType = readRequiredText(firstNode(listener, EVENT_TYPE_KEY, EVENT_ALIAS_KEY),
                path + ".eventType");
        if (!SUPPORTED_EVENT_TYPES.contains(eventType)) {
            throw invalid(path + ".eventType is not supported in M5: " + eventType);
        }
        String handlerId = readRequiredText(firstNode(listener, HANDLER_ID_KEY, HANDLER_ALIAS_KEY),
                path + ".handlerId");
        if (!registeredHandlerIds.contains(handlerId)) {
            throw invalid(path + ".handlerId is not registered: " + handlerId);
        }
        JsonNode failureStrategy = listener.get(FAILURE_STRATEGY_KEY);
        if (failureStrategy != null && !failureStrategy.isNull()) {
            String strategy = readRequiredText(failureStrategy, path + ".failureStrategy");
            if (!SUPPORTED_FAILURE_STRATEGIES.contains(strategy)) {
                throw invalid(path + ".failureStrategy is not supported in M5: " + strategy);
            }
        }
        JsonNode params = listener.get(PARAMS_KEY);
        if (params != null && !params.isNull() && !params.isObject()) {
            throw invalid(path + ".params must be a JSON object");
        }
    }

    private void validateListenerKeys(JsonNode listener, String path) {
        Iterator<String> fieldNames = listener.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!SUPPORTED_LISTENER_KEYS.contains(fieldName)) {
                throw invalid(path + " contains unsupported key: " + fieldName);
            }
        }
    }

    private void validateLegacyHandler(JsonNode root, String key) {
        JsonNode handler = root.get(key);
        if (handler == null || handler.isNull()) {
            return;
        }
        String handlerId = readRequiredText(handler, "listenerConfig." + key);
        if (!registeredHandlerIds.contains(handlerId)) {
            throw invalid("listenerConfig." + key + " handler is not registered: " + handlerId);
        }
    }

    private JsonNode firstNode(JsonNode node, String primaryKey, String aliasKey) {
        JsonNode primary = node.get(primaryKey);
        return primary == null || primary.isNull() ? node.get(aliasKey) : primary;
    }

    private String readRequiredText(JsonNode node, String path) {
        if (node == null || node.isNull() || !node.isTextual() || isBlank(node.asText())) {
            throw invalid(path + " must be a non-blank string");
        }
        return node.asText().trim();
    }

    private static Set<String> defaultRegisteredHandlerIds() {
        Set<String> handlerIds = new LinkedHashSet<String>();
        handlerIds.add("workflowCallbackHandler");
        handlerIds.add("recordingWorkflowCallbackHandler");
        handlerIds.add("callback");
        return handlerIds;
    }

    private static Set<String> supportedEventTypes() {
        Set<String> eventTypes = new LinkedHashSet<String>();
        eventTypes.add(WorkflowEventTypeEnum.TASK_CREATED.name());
        eventTypes.add(WorkflowEventTypeEnum.TASK_COMPLETED.name());
        return Collections.unmodifiableSet(eventTypes);
    }

    private static Set<String> supportedFailureStrategies() {
        Set<String> strategies = new LinkedHashSet<String>();
        strategies.add("IGNORE");
        strategies.add("RETRY");
        strategies.add("FAIL_FAST");
        return Collections.unmodifiableSet(strategies);
    }

    private static Set<String> supportedRootKeys() {
        Set<String> rootKeys = new LinkedHashSet<String>();
        rootKeys.add(LISTENERS_KEY);
        rootKeys.add(TASK_ACTION_RULES_KEY);
        rootKeys.add(ON_CREATE_KEY);
        rootKeys.add(ON_COMPLETE_KEY);
        return Collections.unmodifiableSet(rootKeys);
    }

    private static Set<String> supportedListenerKeys() {
        Set<String> listenerKeys = new LinkedHashSet<String>();
        listenerKeys.add(EVENT_TYPE_KEY);
        listenerKeys.add(EVENT_ALIAS_KEY);
        listenerKeys.add(HANDLER_ID_KEY);
        listenerKeys.add(HANDLER_ALIAS_KEY);
        listenerKeys.add(FAILURE_STRATEGY_KEY);
        listenerKeys.add(PARAMS_KEY);
        return Collections.unmodifiableSet(listenerKeys);
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
}
