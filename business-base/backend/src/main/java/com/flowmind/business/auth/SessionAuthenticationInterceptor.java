package com.flowmind.business.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@Profile({"local", "test"})
public class SessionAuthenticationInterceptor implements HandlerInterceptor {

    private final SessionAuthentication authentication;

    public SessionAuthenticationInterceptor(SessionAuthentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        authentication.currentUser(request);
        return true;
    }
}
