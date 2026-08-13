package com.flowmind.business.organization.local;

import com.flowmind.business.message.BusinessAdministratorProvider;
import com.flowmind.business.security.BusinessAuthorizationProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile({"local", "test"})
public class DatabaseBusinessAuthorizationProvider implements BusinessAuthorizationProvider, BusinessAdministratorProvider {

    private final MockOrganizationRepository repository;

    public DatabaseBusinessAuthorizationProvider(MockOrganizationRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<String> listAdministratorUserIds() {
        return repository.listActiveAdministratorIds();
    }

    @Override
    public boolean isAdministrator(String userId) {
        return userId != null && !userId.trim().isEmpty() && repository.isActiveAdministrator(userId);
    }
}