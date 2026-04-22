package com.example.spring_batch_demo.presentation.api.exceptions;

import java.net.URI;

import com.example.spring_batch_demo.application.customer.exceptions.ImportCommandPublishException;
import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;
import com.example.spring_batch_demo.application.customer.exceptions.InvalidCorrelationIdException;
import com.example.spring_batch_demo.application.customer.exceptions.InvalidInputFileResourceException;
import com.example.spring_batch_demo.application.customer.exceptions.MissingInputFileException;
import com.example.spring_batch_demo.presentation.api.BatchJobController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * HTTP mapping for errors raised by {@link BatchJobController} — keeps the controller thin.
 *
 * <p>Depends on application-layer exception <strong>types</strong> only (imports their classes for
 * {@link ExceptionHandler} dispatch). It does not implement business logic; it maps those types to
 * {@link ProblemDetail} and HTTP status codes.</p>
 */
@RestControllerAdvice(assignableTypes = BatchJobController.class)
public class BatchJobApiExceptionHandler {

    @ExceptionHandler(MissingInputFileException.class)
    public ProblemDetail onMissingInputFile(MissingInputFileException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage() != null ? ex.getMessage() : "inputFile is required"
        );
        detail.setTitle("Missing input file");
        detail.setType(URI.create("about:blank"));
        return detail;
    }

    @ExceptionHandler(InvalidInputFileResourceException.class)
    public ProblemDetail onInvalidInputFileResource(InvalidInputFileResourceException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage() != null ? ex.getMessage() : "inputFile resource does not exist or is not readable"
        );
        detail.setTitle("Invalid input file");
        detail.setType(URI.create("about:blank"));
        return detail;
    }

    @ExceptionHandler(ImportJobLaunchException.class)
    public ProblemDetail onImportLaunchFailed(ImportJobLaunchException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "Import job failed to start"
        );
        detail.setTitle("Import job launch failed");
        detail.setType(URI.create("about:blank"));
        return detail;
    }

    @ExceptionHandler(ImportCommandPublishException.class)
    public ProblemDetail onImportCommandPublishFailed(ImportCommandPublishException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage() != null ? ex.getMessage() : "Import command could not be queued"
        );
        detail.setTitle("Import command publish failed");
        detail.setType(URI.create("about:blank"));
        return detail;
    }

    @ExceptionHandler(InvalidCorrelationIdException.class)
    public ProblemDetail onInvalidCorrelationId(InvalidCorrelationIdException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage() != null ? ex.getMessage() : "Invalid correlation id"
        );
        detail.setTitle("Invalid correlation id");
        detail.setType(URI.create("about:blank"));
        return detail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onBadPathVariable(MethodArgumentTypeMismatchException ex) {
        if ("jobExecutionId".equals(ex.getName())) {
            ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "jobExecutionId must be a number"
            );
            detail.setTitle("Invalid path variable");
            detail.setType(URI.create("about:blank"));
            return detail;
        }
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage() != null ? ex.getMessage() : "Invalid request parameter"
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
        detail.setTitle("Internal error");
        detail.setType(URI.create("about:blank"));
        return detail;
    }
}
