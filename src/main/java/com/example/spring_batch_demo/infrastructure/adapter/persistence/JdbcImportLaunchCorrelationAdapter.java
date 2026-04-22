package com.example.spring_batch_demo.infrastructure.adapter.persistence;

import java.util.OptionalLong;

import com.example.spring_batch_demo.application.customer.port.ImportLaunchCorrelationPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcImportLaunchCorrelationAdapter implements ImportLaunchCorrelationPort {

    private static final String INSERT_IF_ABSENT = """
            INSERT INTO IMPORT_LAUNCH_CORRELATION (CORRELATION_ID, JOB_EXECUTION_ID, CREATED_AT)
            SELECT :correlationId, :jobExecutionId, CURRENT_TIMESTAMP FROM dual
            WHERE NOT EXISTS (
              SELECT 1 FROM IMPORT_LAUNCH_CORRELATION c WHERE c.CORRELATION_ID = :correlationId
            )
            """;

    private static final String SELECT_ID =
            "SELECT JOB_EXECUTION_ID FROM IMPORT_LAUNCH_CORRELATION WHERE CORRELATION_ID = :correlationId";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public JdbcImportLaunchCorrelationAdapter(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public void registerLaunchedJob(String correlationId, long jobExecutionId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("correlationId", correlationId)
                .addValue("jobExecutionId", jobExecutionId);
        namedParameterJdbcTemplate.update(INSERT_IF_ABSENT, params);
    }

    @Override
    public OptionalLong findJobExecutionId(String correlationId) {
        MapSqlParameterSource params = new MapSqlParameterSource("correlationId", correlationId);
        Long id = namedParameterJdbcTemplate.query(
                SELECT_ID,
                params,
                rs -> rs.next() ? rs.getLong("JOB_EXECUTION_ID") : null
        );
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }
}
