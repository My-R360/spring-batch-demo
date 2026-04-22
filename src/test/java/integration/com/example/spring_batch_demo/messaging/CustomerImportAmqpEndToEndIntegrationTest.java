package com.example.spring_batch_demo.messaging;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import com.example.spring_batch_demo.SpringBatchDemoApplication;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportEnqueueResponse;
import com.example.spring_batch_demo.application.customer.dto.CustomerImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = SpringBatchDemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("amqp-it")
@Testcontainers(disabledWithoutDocker = true)
class CustomerImportAmqpEndToEndIntegrationTest {

    @Container
    static final RabbitMQContainer RABBIT_MQ_CONTAINER = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void registerRabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT_MQ_CONTAINER::getHost);
        registry.add("spring.rabbitmq.port", RABBIT_MQ_CONTAINER::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT_MQ_CONTAINER::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT_MQ_CONTAINER::getAdminPassword);
    }

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Test
    void postQueuesMessageThenResolvesJobExecutionIdAndPollsStatus() throws InterruptedException {
        URI importUri = UriComponentsBuilder.fromPath("/api/batch/customer/import")
                .queryParam("inputFile", "classpath:customers-import-audit-sample.csv")
                .build()
                .toUri();

        ResponseEntity<CustomerImportEnqueueResponse> postResponse =
                testRestTemplate.postForEntity(importUri, null, CustomerImportEnqueueResponse.class);

        assertEquals(HttpStatus.ACCEPTED, postResponse.getStatusCode());
        CustomerImportEnqueueResponse accepted = Objects.requireNonNull(postResponse.getBody());
        assertEquals("QUEUED", accepted.status());
        assertNotNull(accepted.correlationId());
        assertEquals(null, accepted.jobExecutionId());

        Long jobExecutionId = awaitJobExecutionId(accepted.correlationId(), Duration.ofSeconds(30));
        assertNotNull(jobExecutionId);

        CustomerImportResult terminal = awaitTerminalStatus(jobExecutionId, Duration.ofSeconds(60));
        assertEquals("COMPLETED", terminal.status());
        assertTrue(terminal.filterCount() >= 1);
    }

    private Long awaitJobExecutionId(String correlationId, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        URI uri = UriComponentsBuilder.fromPath("/api/batch/customer/import/by-correlation/{cid}/job")
                .buildAndExpand(correlationId)
                .toUri();
        while (System.nanoTime() < deadline) {
            ResponseEntity<Map> response = testRestTemplate.getForEntity(uri, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object raw = response.getBody().get("jobExecutionId");
                if (raw instanceof Number number) {
                    return number.longValue();
                }
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Timed out waiting for jobExecutionId for correlationId=" + correlationId);
    }

    private CustomerImportResult awaitTerminalStatus(Long jobExecutionId, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        URI uri = UriComponentsBuilder.fromPath("/api/batch/customer/import/{id}/status")
                .buildAndExpand(jobExecutionId)
                .toUri();
        while (System.nanoTime() < deadline) {
            ResponseEntity<CustomerImportResult> response =
                    testRestTemplate.getForEntity(uri, CustomerImportResult.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String status = response.getBody().status();
                if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                    return response.getBody();
                }
            }
            Thread.sleep(150L);
        }
        throw new AssertionError("Timed out waiting for terminal status for jobExecutionId=" + jobExecutionId);
    }
}
