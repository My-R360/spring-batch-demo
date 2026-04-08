package com.example.spring_batch_demo.infrastructure.config;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class JdbcConfigTest {

    @Test
    void namedParameterJdbcTemplateBeanIsCreated() {
        JdbcConfig config = new JdbcConfig();
        DataSource dataSource = mock(DataSource.class);

        NamedParameterJdbcTemplate jdbc = config.namedParameterJdbcTemplate(dataSource);

        assertNotNull(jdbc);
    }
}
