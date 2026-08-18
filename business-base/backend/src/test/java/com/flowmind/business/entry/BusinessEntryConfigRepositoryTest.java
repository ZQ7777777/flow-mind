package com.flowmind.business.entry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessEntryConfigRepositoryTest {

    private BusinessEntryConfigRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(
                DriverManager.getConnection("jdbc:sqlite::memory:"), true));
        jdbc.execute("PRAGMA foreign_keys = ON");
        jdbc.execute("CREATE TABLE process_definition (id TEXT PRIMARY KEY)");
        jdbc.update("INSERT INTO process_definition (id) VALUES (?)", "definition-1");
        BusinessEntryConfigSchemaInitializer.initialize(jdbc);
        BusinessEntryConfigSchemaInitializer.initialize(jdbc);
        repository = new BusinessEntryConfigRepository(jdbc);
    }

    @Test
    void createsUpdatesUpsertsAndDeletesConfiguration() {
        BusinessEntryConfigEntity entity = config("config-1", "definition-1", "/generated/apply");
        assertThat(repository.insert(entity)).isEqualTo(1);
        assertThat(repository.findByDefinitionId("definition-1")).isPresent();
        assertThat(repository.findById("config-1").get().getCreatedAt()).isNotNull();

        entity.setEntryPageUrl("/generated/apply-v2");
        entity.setEntrySource(BusinessEntrySource.AGENT_GENERATED);
        entity.setEnabled(Boolean.TRUE);
        assertThat(repository.updateById(entity)).isEqualTo(1);
        assertThat(repository.findById("config-1").get().getEntryPageUrl()).isEqualTo("/generated/apply-v2");

        BusinessEntryConfigEntity upsert = config("ignored-new-id", "definition-1", "/generated/apply-v3");
        upsert.setEntrySource(BusinessEntrySource.AGENT_GENERATED);
        repository.upsertByDefinitionId(upsert);
        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findByDefinitionId("definition-1").get().getId()).isEqualTo("config-1");
        assertThat(repository.findByDefinitionId("definition-1").get().getEntryPageUrl())
                .isEqualTo("/generated/apply-v3");

        assertThat(repository.deleteById("config-1")).isEqualTo(1);
        assertThat(repository.deleteById("config-1")).isZero();
    }

    @Test
    void rejectsDuplicateDefinitionBinding() {
        repository.insert(config("config-1", "definition-1", "/generated/one"));

        assertThatThrownBy(() -> repository.insert(config("config-2", "definition-1", "/generated/two")))
                .isInstanceOf(DataAccessException.class);
    }

    private BusinessEntryConfigEntity config(String id, String definitionId, String url) {
        BusinessEntryConfigEntity entity = new BusinessEntryConfigEntity();
        entity.setId(id);
        entity.setDefinitionId(definitionId);
        entity.setEntryPageUrl(url);
        entity.setEntrySource(BusinessEntrySource.MANUAL);
        entity.setEnabled(Boolean.FALSE);
        entity.setCreatedBy("admin-1");
        entity.setUpdatedBy("admin-1");
        return entity;
    }
}
