package com.flowmind.business.security.local;

import com.flowmind.business.config.BusinessBaseProperties;
import com.flowmind.business.security.BusinessAuthenticationException;
import com.flowmind.business.security.CurrentBusinessUserProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 仅供 local/test 使用的白名单业务用户实现。
 *
 * @author FlowMind
 * @since 2026-08-10
 */
@Component
@Profile({"local", "test"})
public class LocalBusinessUserProvider implements CurrentBusinessUserProvider {

    private final BusinessBaseProperties properties;

    public LocalBusinessUserProvider(BusinessBaseProperties properties) {
        this.properties = properties;
    }

    @Override
    public BusinessUser currentUser() {
        BusinessBaseProperties.LocalUser config = properties.getLocalUser();
        String userId = requestedUserId(config);
        Map<String, String> users = config.getUsers();
        String departmentId = users.get(userId);
        if (departmentId == null || departmentId.trim().isEmpty()) {
            throw new BusinessAuthenticationException("当前本地用户不在服务端白名单中");
        }
        return new BusinessUser(userId, departmentId);
    }

    private String requestedUserId(BusinessBaseProperties.LocalUser config) {
        String userId = null;
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
            userId = request.getHeader(config.getHeaderName());
        }
        if (userId == null || userId.trim().isEmpty()) {
            userId = config.getDefaultUserId();
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new BusinessAuthenticationException("未配置本地默认用户");
        }
        return userId.trim();
    }
}
