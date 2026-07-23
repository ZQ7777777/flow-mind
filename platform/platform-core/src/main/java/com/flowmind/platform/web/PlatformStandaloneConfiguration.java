package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.AttachmentDownloadDTO;
import com.flowmind.platform.api.dto.AttachmentTemplateCheckResult;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.CheckAttachmentRequest;
import com.flowmind.platform.api.request.DeleteAttachmentRequest;
import com.flowmind.platform.api.request.DownloadAttachmentRequest;
import com.flowmind.platform.api.request.SaveInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveTaskAttachmentRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.spi.ApproverResolver;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.api.spi.DelegateProvider;
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

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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

    /**
     * 提供本地调试审批人解析器。
     *
     * @return 审批人解析 SPI
     */
    @Bean
    @ConditionalOnMissingBean
    public ApproverResolver approverResolver() {
        return request -> {
            String userId = request == null || isBlank(request.getStarterUserId())
                    ? "mock-user" : request.getStarterUserId();
            return Collections.singletonList(new UserDTO(userId, userId));
        };
    }

    /**
     * 提供本地调试附件服务。
     *
     * @return 附件服务
     */
    @Bean
    @ConditionalOnMissingBean
    public AttachmentService attachmentService() {
        return new StandaloneAttachmentService();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 本地 SQLite 表结构初始化器。
     */
    static final class PlatformStandaloneSchemaInitializer implements InitializingBean {
        private static final String INIT_SCRIPT = "schema/sqlite/001_init_flow_platform.sql";
        private static final String M2_OPERATION_MIGRATION = "schema/sqlite/002_m2_runtime_operation_actions.sql";

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
    }

    /**
     * 固定返回本地调试用户的当前用户 SPI。
     */
    private static final class StandaloneCurrentUserProvider implements CurrentUserProvider {
        @Override
        public UserContext getCurrentUser() {
            return new UserContext("mock-user", "Mock User", "mock-dept", "Mock Department");
        }
    }

    /**
     * 本地调试附件服务，保存元数据并默认通过必传附件校验。
     */
    private static final class StandaloneAttachmentService implements AttachmentService {
        @Override
        public AttachmentDTO saveInstanceAttachment(SaveInstanceAttachmentRequest request) {
            AttachmentDTO dto = toAttachmentDTO(request == null ? null : request.getAttachment());
            dto.setInstanceId(request == null ? null : request.getInstanceId());
            dto.setUploadedBy(request == null ? null : request.getOperatorUserId());
            dto.setOwnerType(com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum.INSTANCE);
            return dto;
        }

        @Override
        public AttachmentDTO saveTaskAttachment(SaveTaskAttachmentRequest request) {
            AttachmentDTO dto = toAttachmentDTO(request == null ? null : request.getAttachment());
            dto.setInstanceId(request == null ? null : request.getInstanceId());
            dto.setTaskId(request == null ? null : request.getTaskId());
            dto.setUploadedBy(request == null ? null : request.getOperatorUserId());
            dto.setOwnerType(com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum.TASK);
            return dto;
        }

        @Override
        public AttachmentDownloadDTO downloadAttachment(DownloadAttachmentRequest request) {
            throw new UnsupportedOperationException("downloadAttachment is not supported by standalone mock");
        }

        @Override
        public List<AttachmentDTO> queryAttachments(com.flowmind.platform.api.dto.AttachmentQuery query) {
            return Collections.emptyList();
        }

        @Override
        public void deleteAttachment(DeleteAttachmentRequest request) {
        }

        @Override
        public AttachmentTemplateCheckResult checkRequiredAttachments(CheckAttachmentRequest request) {
            AttachmentTemplateCheckResult result = new AttachmentTemplateCheckResult();
            result.setPassed(true);
            result.setMissingAttachmentCodes(Collections.emptyList());
            result.setErrors(Collections.emptyList());
            return result;
        }

        private AttachmentDTO toAttachmentDTO(AttachmentUploadItem item) {
            AttachmentDTO dto = new AttachmentDTO();
            dto.setAttachmentId(UUID.randomUUID().toString());
            dto.setAttachmentCode(item == null ? null : item.getAttachmentCode());
            dto.setFileName(item == null ? null : item.getFileName());
            dto.setContentType(item == null ? null : item.getContentType());
            dto.setSizeBytes(item == null ? null : item.getSizeBytes());
            dto.setStorageKey("standalone://" + dto.getAttachmentId());
            dto.setUploadedAt(LocalDateTime.now());
            dto.setDeleted(Boolean.FALSE);
            return dto;
        }
    }
}
