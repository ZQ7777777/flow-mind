package com.flowmind.business.workflow;

import com.flowmind.business.workflow.dto.WorkflowUserCandidateResponse;
import com.flowmind.platform.api.dto.DepartmentDTO;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.spi.OrganizationProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkflowUserSearchService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final ObjectProvider<OrganizationProvider> organizationProvider;

    public WorkflowUserSearchService(ObjectProvider<OrganizationProvider> organizationProvider) {
        this.organizationProvider = organizationProvider;
    }

    public List<WorkflowUserCandidateResponse> search(String keyword, Integer limit) {
        OrganizationProvider directory = organizationProvider.getIfAvailable();
        if (directory == null) {
            return Collections.emptyList();
        }
        int normalizedLimit = normalizeLimit(limit);
        String normalizedKeyword = normalize(keyword);
        Map<String, UserDTO> candidates = new LinkedHashMap<String, UserDTO>();
        if (hasText(keyword)) {
            addExactUser(directory, keyword.trim(), candidates);
        }
        for (DepartmentDTO department : safeDepartments(directory)) {
            if (department == null || !hasText(department.getDepartmentId())) {
                continue;
            }
            for (UserDTO user : safeUsersByDepartment(directory, department.getDepartmentId())) {
                addCandidate(candidates, user);
            }
        }

        List<WorkflowUserCandidateResponse> result = new ArrayList<WorkflowUserCandidateResponse>();
        for (UserDTO user : candidates.values()) {
            if (!active(user) || !matches(user, normalizedKeyword)) {
                continue;
            }
            result.add(toResponse(user));
            if (result.size() >= normalizedLimit) {
                break;
            }
        }
        return Collections.unmodifiableList(result);
    }

    private void addExactUser(OrganizationProvider directory, String userId, Map<String, UserDTO> candidates) {
        try {
            Optional<UserDTO> user = directory.findUser(userId);
            if (user.isPresent()) {
                addCandidate(candidates, user.get());
            }
        } catch (RuntimeException ignored) {
            // Exact lookup is an optimization; department traversal remains the authoritative candidate source.
        }
    }

    private List<DepartmentDTO> safeDepartments(OrganizationProvider directory) {
        List<DepartmentDTO> departments = directory.listDepartments();
        return departments == null ? Collections.<DepartmentDTO>emptyList() : departments;
    }

    private List<UserDTO> safeUsersByDepartment(OrganizationProvider directory, String departmentId) {
        List<UserDTO> users = directory.listUsersByDepartment(departmentId);
        return users == null ? Collections.<UserDTO>emptyList() : users;
    }

    private void addCandidate(Map<String, UserDTO> candidates, UserDTO user) {
        if (user == null || !hasText(user.getUserId()) || candidates.containsKey(user.getUserId())) {
            return;
        }
        candidates.put(user.getUserId(), user);
    }

    private boolean matches(UserDTO user, String keyword) {
        if (!hasText(keyword)) {
            return true;
        }
        return contains(user.getUserId(), keyword)
                || contains(user.getUserName(), keyword)
                || contains(user.getDepartmentId(), keyword)
                || contains(user.getDepartmentName(), keyword);
    }

    private boolean active(UserDTO user) {
        return !Boolean.FALSE.equals(user.getActive());
    }

    private boolean contains(String value, String keyword) {
        return hasText(value) && normalize(value).contains(keyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit.intValue() <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit.intValue(), MAX_LIMIT);
    }

    private WorkflowUserCandidateResponse toResponse(UserDTO user) {
        return new WorkflowUserCandidateResponse(user.getUserId(), user.getUserName(),
                user.getDepartmentId(), user.getDepartmentName());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}