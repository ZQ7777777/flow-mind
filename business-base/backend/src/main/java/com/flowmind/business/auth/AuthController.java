package com.flowmind.business.auth;

import com.flowmind.business.organization.local.MockOrganizationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Profile({"local", "test"})
public class AuthController {

    private final AuthenticationService authenticationService;
    private final SessionAuthentication sessionAuthentication;

    public AuthController(AuthenticationService authenticationService, SessionAuthentication sessionAuthentication) {
        this.authenticationService = authenticationService;
        this.sessionAuthentication = sessionAuthentication;
    }

    @PostMapping("/login")
    public AuthDtos.UserResponse login(@Valid @RequestBody AuthDtos.LoginRequest body,
                                       HttpServletRequest request) {
        MockOrganizationRepository.UserRecord user =
                authenticationService.authenticate(body.getUsername(), body.getPassword());
        sessionAuthentication.establish(request, user.getId());
        return response(user);
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse me(HttpServletRequest request) {
        return response(sessionAuthentication.currentUser(request));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        sessionAuthentication.clear(request);
    }

    private AuthDtos.UserResponse response(MockOrganizationRepository.UserRecord user) {
        AuthDtos.UserResponse response = new AuthDtos.UserResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setDepartmentId(user.getDepartmentId());
        response.setDepartmentName(user.getDepartmentName());
        response.setUserType(user.getUserType());
        response.setAdministrator("ADMIN".equals(user.getUserType()));
        return response;
    }
}
