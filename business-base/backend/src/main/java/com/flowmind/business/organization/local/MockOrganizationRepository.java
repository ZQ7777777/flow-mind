package com.flowmind.business.organization.local;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
@Profile({"local", "test"})
public class MockOrganizationRepository {

    private final JdbcTemplate jdbc;

    public MockOrganizationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DepartmentRecord> listDepartments() {
        return jdbc.query("SELECT id, name, parent_id, manager_id FROM mock_department ORDER BY id",
                departmentMapper());
    }

    public Optional<DepartmentRecord> findDepartment(String departmentId) {
        requireText(departmentId, "departmentId");
        List<DepartmentRecord> rows = jdbc.query(
                "SELECT id, name, parent_id, manager_id FROM mock_department WHERE id = ?",
                departmentMapper(), departmentId);
        return rows.isEmpty() ? Optional.<DepartmentRecord>empty() : Optional.of(rows.get(0));
    }

    public List<UserRecord> listActiveUsers() {
        return jdbc.query(userSelect() + " WHERE u.status = 1 ORDER BY u.id", userMapper());
    }

    public Optional<UserRecord> findActiveUserById(String userId) {
        requireText(userId, "userId");
        return first(jdbc.query(userSelect() + " WHERE u.id = ? AND u.status = 1", userMapper(), userId));
    }

    public Optional<UserRecord> findActiveUserByUsername(String username) {
        requireText(username, "username");
        return first(jdbc.query(userSelect() + " WHERE lower(u.username) = lower(?) AND u.status = 1",
                userMapper(), username.trim()));
    }

    public List<String> listActiveAdministratorIds() {
        return jdbc.queryForList("SELECT id FROM mock_user WHERE user_type = 'ADMIN' AND status = 1 ORDER BY id",
                String.class);
    }
    public boolean isActiveAdministrator(String userId) {
        requireText(userId, "userId");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mock_user WHERE id = ? AND user_type = 'ADMIN' AND status = 1",
                Integer.class, userId);
        return count != null && count.intValue() > 0;
    }

    private String userSelect() {
        return "SELECT u.id, u.username, u.password, u.real_name, u.dept_id, d.name AS dept_name, "
                + "u.position, u.user_type, u.status FROM mock_user u "
                + "JOIN mock_department d ON d.id = u.dept_id";
    }

    private Optional<UserRecord> first(List<UserRecord> rows) {
        return rows.isEmpty() ? Optional.<UserRecord>empty() : Optional.of(rows.get(0));
    }

    private RowMapper<DepartmentRecord> departmentMapper() {
        return new RowMapper<DepartmentRecord>() {
            @Override
            public DepartmentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new DepartmentRecord(rs.getString("id"), rs.getString("name"),
                        rs.getString("parent_id"), rs.getString("manager_id"));
            }
        };
    }

    private RowMapper<UserRecord> userMapper() {
        return new RowMapper<UserRecord>() {
            @Override
            public UserRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new UserRecord(rs.getString("id"), rs.getString("username"),
                        rs.getString("password"), rs.getString("real_name"), rs.getString("dept_id"),
                        rs.getString("dept_name"), rs.getString("position"), rs.getString("user_type"),
                        rs.getInt("status") == 1);
            }
        };
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    public static final class DepartmentRecord {
        private final String id;
        private final String name;
        private final String parentId;
        private final String managerId;

        DepartmentRecord(String id, String name, String parentId, String managerId) {
            this.id = id; this.name = name; this.parentId = parentId; this.managerId = managerId;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getParentId() { return parentId; }
        public String getManagerId() { return managerId; }
    }

    public static final class UserRecord {
        private final String id;
        private final String username;
        private final String password;
        private final String realName;
        private final String departmentId;
        private final String departmentName;
        private final String position;
        private final String userType;
        private final boolean active;

        public UserRecord(String id, String username, String password, String realName, String departmentId,
                          String departmentName, String position, String userType, boolean active) {
            this.id = id; this.username = username; this.password = password; this.realName = realName;
            this.departmentId = departmentId; this.departmentName = departmentName;
            this.position = position; this.userType = userType; this.active = active;
        }

        public String getId() { return id; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getRealName() { return realName; }
        public String getDepartmentId() { return departmentId; }
        public String getDepartmentName() { return departmentName; }
        public String getPosition() { return position; }
        public String getUserType() { return userType; }
        public boolean isActive() { return active; }
    }
}
