package com.flowmind.business.reference;

import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * 幂等创建宿主统一账户演示数据表，不在启动时灌入或覆盖演示数据。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Component
@DependsOn("platformSchemaInitializer")
public class ReferenceDataSchemaInitializer {

    private final DataSource dataSource;

    public ReferenceDataSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("mock-data/003_reference_data_schema.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.setContinueOnError(false);
        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
