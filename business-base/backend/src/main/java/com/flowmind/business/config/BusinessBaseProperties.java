package com.flowmind.business.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务基础底座配置。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
@ConfigurationProperties(prefix = "flow-mind.business")
public class BusinessBaseProperties {

    private final LocalUser localUser = new LocalUser();

    public LocalUser getLocalUser() {
        return localUser;
    }

    public static class LocalUser {
        private String headerName = "X-FlowMind-Local-User";
        private String defaultUserId = "u_sales_01";
        private Map<String, String> users = new LinkedHashMap<String, String>();

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getDefaultUserId() {
            return defaultUserId;
        }

        public void setDefaultUserId(String defaultUserId) {
            this.defaultUserId = defaultUserId;
        }

        public Map<String, String> getUsers() {
            return users;
        }

        public void setUsers(Map<String, String> users) {
            this.users = users == null ? new LinkedHashMap<String, String>() : users;
        }
    }
}
