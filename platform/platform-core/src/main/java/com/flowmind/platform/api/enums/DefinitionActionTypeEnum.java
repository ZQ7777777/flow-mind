package com.flowmind.platform.api.enums;

/**
 * 流程定义管理动作类型。
 */
public enum DefinitionActionTypeEnum {
    /** 创建流程定义草稿。 */
    CREATE,
    /** 保存流程图结构。 */
    SAVE_GRAPH,
    /** 复制流程定义为新版本草稿。 */
    COPY,
    /** 删除流程定义。 */
    DELETE,
    /** 发布前校验流程定义。 */
    VALIDATE_FOR_PUBLISH,
    /** 发布流程定义。 */
    PUBLISH,
    /** 激活流程定义。 */
    ACTIVATE,
    /** 停用流程定义。 */
    DEACTIVATE,
    /** 归档流程定义。 */
    ARCHIVE,
    /** 开启灰度发布。 */
    ENABLE_GRAY,
    /** 关闭灰度发布。 */
    DISABLE_GRAY;

    /**
     * 返回写入 process_operation_record.action_type 的命名空间动作值。
     *
     * @return 幂等记录动作值
     */
    public String getOperationActionType() {
        return "DEFINITION_" + name();
    }
}
