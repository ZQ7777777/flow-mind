package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.api.spi.DelegateProvider;
import com.flowmind.platform.api.spi.AttachmentAccessProvider;
import com.flowmind.platform.api.spi.FileStorageProvider;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.api.spi.OrganizationProvider;
import com.flowmind.platform.api.spi.WorkflowCallbackHandler;
import com.flowmind.platform.core.runtime.DefaultApproverResolver;
import com.flowmind.platform.core.security.AttachmentAccessGuard;
import com.flowmind.platform.mock.InMemoryFileStorageProvider;
import com.flowmind.platform.mock.InMemoryOrganizationProvider;
import com.flowmind.platform.mock.MockAttachmentAccessProvider;
import com.flowmind.platform.mock.RecordingMessagePublisher;
import com.flowmind.platform.mock.RecordingWorkflowCallbackHandler;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;

/**
 * 本地独立运行入口所需的基础 Bean 和 Mock SPI 默认实现。
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@Configuration
public class PlatformStandaloneConfiguration {

    private static final int SQLITE_BUSY_TIMEOUT_MILLIS = 10000;

    /**
     * 创建本地 SQLite 数据源，默认写入项目 data 目录。
     *
     * @param path SQLite 数据库文件路径，支持 jdbc:sqlite: 前缀或 :memory:
     * @return SQLite 数据源
     */
    @Bean
    @ConditionalOnMissingBean
    public DataSource dataSource(@Value("${flow-mind.platform.sqlite.path:./data/flow-mind.db}") String path) {
        String normalizedPath = path == null || path.trim().isEmpty() ? "./data/flow-mind.db" : path.trim();
        if (!normalizedPath.startsWith("jdbc:sqlite:")
                && !":memory:".equals(normalizedPath)
                && !normalizedPath.startsWith("file:")) {
            File file = new File(normalizedPath);
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
        }
        SQLiteConfig config = new SQLiteConfig();
        config.setBusyTimeout(SQLITE_BUSY_TIMEOUT_MILLIS);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl(normalizedPath.startsWith("jdbc:sqlite:")
                ? normalizedPath : "jdbc:sqlite:" + normalizedPath);
        return dataSource;
    }

    /**
     * 创建 Spring JDBC 操作模板。
     *
     * @param dataSource 数据源
     * @return JDBC 模板
     */
    @Bean
    @ConditionalOnMissingBean
    @DependsOn("platformStandaloneSchemaInitializer")
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 创建本地数据库事务管理器。
     *
     * @param dataSource 数据源
     * @return 平台事务管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 初始化本地 SQLite 表结构和增量脚本。
     *
     * @param dataSource 数据源
     * @return 初始化回调
     */
    @Bean
    @ConditionalOnMissingBean
    public PlatformStandaloneSchemaInitializer platformStandaloneSchemaInitializer(DataSource dataSource) {
        return new PlatformStandaloneSchemaInitializer(dataSource);
    }

    /**
     * 提供本地调试默认用户。
     *
     * @return 当前用户 SPI
     */
    @Bean
    @ConditionalOnMissingBean
    public CurrentUserProvider currentUserProvider() {
        return new StandaloneCurrentUserProvider();
    }

    /** 本地独立应用明确提供允许访问的授权 Mock，生产 Starter 仍默认拒绝。 */
    @Bean
    @ConditionalOnMissingBean
    public AttachmentAccessProvider attachmentAccessProvider() {
        return new MockAttachmentAccessProvider(true);
    }

    @Bean
    @ConditionalOnMissingBean
    public AttachmentAccessGuard attachmentAccessGuard(AttachmentAccessProvider accessProvider) {
        return new AttachmentAccessGuard(accessProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public FileStorageProvider fileStorageProvider() {
        return new InMemoryFileStorageProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public MessagePublisher messagePublisher() {
        return new RecordingMessagePublisher();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowCallbackHandler workflowCallbackHandler() {
        return new RecordingWorkflowCallbackHandler();
    }

    /**
     * 提供空委托关系。
     *
     * @return 委托关系 SPI
     */
    @Bean
    @ConditionalOnMissingBean
    public DelegateProvider delegateProvider() {
        return (principalUserId, at) -> Collections.emptyList();
    }

    @Bean
    @ConditionalOnMissingBean
    public OrganizationProvider organizationProvider() {
        return new InMemoryOrganizationProvider();
    }

    /**
     * 提供本地调试审批人解析器。
     *
     * @return 审批人解析 SPI
     */
    @Bean
    @ConditionalOnMissingBean
    public ApproverResolver approverResolver(OrganizationProvider organizationProvider) {
        return new DefaultApproverResolver(organizationProvider);
    }

    /**
     * 提供本地调试附件服务。
     *
     * @return 附件服务
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }


    /**
     * 本地 SQLite 表结构初始化器。
     */
    static final class PlatformStandaloneSchemaInitializer implements InitializingBean {
        private static final String INIT_SCRIPT = "schema/sqlite/001_init_flow_platform.sql";
        private static final String M2_OPERATION_MIGRATION = "schema/sqlite/002_m2_runtime_operation_actions.sql";
        private static final String ATTACHMENT_OPERATION_MIGRATION =
                "schema/sqlite/003_attachment_operation_actions.sql";

        private final DataSource dataSource;

        private PlatformStandaloneSchemaInitializer(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(INIT_SCRIPT));
                if (!operationRecordSupportsM2Actions(connection)) {
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource(M2_OPERATION_MIGRATION));
                }
                if (!schemaSupportsAttachmentActions(connection)) {
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource(ATTACHMENT_OPERATION_MIGRATION));
                }
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        }

        private boolean operationRecordSupportsM2Actions(Connection connection) throws Exception {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT sql FROM sqlite_master WHERE type = 'table' "
                                 + "AND name = 'process_operation_record'")) {
                if (!resultSet.next() || resultSet.getString("sql") == null) {
                    return false;
                }
                String definition = resultSet.getString("sql");
                return definition.contains("START_AND_SUBMIT") && definition.contains("UPDATE_VARIABLES");
            }
        }

        private boolean schemaSupportsAttachmentActions(Connection connection) throws Exception {
            return tableSupportsAttachmentActions(connection, "process_operation_record")
                    && tableSupportsAttachmentActions(connection, "process_audit_log");
        }

        private boolean tableSupportsAttachmentActions(Connection connection, String tableName) throws Exception {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT sql FROM sqlite_master WHERE type = 'table' "
                                 + "AND name = '" + tableName + "'")) {
                if (!resultSet.next() || resultSet.getString("sql") == null) {
                    return false;
                }
                String definition = resultSet.getString("sql");
                return definition.contains("ATTACHMENT_UPLOAD") && definition.contains("ATTACHMENT_DELETE");
            }
        }
    }

    /**
     * 固定返回本地调试用户的当前用户 SPI。
     */
    private static final class StandaloneCurrentUserProvider implements CurrentUserProvider {
        @Override
        public UserContext getCurrentUser() {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes) {
                HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
                String userId = firstText(request.getHeader("X-Flow-User-Id"), request.getParameter("userId"));
                if (!isBlank(userId)) {
                    return userContext(userId, request.getHeader("X-Flow-User-Name"),
                            request.getHeader("X-Flow-Dept-Id"), request.getHeader("X-Flow-Dept-Name"));
                }
            }
            return userContext("user_sales", null, "dept_sales", null);
        }
    }

    /**
     * 仅供独立调试使用的最小组织架构实现；生产环境由宿主系统提供同名 SPI。
     */
    private static UserContext userContext(String userId, String userName, String departmentId, String departmentName) {
        String resolvedDepartmentId = isBlank(departmentId) ? defaultDepartmentId(userId) : departmentId;
        return new UserContext(userId, isBlank(userName) ? displayName(userId) : userName,
                resolvedDepartmentId, isBlank(departmentName) ? displayDepartmentName(resolvedDepartmentId) : departmentName);
    }

    private static String displayName(String userId) {
        if ("user_sales".equals(userId)) {
            return "Sales User";
        }
        if ("user_manager".equals(userId)) {
            return "Manager User";
        }
        if ("user_finance".equals(userId)) {
            return "Finance User";
        }
        return userId;
    }

    private static String defaultDepartmentId(String userId) {
        if ("user_manager".equals(userId)) {
            return "dept_manager";
        }
        if ("user_finance".equals(userId)) {
            return "dept_finance";
        }
        return "dept_sales";
    }

    private static String displayDepartmentName(String departmentId) {
        if ("dept_manager".equals(departmentId)) {
            return "Manager Department";
        }
        if ("dept_finance".equals(departmentId)) {
            return "Finance Department";
        }
        return "Sales Department";
    }

    private static String firstText(String first, String second) {
        return isBlank(first) ? second : first;
    }

    /**
     * 本地调试附件服务，保存元数据并默认通过必传附件校验。
     */
}
