package com.flowmind.platform.core.security;

import com.flowmind.platform.api.request.AttachmentAccessRequest;
import com.flowmind.platform.api.spi.AttachmentAccessProvider;

/**
 * 附件访问的失败关闭安全边界。
 *
 * @author FlowMind
 * @since 1.0.0
 */
public final class AttachmentAccessGuard {

    private final AttachmentAccessProvider accessProvider;

    public AttachmentAccessGuard(AttachmentAccessProvider accessProvider) {
        this.accessProvider = accessProvider;
    }

    /**
     * 仅在宿主授权 SPI 明确允许时返回 {@code true}。
     *
     * @param request 附件访问上下文
     * @return SPI 缺失、拒绝或异常时均返回 {@code false}
     */
    public boolean isAllowed(AttachmentAccessRequest request) {
        if (accessProvider == null) {
            return false;
        }
        try {
            return accessProvider.isAllowed(request);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
