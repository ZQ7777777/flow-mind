package com.flowmind.platform.api.request;

import java.util.List;

public class AddSignRequest extends TaskOperationRequest {

    /** 当前节点临时新增的办理人 ID 列表。 */
    private List<String> addSignUserIds;

    public AddSignRequest() {
    }

    public List<String> getAddSignUserIds() {
        return addSignUserIds;
    }

    public void setAddSignUserIds(List<String> addSignUserIds) {
        this.addSignUserIds = addSignUserIds;
    }
}
