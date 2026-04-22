package com.example.spring_batch_demo.infrastructure.adapter.batch;

import com.example.spring_batch_demo.application.customer.audit.ImportRejectionReasons;
import com.example.spring_batch_demo.application.customer.port.ImportAuditPort;
import com.example.spring_batch_demo.domain.customer.Customer;
import com.example.spring_batch_demo.domain.importaudit.ImportRejectionCategory;
import com.example.spring_batch_demo.domain.importaudit.RejectedRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;

@Slf4j
@RequiredArgsConstructor
public class CustomerImportAuditStepListener implements SkipListener<Customer, Customer>, ItemProcessListener<Customer, Customer> {

    private final FlatFileItemReader<Customer> customerReader;
    private final ImportAuditPort importAuditPort;

    @Override
    public void onSkipInRead(Throwable t) {
        long jobExecutionId = requireJobExecutionId();
        if (t instanceof FlatFileParseException ex) {
            importAuditPort.recordRejected(
                    jobExecutionId,
                    new RejectedRow(
                            ImportRejectionCategory.PARSE_SKIP,
                            (long) ex.getLineNumber(),
                            safeMessage(ex),
                            null,
                            null,
                            truncateInput(ex.getInput())
                    )
            );
        } else {
            importAuditPort.recordRejected(
                    jobExecutionId,
                    new RejectedRow(
                            ImportRejectionCategory.READ_SKIPPED,
                            null,
                            ImportRejectionReasons.readSkippedDetail(t),
                            null,
                            null,
                            null
                    )
            );
        }
    }

    @Override
    public void onSkipInProcess(Customer item, Throwable t) {
        long jobExecutionId = requireJobExecutionId();
        Long lineNumber = lineNumberOrNull();
        importAuditPort.recordRejected(
                jobExecutionId,
                new RejectedRow(
                        ImportRejectionCategory.PROCESS_SKIPPED,
                        lineNumber,
                        ImportRejectionReasons.processSkippedDetail(t),
                        item != null && item.id() != null ? String.valueOf(item.id()) : null,
                        item != null ? item.name() : null,
                        item != null ? item.email() : null
                )
        );
    }

    @Override
    public void onSkipInWrite(Customer item, Throwable t) {
        long jobExecutionId = requireJobExecutionId();
        Long lineNumber = lineNumberOrNull();
        importAuditPort.recordRejected(
                jobExecutionId,
                new RejectedRow(
                        ImportRejectionCategory.WRITE_SKIPPED,
                        lineNumber,
                        ImportRejectionReasons.writeSkippedDetail(t),
                        item != null && item.id() != null ? String.valueOf(item.id()) : null,
                        item != null ? item.name() : null,
                        item != null ? item.email() : null
                )
        );
    }

    @Override
    public void beforeProcess(Customer item) {
        // no-op
    }

    @Override
    public void afterProcess(Customer item, Customer result) {
        if (item == null || result != null) {
            return;
        }
        long jobExecutionId = requireJobExecutionId();
        Long lineNumber = lineNumberOrNull();
        importAuditPort.recordRejected(
                jobExecutionId,
                new RejectedRow(
                        ImportRejectionCategory.POLICY_FILTER,
                        lineNumber,
                        ImportRejectionReasons.POLICY_FILTER_INVALID_EMAIL,
                        item.id() != null ? String.valueOf(item.id()) : null,
                        item.name(),
                        item.email()
                )
        );
    }

    @Override
    public void onProcessError(Customer item, Exception e) {
        // no-op
    }

    private static String safeMessage(Throwable t) {
        if (t == null) {
            return "Unknown error";
        }
        String m = t.getMessage();
        if (m != null && !m.isBlank()) {
            return m.trim();
        }
        return t.getClass().getSimpleName();
    }

    private static String truncateInput(String input) {
        if (input == null) {
            return null;
        }
        if (input.length() <= 255) {
            return input;
        }
        return input.substring(0, 255);
    }

    private static long requireJobExecutionId() {
        var context = StepSynchronizationManager.getContext();
        if (context == null || context.getStepExecution() == null) {
            throw new IllegalStateException("No StepExecution bound for import audit listener");
        }
        return context.getStepExecution().getJobExecutionId();
    }

    private Long lineNumberOrNull() {
        try {
            return (long) customerReader.getCurrentItemCount();
        } catch (Exception e) {
            log.debug("Could not resolve reader item index for audit: {}", e.toString());
            return null;
        }
    }
}
