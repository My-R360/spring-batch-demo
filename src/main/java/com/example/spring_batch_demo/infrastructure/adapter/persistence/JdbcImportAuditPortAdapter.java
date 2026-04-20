package com.example.spring_batch_demo.infrastructure.adapter.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.spring_batch_demo.application.customer.port.ImportAuditPort;
import com.example.spring_batch_demo.domain.importaudit.ImportRejectionCategory;
import com.example.spring_batch_demo.domain.importaudit.RejectedRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JdbcImportAuditPortAdapter implements ImportAuditPort {

    private static final int MAX_REASON_LENGTH = 3800;
    private static final int MAX_SOURCE_FIELD = 255;
    private static final int MAX_SOURCE_ID = 64;

    private static final String INSERT_SQL = """
            INSERT INTO IMPORT_REJECTED_ROW (
              JOB_EXECUTION_ID, CATEGORY, LINE_NUMBER, REASON, SOURCE_ID, SOURCE_NAME, SOURCE_EMAIL
            ) VALUES (
              :jobExecutionId, :category, :lineNumber, :reason, :sourceId, :sourceName, :sourceEmail
            )
            """;

    private static final String COUNT_SQL =
            "SELECT COUNT(*) FROM IMPORT_REJECTED_ROW WHERE JOB_EXECUTION_ID = :jobExecutionId";

    private static final RowMapper<RejectedRow> ROW_MAPPER = (rs, rowNum) -> new RejectedRow(
            ImportRejectionCategory.valueOf(rs.getString("CATEGORY")),
            rs.getObject("LINE_NUMBER") == null ? null : rs.getLong("LINE_NUMBER"),
            rs.getString("REASON"),
            rs.getString("SOURCE_ID"),
            rs.getString("SOURCE_NAME"),
            rs.getString("SOURCE_EMAIL")
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate newTransactionTemplate;

    public JdbcImportAuditPortAdapter(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbc = jdbc;
        this.newTransactionTemplate = new TransactionTemplate(transactionManager);
        this.newTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void recordRejected(long jobExecutionId, RejectedRow row) {
        // Propagate failures: a failed audit insert fails the step so nothing is "silently" dropped.
        // REQUIRES_NEW still isolates this transaction from the chunk transaction lifecycle.
        newTransactionTemplate.executeWithoutResult(status -> insertRow(jobExecutionId, row));
    }

    private void insertRow(long jobExecutionId, RejectedRow row) {
        Map<String, Object> params = new HashMap<>();
        params.put("jobExecutionId", jobExecutionId);
        params.put("category", row.category().name());
        params.put("lineNumber", row.lineNumber());
        params.put("reason", truncate(row.reason(), MAX_REASON_LENGTH));
        params.put("sourceId", truncate(row.sourceId(), MAX_SOURCE_ID));
        params.put("sourceName", truncate(row.sourceName(), MAX_SOURCE_FIELD));
        params.put("sourceEmail", truncate(row.sourceEmail(), MAX_SOURCE_FIELD));
        jdbc.update(INSERT_SQL, params);
    }

    @Override
    public long countRejected(long jobExecutionId) {
        Long n = jdbc.queryForObject(COUNT_SQL, Map.of("jobExecutionId", jobExecutionId), Long.class);
        return n == null ? 0L : n;
    }

    @Override
    public List<RejectedRow> loadRows(long jobExecutionId, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        params.put("jobExecutionId", jobExecutionId);
        params.put("limit", limit);
        params.put("offset", offset);
        String sql = """
                SELECT CATEGORY, LINE_NUMBER, REASON, SOURCE_ID, SOURCE_NAME, SOURCE_EMAIL
                FROM IMPORT_REJECTED_ROW
                WHERE JOB_EXECUTION_ID = :jobExecutionId
                ORDER BY ID
                OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
                """;
        return jdbc.query(sql, params, ROW_MAPPER);
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
