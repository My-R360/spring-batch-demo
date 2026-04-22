package com.example.spring_batch_demo.application.customer.port;

import com.example.spring_batch_demo.application.customer.dto.CustomerImportCommand;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportEnqueueResponse;
import com.example.spring_batch_demo.application.customer.exceptions.ImportCommandPublishException;
import com.example.spring_batch_demo.application.customer.exceptions.ImportJobLaunchException;

/**
 * Publishes a customer import command (to RabbitMQ or in-process), returning how the caller should interpret HTTP.
 */
public interface CustomerImportCommandPublisher {

    /**
     * Validates and dispatches the command.
     *
     * @return response body fields for 202 Accepted
     * @throws ImportJobLaunchException        when messaging is disabled and the batch job fails to start
     * @throws ImportCommandPublishException when messaging is enabled and the broker rejects or is unreachable
     */
    CustomerImportEnqueueResponse publish(CustomerImportCommand command)
            throws ImportJobLaunchException, ImportCommandPublishException;
}
