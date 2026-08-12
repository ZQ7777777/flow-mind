package com.flowmind.business.admin;

import com.flowmind.platform.api.dto.DepartmentDTO;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.spi.OrganizationProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads organization candidates used by the process-definition editor. */
@Service
public class ProcessDefinitionOptionsService {
    private final ObjectProvider<OrganizationProvider> organizationProvider;

    public ProcessDefinitionOptionsService(ObjectProvider<OrganizationProvider> organizationProvider) {
        this.organizationProvider = organizationProvider;
    }

    public ProcessDefinitionOptionsResponse options() {
        OrganizationProvider provider = organizationProvider.getIfAvailable();
        if (provider == null) {
            return empty();
        }
        List<DepartmentDTO> sourceDepartments = safeDepartments(provider);
        List<ProcessDefinitionOptionsResponse.DepartmentOption> departments = new ArrayList<ProcessDefinitionOptionsResponse.DepartmentOption>();
        Map<String, UserDTO> usersById = new LinkedHashMap<String, UserDTO>();
        for (DepartmentDTO department : sourceDepartments) {
            if (department == null || !hasText(department.getDepartmentId())) continue;
            departments.add(new ProcessDefinitionOptionsResponse.DepartmentOption(
                    department.getDepartmentId(), department.getDepartmentName(), department.getParentDepartmentId()));
            for (UserDTO user : safeUsers(provider, department.getDepartmentId())) {
                if (user != null && hasText(user.getUserId()) && !Boolean.FALSE.equals(user.getActive())) {
                    usersById.put(user.getUserId(), user);
                }
            }
        }

        List<ProcessDefinitionOptionsResponse.UserOption> users = new ArrayList<ProcessDefinitionOptionsResponse.UserOption>();
        Set<String> roleCodes = new LinkedHashSet<String>();
        for (UserDTO user : usersById.values()) {
            List<String> userRoles = normalizedRoles(user.getRoleCodes());
            roleCodes.addAll(userRoles);
            users.add(new ProcessDefinitionOptionsResponse.UserOption(user.getUserId(), user.getUserName(),
                    user.getDepartmentId(), user.getDepartmentName(), userRoles));
        }
        List<ProcessDefinitionOptionsResponse.RoleOption> roles = new ArrayList<ProcessDefinitionOptionsResponse.RoleOption>();
        for (String roleCode : roleCodes) {
            roles.add(new ProcessDefinitionOptionsResponse.RoleOption(roleCode, roleCode));
        }
        Comparator<ProcessDefinitionOptionsResponse.RoleOption> roleComparator =
                Comparator.comparing(ProcessDefinitionOptionsResponse.RoleOption::getRoleCode);
        Collections.sort(roles, roleComparator);
        return new ProcessDefinitionOptionsResponse(users, departments, roles);
    }

    private ProcessDefinitionOptionsResponse empty() {
        return new ProcessDefinitionOptionsResponse(Collections.<ProcessDefinitionOptionsResponse.UserOption>emptyList(),
                Collections.<ProcessDefinitionOptionsResponse.DepartmentOption>emptyList(),
                Collections.<ProcessDefinitionOptionsResponse.RoleOption>emptyList());
    }

    private List<DepartmentDTO> safeDepartments(OrganizationProvider provider) {
        try {
            List<DepartmentDTO> result = provider.listDepartments();
            return result == null ? Collections.<DepartmentDTO>emptyList() : result;
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    private List<UserDTO> safeUsers(OrganizationProvider provider, String departmentId) {
        try {
            List<UserDTO> result = provider.listUsersByDepartment(departmentId);
            return result == null ? Collections.<UserDTO>emptyList() : result;
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> normalizedRoles(List<String> values) {
        if (values == null) return Collections.emptyList();
        Set<String> result = new LinkedHashSet<String>();
        for (String value : values) if (hasText(value)) result.add(value.trim());
        return Collections.unmodifiableList(new ArrayList<String>(result));
    }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
