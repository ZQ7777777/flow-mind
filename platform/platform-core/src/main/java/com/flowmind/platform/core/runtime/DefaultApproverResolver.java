package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.request.ApproverResolveRequest;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.OrganizationProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 基于组织架构 SPI 的默认审批人解析器。
 *
 * <p>本实现只消费宿主系统提供的 {@link OrganizationProvider}，不保存组织数据，也不提供本地
 * Mock。表达式规则只支持白名单函数和上下文字段，不执行脚本、反射或任意代码。</p>
 *
 * @author FlowMind
 * @since 2026-07-24
 */
public class DefaultApproverResolver implements ApproverResolver {

    /** 审批规则配置中的用户 ID 列表字段。 */
    private static final String USER_IDS = "userIds";
    /** 审批规则配置中的部门 ID 字段。 */
    private static final String DEPARTMENT_ID = "departmentId";
    /** 审批规则配置中的角色编码字段。 */
    private static final String ROLE_CODE = "roleCode";
    /** 审批规则配置中的表达式字段。 */
    private static final String EXPRESSION = "expression";
    /** 表达式内置部门经理角色编码。 */
    private static final String MANAGER_ROLE = "manager";

    /** 宿主系统提供的组织架构查询 SPI。 */
    private final OrganizationProvider organizationProvider;

