package com.flowmind.platform.api.entity.dto;

public class UserDTO {

    /** 用户 ID。 */
    private String userId;
    /** 用户名称。 */
    private String userName;

    public UserDTO() {
    }

    public UserDTO(String userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
