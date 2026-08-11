package com.flowmind.business.organization.local;

import com.flowmind.platform.api.dto.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseOrganizationProviderTest {

    @TempDir
    Path tempDir;
    private JdbcTemplate jdbc;
    private DatabaseOrganizationProvider provider;
    private DatabaseBusinessAuthorizationProvider authorizationProvider;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("provider.db"));
        new MockOrganizationSchemaInitializer(dataSource).initialize();
        jdbc = new JdbcTemplate(dataSource);
        MockOrganizationRepository repository = new MockOrganizationRepository(jdbc);
        provider = new DatabaseOrganizationProvider(repository, new MockOrganizationRoleMapper());
        authorizationProvider = new DatabaseBusinessAuthorizationProvider(repository);
    }

    @Test
    void resolvesEntryApplicationManagersAndFinanceUsers() {
        List<UserDTO> managers = provider.listUsersByRoleAndDepartment("department_manager", "dept_sales");
        List<String> financeIds = provider.listUsersByRole("finance").stream()
                .map(UserDTO::getUserId).collect(Collectors.toList());

        assertThat(managers).extracting(UserDTO::getUserId).containsExactly("u_dept_manager_01");
        assertThat(financeIds).contains("u_finance_01", "u_dept_manager_02");
        assertThat(provider.findDepartment("dept_sales").get().getDepartmentName()).isEqualTo("业务一部");
        assertThat(provider.findUser("u_sales_01").get().getRoleCodes()).containsExactly("sales");
    }

    @Test
    void resolvesAgentGeneratedUppercaseRoleCodes() {
        List<UserDTO> managers = provider.listUsersByRoleAndDepartment("DEPARTMENT_MANAGER", "dept_sales");
        List<String> financeIds = provider.listUsersByRole("FINANCE").stream()
                .map(UserDTO::getUserId).collect(Collectors.toList());

        assertThat(managers).extracting(UserDTO::getUserId).containsExactly("u_dept_manager_01");
        assertThat(financeIds).contains("u_finance_01", "u_dept_manager_02");
    }

    @Test
    void excludesDisabledUsersAndRecognizesOnlyAdministrators() {
        jdbc.update("UPDATE mock_user SET status = 0 WHERE id = 'u_finance_01'");

        assertThat(provider.findUser("u_finance_01")).isEmpty();
        assertThat(provider.listUsersByRole("finance")).extracting(UserDTO::getUserId)
                .doesNotContain("u_finance_01");
        assertThat(authorizationProvider.isAdministrator("u_admin_01")).isTrue();
        assertThat(authorizationProvider.isAdministrator("u_sales_01")).isFalse();
    }
}
