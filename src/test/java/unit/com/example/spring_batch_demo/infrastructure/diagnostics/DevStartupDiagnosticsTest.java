package com.example.spring_batch_demo.infrastructure.diagnostics;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DevStartupDiagnosticsTest {

    @Test
    void runSucceedsWhenQueriesWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class))).thenReturn("BATCH_USER");
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("CUSTOMER"));

        DevStartupDiagnostics diagnostics = new DevStartupDiagnostics(jdbcTemplate);
        assertDoesNotThrow(() -> diagnostics.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void runHandlesExceptions() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class))).thenThrow(new RuntimeException("db error"));

        DevStartupDiagnostics diagnostics = new DevStartupDiagnostics(jdbcTemplate);
        assertDoesNotThrow(() -> diagnostics.run(new DefaultApplicationArguments(new String[0])));
    }
}
