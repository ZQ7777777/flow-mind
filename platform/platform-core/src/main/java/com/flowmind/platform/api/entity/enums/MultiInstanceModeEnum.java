package com.flowmind.platform.api.entity.enums;

/**
 * 用户任务多实例办理模式。
 */
public enum MultiInstanceModeEnum {
    /** 单人办理。 */
    SINGLE,
    /** 或签，任一办理人完成即可通过。 */
    OR_SIGN,
    /** 会签，多个办理人均需办理。 */
    COUNTERSIGN
}
