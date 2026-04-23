package com.example.spring_batch_demo.application.customer.port;

/**
 * Prepares a customer import input file for durable hand-off to the batch launcher.
 *
 * <p>The returned location must be readable by the process that eventually launches the
 * Spring Batch job. Local development can use a same-machine classpath copy; distributed
 * deployments should use shared storage or another consumer-visible resource location.</p>
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
