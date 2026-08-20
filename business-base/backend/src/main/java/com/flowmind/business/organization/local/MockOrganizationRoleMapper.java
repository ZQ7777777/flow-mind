package com.flowmind.business.organization.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class MockOrganizationRoleMapper {

    public List<String> rolesFor(MockOrganizationRepository.UserRecord user) {
        if (user == null) return Collections.emptyList();
        List<String> roles = new ArrayList<String>();
        String position = user.getPosition();
        if ("业务员".equals(position)) roles.add("sales");
        if ("组长".equals(position)) roles.add("group");
        if ("部门经理".equals(position)) addManagerRoles(roles);
        if ("财务职工".equals(position)) roles.add("finance");
        if ("财务经理".equals(position)) {
            roles.add("finance"); roles.add("finance_manager"); addManagerRoles(roles);
        }
        if ("运营职工".equals(position)) roles.add("operations");
        if ("运营经理".equals(position)) {
            roles.add("operations"); addManagerRoles(roles);
        }
        if ("风控职工".equals(position)) roles.add("risk");
        if ("风控经理".equals(position)) {
            roles.add("risk"); addManagerRoles(roles);
        }
        if ("交割职工".equals(position)) roles.add("delivery");
        if ("交割经理".equals(position)) {
            roles.add("delivery"); addManagerRoles(roles);
        }
        if ("结算职工".equals(position)) roles.add("settlement");
        if ("结算经理".equals(position)) {
            roles.add("settlement"); addManagerRoles(roles);
        }
        if ("ADMIN".equals(user.getUserType()) || "系统管理员".equals(position)) roles.add("admin");
        return Collections.unmodifiableList(roles);
    }

    private void addManagerRoles(List<String> roles) {
        roles.add("department_manager");
        roles.add("manager");
    }
}
