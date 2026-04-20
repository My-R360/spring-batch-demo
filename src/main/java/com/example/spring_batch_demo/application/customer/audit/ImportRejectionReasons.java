package com.example.spring_batch_demo.application.customer.audit;

/**
 * Stable operator-facing reasons for {@link com.example.spring_batch_demo.domain.importaudit.RejectedRow}.
 */
public final class ImportRejectionReasons {

    public static final String POLICY_FILTER_INVALID_EMAIL =
            "Row filtered by import policy: email is missing or invalid (must contain '@').";

    public static final String READ_SKIPPED_PREFIX = "Row skipped during CSV read: ";

    public static final String PROCESS_SKIPPED_PREFIX = "Row skipped during item processing: ";

    public static final String WRITE_SKIPPED_PREFIX = "Row skipped during database write: ";

    private ImportRejectionReasons() {
    }

    public static String readSkippedDetail(Throwable cause) {
        return READ_SKIPPED_PREFIX + safeMessage(cause);
    }

    public static String processSkippedDetail(Throwable cause) {
        return PROCESS_SKIPPED_PREFIX + safeMessage(cause);
    }

    public static String writeSkippedDetail(Throwable cause) {
        return WRITE_SKIPPED_PREFIX + safeMessage(cause);
    }

    private static String safeMessage(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String m = t.getMessage();
        if (m != null && !m.isBlank()) {
            return m.trim();
        }
        return t.getClass().getSimpleName();
    }
}
