package com.flowmind.platform.api.spi;

import com.flowmind.platform.api.entity.request.AttachmentAccessRequest;

/**
 * 附件访问授权 SPI。
 */
public interface AttachmentAccessProvider {
    /**
     * 判断当前请求是否允许访问附件。
     *
     * @param request 访问请求
     * @return 是否允许
     */
    boolean isAllowed(AttachmentAccessRequest request);
}
