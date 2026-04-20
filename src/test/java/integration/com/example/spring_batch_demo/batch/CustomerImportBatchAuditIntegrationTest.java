package com.example.spring_batch_demo.batch;

import com.example.spring_batch_demo.SpringBatchDemoApplication;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportResult;
import com.example.spring_batch_demo.application.customer.dto.ImportAuditReport;
import com.example.spring_batch_demo.application.customer.port.CustomerImportUseCase;
import com.example.spring_batch_demo.domain.importaudit.ImportRejectionCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = SpringBatchDemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("audit-it")
class CustomerImportBatchAuditIntegrationTest {

    @Autowired
    private CustomerImportUseCase useCase;

    @Test
    void importPersistsAuditForPolicyFilterAndParseSkip() throws Exception {
        Long jobExecutionId = useCase.launchImport("classpath:customers-import-audit-sample.csv");
        assertNotNull(jobExecutionId);

        awaitTerminalStatus(jobExecutionId);

        CustomerImportResult status = useCase.getImportStatus(jobExecutionId);
        assertNotNull(status);
        assertEquals("COMPLETED", status.status());
        assertTrue(status.filterCount() >= 1, "expected at least one policy-filtered row");
        assertTrue(status.skipCount() >= 1, "expected at least one parse-skipped row");

        ImportAuditReport report = useCase.getImportAuditReport(jobExecutionId, 100, 0);
        assertNotNull(report);
        assertTrue(report.totalRejectedRows() >= 2);
        long policy = report.rows().stream()
                .filter(r -> r.category() == ImportRejectionCategory.POLICY_FILTER)
                .count();
        long parse = report.rows().stream()
                .filter(r -> r.category() == ImportRejectionCategory.PARSE_SKIP)
                .count();
        assertTrue(policy >= 1);
        assertTrue(parse >= 1);
    }

    private void awaitTerminalStatus(Long jobExecutionId) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            CustomerImportResult s = useCase.getImportStatus(jobExecutionId);
            if (s != null && ("COMPLETED".equals(s.status()) || "FAILED".equals(s.status()))) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Job did not reach a terminal status in time: " + jobExecutionId);
    }
}
