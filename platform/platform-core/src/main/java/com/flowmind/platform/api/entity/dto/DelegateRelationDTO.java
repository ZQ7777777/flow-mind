package com.flowmind.platform.api.entity.dto;

import java.time.LocalDateTime;

public class DelegateRelationDTO {

    /** 委托人用户 ID。 */
    private String principalUserId;
    /** 委托人名称快照。 */
    private String principalUserName;
    /** 代理人用户 ID。 */
    private String delegateUserId;
    /** 代理人名称快照。 */
    private String delegateUserName;
    /** 委托关系生效时间。 */
    private LocalDateTime effectiveFrom;
    /** 委托关系失效时间。 */
    private LocalDateTime effectiveTo;

    public DelegateRelationDTO() {
    }

    public DelegateRelationDTO(String principalUserId, String principalUserName, String delegateUserId,
            String delegateUserName, LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
        this.principalUserId = principalUserId;
        this.principalUserName = principalUserName;
        this.delegateUserId = delegateUserId;
        this.delegateUserName = delegateUserName;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public String getPrincipalUserId() {
        return principalUserId;
    }

    public void setPrincipalUserId(String principalUserId) {
        this.principalUserId = principalUserId;
    }

    public String getPrincipalUserName() {
        return principalUserName;
    }

    public void setPrincipalUserName(String principalUserName) {
        this.principalUserName = principalUserName;
    }

    public String getDelegateUserId() {
        return delegateUserId;
    }

    public void setDelegateUserId(String delegateUserId) {
        this.delegateUserId = delegateUserId;
    }

    public String getDelegateUserName() {
        return delegateUserName;
    }

    public void setDelegateUserName(String delegateUserName) {
        this.delegateUserName = delegateUserName;
    }

    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDateTime effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDateTime effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
}
