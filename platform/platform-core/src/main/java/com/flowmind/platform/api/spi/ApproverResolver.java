package com.flowmind.platform.api.spi;

import com.flowmind.platform.api.entity.dto.UserDTO;
import com.flowmind.platform.api.entity.request.ApproverResolveRequest;

import java.util.List;

/**
 * 审批人解析 SPI。
 */
public interface ApproverResolver {
    /**
     * 根据审批人解析请求返回可办理用户列表。
     *
     * @param request 审批人解析请求
     * @return 审批人列表
     */
    List<UserDTO> resolveApprovers(ApproverResolveRequest request);
}
