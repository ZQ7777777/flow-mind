package com.flowmind.platform.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程定义发布前校验结果，包含整体是否通过以及具体问题列表。
 *
 * @author Yuxin Xu
 * @since 2026-07-14
 */
@Data
public class ValidationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否校验通过。
     */
    private boolean valid;
    /**
     * 校验问题列表；为空时表示没有发现阻塞问题。
     */
    private List<Issue> issues = new ArrayList<Issue>();

    /**
     * 单条校验问题，定位到流程节点或连线。
     *
     */
    @Data
    public static class Issue implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 问题编码，用于前端分类展示或国际化。
         */
        private String code;
        /**
         * 问题描述。
         */
        private String message;
        /**
         * 关联节点编码，可为空。
         */
        private String nodeCode;
        /**
         * 关联连线编码，可为空。
         */
        private String edgeCode;
    }
}
