package com.flowmind.business.organization.local;

import com.flowmind.platform.api.dto.DepartmentDTO;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.spi.OrganizationProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Primary
@Profile({"local", "test"})
public class DatabaseOrganizationProvider implements OrganizationProvider {

    private final MockOrganizationRepository repository;
    private final MockOrganizationRoleMapper roleMapper;

    public DatabaseOrganizationProvider(MockOrganizationRepository repository, MockOrganizationRoleMapper roleMapper) {
        this.repository = repository;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<DepartmentDTO> listDepartments() {
        List<DepartmentDTO> result = new ArrayList<DepartmentDTO>();
        for (MockOrganizationRepository.DepartmentRecord row : repository.listDepartments()) {
            result.add(toDepartment(row));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<UserDTO> listUsersByDepartment(String departmentId) {
        requireText(departmentId, "departmentId");
        return filter(null, departmentId);
    }

    @Override
    public List<UserDTO> listUsersByRole(String roleCode) {
        return filter(normalizeRoleCode(roleCode), null);
    }

    @Override
    public List<UserDTO> listUsersByRoleAndDepartment(String roleCode, String departmentId) {
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        requireText(departmentId, "departmentId");
        if ("manager".equals(normalizedRoleCode) || "department_manager".equals(normalizedRoleCode)) {
            Optional<MockOrganizationRepository.DepartmentRecord> department = repository.findDepartment(departmentId);
            if (!department.isPresent() || department.get().getManagerId() == null) return Collections.emptyList();
            Optional<MockOrganizationRepository.UserRecord> manager =
                    repository.findActiveUserById(department.get().getManagerId());
            return manager.isPresent() ? Collections.singletonList(toUser(manager.get())) : Collections.<UserDTO>emptyList();
        }
        return filter(normalizedRoleCode, departmentId);
    }

    @Override
    public Optional<UserDTO> findUser(String userId) {
        requireText(userId, "userId");
        Optional<MockOrganizationRepository.UserRecord> row = repository.findActiveUserById(userId);
        return row.isPresent() ? Optional.of(toUser(row.get())) : Optional.<UserDTO>empty();
    }

    @Override
    public Optional<DepartmentDTO> findDepartment(String departmentId) {
        requireText(departmentId, "departmentId");
        Optional<MockOrganizationRepository.DepartmentRecord> row = repository.findDepartment(departmentId);
        return row.isPresent() ? Optional.of(toDepartment(row.get())) : Optional.<DepartmentDTO>empty();
    }

    private String normalizeRoleCode(String roleCode) {
        requireText(roleCode, "roleCode");
        String normalized = roleCode.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("dept_manager".equals(normalized) || "departmentmanager".equals(normalized)) {
            return "department_manager";
        }
        return normalized;
    }

    private List<UserDTO> filter(String roleCode, String departmentId) {
        List<UserDTO> result = new ArrayList<UserDTO>();
        for (MockOrganizationRepository.UserRecord row : repository.listActiveUsers()) {
            if (departmentId != null && !departmentId.equals(row.getDepartmentId())) continue;
            if (roleCode != null && !roleMapper.rolesFor(row).contains(roleCode)) continue;
            result.add(toUser(row));
        }
        return Collections.unmodifiableList(result);
    }

    private UserDTO toUser(MockOrganizationRepository.UserRecord row) {
        UserDTO user = new UserDTO(row.getId(), row.getRealName());
        user.setDepartmentId(row.getDepartmentId());
        user.setDepartmentName(row.getDepartmentName());
        user.setRoleCodes(new ArrayList<String>(roleMapper.rolesFor(row)));
        user.setActive(Boolean.valueOf(row.isActive()));
        return user;
    }

    private DepartmentDTO toDepartment(MockOrganizationRepository.DepartmentRecord row) {
        return new DepartmentDTO(row.getId(), row.getName(), row.getParentId());
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(fieldName + " is required");
    }
}
