package com.flowmind.platform.api.request;

import java.util.List;

public class AddSignRequest extends TaskOperationRequest {

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
