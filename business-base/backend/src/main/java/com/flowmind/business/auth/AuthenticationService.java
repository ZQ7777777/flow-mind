package com.flowmind.business.auth;

import com.flowmind.business.organization.local.MockOrganizationRepository;
import com.flowmind.business.security.BusinessAuthenticationException;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Profile({"local", "test"})
public class AuthenticationService {

    private static final String INVALID_CREDENTIALS = "用户名或密码错误";
    private final MockOrganizationRepository repository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthenticationService(MockOrganizationRepository repository) {
        this.repository = repository;
    }

    public MockOrganizationRepository.UserRecord authenticate(String username, String password) {
        Optional<MockOrganizationRepository.UserRecord> user = repository.findActiveUserByUsername(username);
        if (!user.isPresent() || password == null || !passwordEncoder.matches(password, user.get().getPassword())) {
            throw new BusinessAuthenticationException(INVALID_CREDENTIALS);
        }
        return user.get();
    }
}
