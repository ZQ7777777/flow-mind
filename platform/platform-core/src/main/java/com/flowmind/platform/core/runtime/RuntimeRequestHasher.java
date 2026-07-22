package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.request.OperationRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 运行时操作请求的规范化 SHA-256 摘要生成器。
 *
 * <p>摘要排除 operationId；Map 键稳定排序；二进制附件内容仅写入其摘要而不写入原始字节。
 * 因而同一请求可安全重试，同时不会把文件内容保存进幂等记录。</p>
 *
 * @author FlowMind
 * @since 2026-07-22
 */
public class RuntimeRequestHasher {

    /**
     * 生成请求的十六进制 SHA-256 摘要。
     *
     * @param request 运行时操作请求
     * @return 排除 operationId 后的稳定摘要
     */
    public String hash(OperationRequest request) {
        if (request == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "operation request is required");
        }
        StringBuilder canonical = new StringBuilder();
        appendValue(canonical, request, new IdentityHashMap<Object, Boolean>());
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 将任意请求字段序列化为带类型和长度的规范化表示，防止字符串拼接碰撞。 */
    private void appendValue(StringBuilder target, Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null) {
            appendToken(target, "null");
        } else if (value instanceof byte[]) {
            appendToken(target, "bytes:" + sha256((byte[]) value));
        } else if (value instanceof CharSequence || value instanceof Character
                || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            appendToken(target, value.getClass().getName() + ":" + String.valueOf(value));
        } else if (value instanceof Map<?, ?>) {
            appendMap(target, (Map<?, ?>) value, visiting);
        } else if (value instanceof Iterable<?>) {
            appendIterable(target, (Iterable<?>) value, visiting, value instanceof Set<?>);
        } else if (value.getClass().isArray()) {
            appendArray(target, value, visiting);
        } else {
            appendObject(target, value, visiting);
        }
    }

    /** 对 Map 键排序；运行时请求的 Map 约束为 String 键。 */
    private void appendMap(StringBuilder target, Map<?, ?> map, IdentityHashMap<Object, Boolean> visiting) {
        appendToken(target, "map:" + map.size());
        List<String> keys = new ArrayList<String>();
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                        "runtime request map keys must be strings");
            }
            keys.add((String) key);
        }
        Collections.sort(keys);
        for (String key : keys) {
            appendToken(target, "key");
            appendToken(target, key);
            appendValue(target, map.get(key), visiting);
        }
    }

    /** List 保持请求语义顺序；Set 先按每个元素的规范化结果排序。 */
    private void appendIterable(StringBuilder target, Iterable<?> values,
                                IdentityHashMap<Object, Boolean> visiting, boolean unordered) {
        List<Object> elements = new ArrayList<Object>();
        for (Object value : values) {
            elements.add(value);
        }
        if (unordered) {
            Collections.sort(elements, new Comparator<Object>() {
                @Override
                public int compare(Object left, Object right) {
                    return canonicalForComparison(left).compareTo(canonicalForComparison(right));
                }
            });
        }
        appendToken(target, (unordered ? "set:" : "list:") + elements.size());
        for (Object element : elements) {
            appendValue(target, element, visiting);
        }
    }

    /** 对对象数组按原有顺序序列化。 */
    private void appendArray(StringBuilder target, Object array, IdentityHashMap<Object, Boolean> visiting) {
        int length = java.lang.reflect.Array.getLength(array);
        appendToken(target, "array:" + length);
        for (int index = 0; index < length; index++) {
            appendValue(target, java.lang.reflect.Array.get(array, index), visiting);
        }
    }

    /** 反射读取请求及嵌套附件字段，跳过 operationId 与静态字段。 */
    private void appendObject(StringBuilder target, Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "runtime request contains a circular object reference");
        }
        try {
            appendToken(target, "object:" + value.getClass().getName());
            List<Field> fields = fieldsOf(value.getClass());
            for (Field field : fields) {
                if ("operationId".equals(field.getName())) {
                    continue;
                }
                field.setAccessible(true);
                appendToken(target, "field:" + field.getDeclaringClass().getName() + "." + field.getName());
                appendValue(target, field.get(value), visiting);
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Unable to read runtime request field", ex);
        } finally {
            visiting.remove(value);
        }
    }

    private List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> current = type; current != null && !Object.class.equals(current);
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }
        Collections.sort(fields, new Comparator<Field>() {
            @Override
            public int compare(Field left, Field right) {
                String leftName = left.getDeclaringClass().getName() + "." + left.getName();
                String rightName = right.getDeclaringClass().getName() + "." + right.getName();
                return leftName.compareTo(rightName);
            }
        });
        return fields;
    }

    private String canonicalForComparison(Object value) {
        StringBuilder canonical = new StringBuilder();
        appendValue(canonical, value, new IdentityHashMap<Object, Boolean>());
        return canonical.toString();
    }

    private void appendToken(StringBuilder target, String value) {
        String text = value == null ? "" : value;
        target.append(text.length()).append(':').append(text).append(';');
    }

    private String sha256(byte[] data) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(String.format("%02x", Byte.valueOf(value)));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
