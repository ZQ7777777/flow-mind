package com.flowmind.platform.api.request;

import java.util.Map;

/**
 * 开启灰度发布请求。
 */
public class GrayReleaseRequest extends DefinitionOperationRequest {

    /** 灰度规则配置。 */
    private Map<String, Object> grayRuleConfig;

    public GrayReleaseRequest() {
    }

    public Map<String, Object> getGrayRuleConfig() {
        return grayRuleConfig;
    }

    public void setGrayRuleConfig(Map<String, Object> grayRuleConfig) {
        this.grayRuleConfig = grayRuleConfig;
    }
}
