package com.flowmind.platform.api.enums;

/**
 * 历史任务的实际办理方式。
 *
 * @author FlowMind
 * @since 1.0.0
 */
public enum HandleTypeEnum {
    /** 普通办理。 */
    NORMAL,
    /** 委托代办。 */
    DELEGATE,
    /** 转办后由目标用户办理。 */
    TRANSFER,
    /** 管理员代办。 */
    ADMIN_PROXY
}
