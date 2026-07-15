package com.flowmind.platform.core.validation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class StatusTransitionRules {

    /** 状态迁移白名单：key 为来源状态，value 为允许到达的目标状态集合。 */
    private final Map<String, Set<String>> transitions = new LinkedHashMap<String, Set<String>>();

    /** 注册一条允许的单向迁移规则。 */
    StatusTransitionRules allow(String source, String target) {
        Set<String> targets = transitions.get(source);
        if (targets == null) {
            targets = new LinkedHashSet<String>();
            transitions.put(source, targets);
        }
        targets.add(target);
        return this;
    }

    /** 判断来源状态到目标状态是否在冻结规则允许范围内。 */
    boolean allows(String source, String target) {
        Set<String> targets = transitions.get(source);
        return targets != null && targets.contains(target);
    }

    /** 返回规则表中出现过的全部状态，主要用于后续扩展检查。 */
    Collection<String> statuses() {
        Set<String> statuses = new LinkedHashSet<String>();
        statuses.addAll(transitions.keySet());
        for (Set<String> targets : transitions.values()) {
            statuses.addAll(targets);
        }
        return Collections.unmodifiableSet(statuses);
    }
}
