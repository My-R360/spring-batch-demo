package com.example.spring_batch_demo.application.customer.port;

/**
 * Prepares a customer import input file for durable hand-off to the batch launcher.
 */
public interface CustomerImportInputFileStagingPort {

    /**
     * Returns the location that should be passed through the import command and job parameter.
     *
     * @param inputFile non-blank input file location supplied by the caller
     * @param importId stable import identifier, normally the HTTP/Rabbit correlation id
     */
    String stageForImport(String inputFile, String importId);
}
