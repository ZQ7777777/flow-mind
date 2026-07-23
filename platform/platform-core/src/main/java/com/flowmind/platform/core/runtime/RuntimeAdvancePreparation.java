package com.flowmind.platform.core.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A prepared runtime advancement.
 *
 * <p>The preparation contains the candidate users returned by the approver
 * SPI before a task compare-and-set is attempted.  Advancing with this object
 * must therefore not invoke an external resolver after a state mutation.</p>
 */
public final class RuntimeAdvancePreparation {

    private final Map<String, List<String>> candidateUserIdsByNodeCode;
    private final Map<String, String> exclusiveTargetNodeCodes;

    RuntimeAdvancePreparation(Map<String, List<String>> candidateUserIdsByNodeCode,
                              Map<String, String> exclusiveTargetNodeCodes) {
        Map<String, List<String>> copied = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> entry : candidateUserIdsByNodeCode.entrySet()) {
            copied.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<String>(entry.getValue())));
        }
        this.candidateUserIdsByNodeCode = Collections.unmodifiableMap(copied);
        this.exclusiveTargetNodeCodes = Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(exclusiveTargetNodeCodes));
    }

    /** Returns the already-resolved candidate user IDs for a user-task node. */
    List<String> candidateUserIds(String nodeCode) {
        return candidateUserIdsByNodeCode.get(nodeCode);
    }

    /** Returns the condition-gateway target selected during preparation. */
    String exclusiveTargetNodeCode(String nodeCode) {
        return exclusiveTargetNodeCodes.get(nodeCode);
    }
}
