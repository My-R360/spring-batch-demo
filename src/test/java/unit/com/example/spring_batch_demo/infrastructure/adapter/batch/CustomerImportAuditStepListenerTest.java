package com.example.spring_batch_demo.infrastructure.adapter.batch;

import com.example.spring_batch_demo.application.customer.audit.ImportRejectionReasons;
import com.example.spring_batch_demo.application.customer.port.ImportAuditPort;
import com.example.spring_batch_demo.domain.customer.Customer;
import com.example.spring_batch_demo.domain.importaudit.ImportRejectionCategory;
import com.example.spring_batch_demo.domain.importaudit.RejectedRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerImportAuditStepListenerTest {

    private final FlatFileItemReader<Customer> reader = mock(FlatFileItemReader.class);
    private final ImportAuditPort auditPort = mock(ImportAuditPort.class);
    private final CustomerImportAuditStepListener listener = new CustomerImportAuditStepListener(reader, auditPort);
    private final StepExecution stepExecution = mock(StepExecution.class);

    @BeforeEach
    void registerStepContext() {
        when(stepExecution.getJobExecutionId()).thenReturn(42L);
        StepSynchronizationManager.register(stepExecution);
    }

    @AfterEach
    void clearStepContext() {
        StepSynchronizationManager.close();
    }

    @Test
    void onSkipInReadRecordsParseSkipFromFlatFileParseException() {
        FlatFileParseException ex = new FlatFileParseException("Parsing error", "x,y", 7);

        listener.onSkipInRead(ex);

        ArgumentCaptor<RejectedRow> captor = ArgumentCaptor.forClass(RejectedRow.class);
        verify(auditPort).recordRejected(eq(42L), captor.capture());
        RejectedRow row = captor.getValue();
        assertEquals(ImportRejectionCategory.PARSE_SKIP, row.category());
        assertEquals(7L, row.lineNumber());
        assertTrue(row.reason().contains("Parsing error"));
    }

    @Test
    void onSkipInReadRecordsReadSkippedForNonFlatFileCause() {
        NumberFormatException ex = new NumberFormatException("not a number");

        listener.onSkipInRead(ex);

        ArgumentCaptor<RejectedRow> captor = ArgumentCaptor.forClass(RejectedRow.class);
        verify(auditPort).recordRejected(eq(42L), captor.capture());
        RejectedRow row = captor.getValue();
        assertEquals(ImportRejectionCategory.READ_SKIPPED, row.category());
        assertTrue(row.reason().contains("not a number"));
    }

    @Test
    void afterProcessRecordsPolicyFilterWhenResultIsNull() {
        when(reader.getCurrentItemCount()).thenReturn(3);
        Customer in = new Customer(2L, "Bob", "bad");

        listener.afterProcess(in, null);

        ArgumentCaptor<RejectedRow> captor = ArgumentCaptor.forClass(RejectedRow.class);
        verify(auditPort).recordRejected(eq(42L), captor.capture());
        RejectedRow row = captor.getValue();
        assertEquals(ImportRejectionCategory.POLICY_FILTER, row.category());
        assertEquals(3L, row.lineNumber());
        assertEquals(ImportRejectionReasons.POLICY_FILTER_INVALID_EMAIL, row.reason());
        assertEquals("2", row.sourceId());
        assertEquals("Bob", row.sourceName());
        assertEquals("bad", row.sourceEmail());
    }
}
