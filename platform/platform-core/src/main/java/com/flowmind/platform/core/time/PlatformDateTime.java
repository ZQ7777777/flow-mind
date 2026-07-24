package com.flowmind.platform.core.time;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 平台业务时间工具，统一生成不带时区字段的北京时间。
 *
 * @author FlowMind
 * @since 2026-07-24
 */
public final class PlatformDateTime {

    /**
     * 平台展示和本地持久化统一使用的业务时区。
     */
    public static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    private PlatformDateTime() {
    }

    /**
     * 获取当前北京时间。
     *
     * @return 当前北京时间，类型为 {@link LocalDateTime}
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(BEIJING_ZONE);
    }
}
