package com.assessment.agentic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsDurableWorkflowTables() {
        List<String> tables = jdbcTemplate.queryForList(
            """
            select table_name
            from information_schema.tables
            where table_schema = 'public'
            """,
            String.class
        );

        assertThat(tables)
            .contains(
                "workflows",
                "workflow_revisions",
                "workflow_tasks",
                "workflow_artifacts",
                "validation_attempts",
                "approvals",
                "audit_events"
            );
    }
}
