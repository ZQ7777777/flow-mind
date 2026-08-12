package com.flowmind.business.generated.entryapplication.dto;

import java.math.BigDecimal;

/**
 * 入金申请提交请求。
 * <p>
 * 仅承载确认的表单字段；流程编码、发起人身份与部门信息一律由服务端从
 * {@code CurrentBusinessUserProvider} 获取，绝不接受客户端传入。
 */
public class EntryApplicationSubmitRequest {

    /** 申请人姓名（fieldCode: applicantName）。 */
    private String applicantName;

    /** 入金金额（fieldCode: amount），必须大于 0。 */
    private BigDecimal amount;

    public String getApplicantName() {
        return applicantName;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
