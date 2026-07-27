package com.flowmind.platform.api.spi;

import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.request.ApproverResolveRequest;

import java.util.List;

/**
 * 审批人解析 SPI。
 *
 * <p>平台运行时在创建用户任务前调用该接口。实现方应返回可办理用户列表，未解析到有效
 * 审批人时返回空集合或抛出异常，由运行时统一映射为审批人解析失败。</p>
 *
 * @author FlowMind
 * @since 2026-07-24
 */
public interface ApproverResolver {
    /**
     * 根据审批人解析请求返回可办理用户列表。
     *
     * @param request 审批人解析请求；包含流程、节点、审批规则、发起人和变量快照
     * @return 审批人列表；同一用户 ID 重复出现时运行时按首次出现去重
     */
    List<UserDTO> resolveApprovers(ApproverResolveRequest request);
}
