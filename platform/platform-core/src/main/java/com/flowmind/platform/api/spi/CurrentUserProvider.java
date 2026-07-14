package com.flowmind.platform.api.spi;

import com.flowmind.platform.api.dto.UserContext;

/**
 * 当前用户获取 SPI。
 */
public interface CurrentUserProvider {
    /**
     * 获取当前登录用户上下文。
     *
     * @return 当前用户上下文
     */
    UserContext getCurrentUser();
}
