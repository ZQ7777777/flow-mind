package com.flowmind.business.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMessageResponseTest {

    @Test
    void timeoutJumpContentUsesTargetNodeFromPayload() {
        BusinessUserMessageEntity entity = new BusinessUserMessageEntity();
        entity.setMessageType("TASK_TIMEOUT");
        entity.setContent("original content");
        entity.setPayloadJson("{\"instanceTitle\":\"入金申请\",\"taskName\":\"部门经理审批\","
                + "\"timeoutAction\":\"JUMP\",\"targetNodeCode\":\"finance_confirm\","
                + "\"targetNodeName\":\"财务确认\"}");

        BusinessMessageResponse response = BusinessMessageResponse.from(entity);

        assertThat(response.getContent()).isEqualTo(
                "【超时提醒】流程“入金申请”中的任务“部门经理审批”已超过处理时限，系统将按配置流转至财务确认");
    }
}