    /**
     * 创建默认审批人解析器。
     *
     * @param organizationProvider 组织架构 SPI
     */
    public DefaultApproverResolver(OrganizationProvider organizationProvider) {
        if (organizationProvider == null) {
            throw new RuntimeConfigurationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    "organization provider is required");
        }
        this.organizationProvider = organizationProvider;
    }

    @Override
    public List<UserDTO> resolveApprovers(ApproverResolveRequest request) {
        if (request == null || request.getApproverRuleType() == null) {
            throw config("approver rule type is required");
        }
        List<UserDTO> resolved;
        ApproverRuleTypeEnum ruleType = request.getApproverRuleType();
        switch (ruleType) {
            case USER:
                resolved = resolveUsers(configuredUserIds(request));
                break;
            case STARTER:
                resolved = resolveSingleUser(requiredText(request.getStarterUserId(), "starterUserId"));
                break;
            case DEPARTMENT:
                resolved = organizationProvider.listUsersByDepartment(requiredConfigText(request, DEPARTMENT_ID));
                break;
            case ROLE:
                resolved = organizationProvider.listUsersByRole(requiredConfigText(request, ROLE_CODE));
                break;
            case ROLE_IN_DEPARTMENT:
                resolved = organizationProvider.listUsersByRoleAndDepartment(requiredConfigText(request, ROLE_CODE),
                        roleDepartmentId(request));
                break;
            case APPROVER_EXPRESSION:
                resolved = resolveExpression(request);
                break;
            default:
                throw config("unsupported approver rule type: " + ruleType);
        }
        return requireResolvedUsers(resolved);
    }

    private List<UserDTO> resolveUsers(List<String> userIds) {
        List<UserDTO> users = new ArrayList<UserDTO>();
        for (String userId : userIds) {
            Optional<UserDTO> user = optionalUser(userId);
            if (user.isPresent()) {
                users.add(user.get());
            }
        }
        return users;
    }

    private List<UserDTO> resolveSingleUser(String userId) {
        Optional<UserDTO> user = optionalUser(userId);
        return user.isPresent() ? java.util.Collections.singletonList(user.get())
                : java.util.Collections.<UserDTO>emptyList();
    }

    private Optional<UserDTO> optionalUser(String userId) {
        Optional<UserDTO> user = organizationProvider.findUser(userId);
        return user == null ? Optional.<UserDTO>empty() : user;
    }

    private List<UserDTO> resolveExpression(ApproverResolveRequest request) {
        ExpressionCall call = parseExpressionCall(expressionText(request));
        if ("departmentManager".equals(call.getFunctionName())) {
            requireArgumentCount(call, 1);
            return organizationProvider.listUsersByRoleAndDepartment(MANAGER_ROLE,
                    resolveArgument(call.getArguments().get(0), request));
        }
        if ("roleInDepartment".equals(call.getFunctionName())) {
            requireArgumentCount(call, 2);
            return organizationProvider.listUsersByRoleAndDepartment(
                    resolveArgument(call.getArguments().get(0), request),
                    resolveArgument(call.getArguments().get(1), request));
        }
        if ("role".equals(call.getFunctionName())) {
            requireArgumentCount(call, 1);
            return organizationProvider.listUsersByRole(resolveArgument(call.getArguments().get(0), request));
        }
        if ("department".equals(call.getFunctionName())) {
            requireArgumentCount(call, 1);
            return organizationProvider.listUsersByDepartment(resolveArgument(call.getArguments().get(0), request));
        }
        if ("user".equals(call.getFunctionName())) {
            requireArgumentCount(call, 1);
            return resolveSingleUser(resolveArgument(call.getArguments().get(0), request));
        }
        throw resolveFailure("unsupported approver expression function: " + call.getFunctionName());
    }

    private List<UserDTO> requireResolvedUsers(List<UserDTO> users) {
        Map<String, UserDTO> usersById = new TreeMap<String, UserDTO>();
        if (users != null) {
            for (UserDTO user : users) {
                if (isValidUser(user) && !usersById.containsKey(user.getUserId())) {
                    usersById.put(user.getUserId(), copyUser(user));
                }
            }
        }
        if (usersById.isEmpty()) {
            throw new RuntimeStateException(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED,
                    "approver rule resolved no valid user");
        }
        return new ArrayList<UserDTO>(usersById.values());
    }

    private boolean isValidUser(UserDTO user) {
        return user != null && hasText(user.getUserId()) && !Boolean.FALSE.equals(user.getActive());
    }

    private UserDTO copyUser(UserDTO source) {
        UserDTO target = new UserDTO(source.getUserId(), source.getUserName());
        target.setDepartmentId(source.getDepartmentId());
        target.setDepartmentName(source.getDepartmentName());
        target.setRoleCodes(source.getRoleCodes() == null ? null : new ArrayList<String>(source.getRoleCodes()));
        target.setActive(source.getActive());
        return target;
    }

    private List<String> configuredUserIds(ApproverResolveRequest request) {
        Object value = config(request).get(USER_IDS);
        List<String> userIds = new ArrayList<String>();
        if (value instanceof Collection<?>) {
            for (Object item : (Collection<?>) value) {
                addText(userIds, item);
            }
        } else {
            addText(userIds, value);
        }
        if (userIds.isEmpty()) {
            throw config("userIds must not be empty");
        }
        return userIds;
    }

    private void addText(List<String> values, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && ((String) value).indexOf(',') >= 0) {
            String[] parts = ((String) value).split(",");
            for (String part : parts) {
                addText(values, part);
            }
            return;
        }
        String text = String.valueOf(value).trim();
        if (hasText(text)) {
            values.add(text);
        }
    }

    private String roleDepartmentId(ApproverResolveRequest request) {
        String departmentId = optionalConfigText(request, DEPARTMENT_ID);
        if (hasText(departmentId)) {
            return departmentId;
        }
        return requiredText(request.getStarterDeptId(), "starterDeptId");
    }

    private String requiredConfigText(ApproverResolveRequest request, String key) {
        return requiredText(config(request).get(key), key);
    }

    private String optionalConfigText(ApproverResolveRequest request, String key) {
        Object value = config(request).get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private Map<String, Object> config(ApproverResolveRequest request) {
        Map<String, Object> config = request.getApproverRuleConfig();
        return config == null ? java.util.Collections.<String, Object>emptyMap() : config;
    }

    private String requiredText(Object value, String fieldName) {
        String text = value == null ? null : String.valueOf(value).trim();
        if (!hasText(text)) {
            throw config(fieldName + " must not be blank");
        }
        return text;
    }

    private String expressionText(ApproverResolveRequest request) {
        Object value = config(request).get(EXPRESSION);
        String expression = value == null ? null : String.valueOf(value).trim();
        if (!hasText(expression)) {
            throw resolveFailure("approver expression must not be blank");
        }
        return expression;
    }

    private ExpressionCall parseExpressionCall(String expression) {
        int open = expression.indexOf('(');
        int close = expression.lastIndexOf(')');
        if (open <= 0 || close != expression.length() - 1 || expression.indexOf(')', open) != close) {
            throw resolveFailure("approver expression syntax is invalid");
        }
        String functionName = expression.substring(0, open).trim();
        if (!isIdentifier(functionName)) {
            throw resolveFailure("approver expression function is invalid");
        }
        return new ExpressionCall(functionName, splitArguments(expression.substring(open + 1, close)));
    }

    private List<String> splitArguments(String argumentsText) {
        List<String> arguments = new ArrayList<String>();
        if (!hasText(argumentsText)) {
            return arguments;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < argumentsText.length(); i++) {
            char ch = argumentsText.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
                current.append(ch);
                continue;
            }
            if (ch == ',' && !quoted) {
                addArgument(arguments, current);
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        if (quoted) {
            throw resolveFailure("approver expression string literal is not closed");
        }
        addArgument(arguments, current);
        return arguments;
    }

    private void addArgument(List<String> arguments, StringBuilder current) {
        String argument = current.toString().trim();
        if (!hasText(argument)) {
            throw resolveFailure("approver expression argument must not be blank");
        }
        arguments.add(argument);
    }

    private void requireArgumentCount(ExpressionCall call, int expected) {
        if (call.getArguments().size() != expected) {
            throw resolveFailure("approver expression argument count is invalid: " + call.getFunctionName());
        }
    }

    private String resolveArgument(String argument, ApproverResolveRequest request) {
        if (argument.length() >= 2 && argument.startsWith("\"") && argument.endsWith("\"")) {
            return argument.substring(1, argument.length() - 1);
        }
        if ("starterUserId".equals(argument)) {
            return requiredExpressionText(request.getStarterUserId(), "starterUserId");
        }
        if ("starterDeptId".equals(argument)) {
            return requiredExpressionText(request.getStarterDeptId(), "starterDeptId");
        }
        if (argument.startsWith("variables.")) {
            String key = argument.substring("variables.".length());
            if (!isIdentifier(key)) {
                throw resolveFailure("approver expression variable key is invalid");
            }
            Map<String, Object> variables = request.getVariables();
            Object value = variables == null ? null : variables.get(key);
            return requiredExpressionText(value, "variables." + key);
        }
        throw resolveFailure("approver expression cannot read context: " + argument);
    }

    private String requiredExpressionText(Object value, String fieldName) {
        String text = value == null ? null : String.valueOf(value).trim();
        if (!hasText(text)) {
            throw resolveFailure("approver expression context is blank: " + fieldName);
        }
        return text;
    }

    private RuntimeConfigurationException config(String message) {
        return new RuntimeConfigurationException(RuntimeErrorCodes.NODE_CONFIG_INVALID, message);
    }

    private RuntimeStateException resolveFailure(String message) {
        return new RuntimeStateException(RuntimeErrorCodes.APPROVER_RESOLVE_FAILED, message);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isIdentifier(String value) {
        if (!hasText(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean valid = i == 0 ? Character.isLetter(ch)
                    : Character.isLetterOrDigit(ch) || ch == '_';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static final class ExpressionCall {
        private final String functionName;
        private final List<String> arguments;

        private ExpressionCall(String functionName, List<String> arguments) {
            this.functionName = functionName;
            this.arguments = arguments;
        }

        private String getFunctionName() {
            return functionName;
        }

        private List<String> getArguments() {
            return arguments;
        }
    }
}
