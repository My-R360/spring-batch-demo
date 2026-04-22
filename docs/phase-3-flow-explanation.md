# Phase 3 flow explanation - RabbitMQ command boundary

Phase 3 adds a RabbitMQ boundary before the Spring Batch launch. The batch job, CSV reader, domain policy, writer, and Phase 2 audit/report behavior remain the same. What changes is how work is accepted and how the client gets the eventual `jobExecutionId`.

In `dev`, messaging is enabled by [application-dev.properties](../src/main/resources/application-dev.properties) (line 4), RabbitMQ connection properties are configured on lines 6-9, and [application-dev.yaml](../src/main/resources/application-dev.yaml) clears the base AMQP auto-configuration exclusion (line 5).

## Before any request: service startup, RabbitMQ wiring, and listeners

Phase 3 startup builds everything from Phases 1 and 2, then adds RabbitMQ infrastructure. The key difference from direct mode is that the listener container is started during application startup and waits for messages before any HTTP request is sent.

Chronological startup flow:

1. The JVM enters `SpringBatchDemoApplication.main` in [SpringBatchDemoApplication.java](../src/main/java/com/example/spring_batch_demo/SpringBatchDemoApplication.java) (line 9), and `@SpringBootApplication` (line 6) starts component scanning and auto-configuration.
2. Spring loads base and `dev` profile properties. Base [application.properties](../src/main/resources/application.properties) disables messaging by default (line 4) and excludes Rabbit auto-configuration (line 5). `dev` overrides messaging to `true` in [application-dev.properties](../src/main/resources/application-dev.properties) (line 4), while [application-dev.yaml](../src/main/resources/application-dev.yaml) clears the AMQP exclusion (line 5).
3. Spring creates the web application context, embedded web server, Spring MVC request mappings, JSON converters, and controller advice infrastructure.
4. Spring registers `BatchJobController` from [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (class line 33), including POST, correlation lookup, status, and report endpoints.
5. Spring registers `BatchJobApiExceptionHandler` from [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) (line 23), including the `503` mapping for Rabbit publish failures.
6. Spring creates the Oracle `DataSource` using [application-dev.properties](../src/main/resources/application-dev.properties) (lines 22-25).
7. Spring SQL initialization runs Oracle [schema.sql](../src/main/resources/schema.sql) because `spring.sql.init.mode=always` is set in [application-dev.properties](../src/main/resources/application-dev.properties) (line 12). The custom Oracle separator is configured on line 16. This creates `CUSTOMER` (schema line 7), `IMPORT_REJECTED_ROW` (schema line 24), and `IMPORT_LAUNCH_CORRELATION` (schema line 46).
8. Spring Batch initializes metadata tables because [application-dev.properties](../src/main/resources/application-dev.properties) sets `spring.batch.jdbc.initialize-schema=always` (line 18) and `spring.batch.jdbc.platform=oracle` (line 19).
9. `spring.batch.job.enabled=false` in [application-dev.properties](../src/main/resources/application-dev.properties) (line 27) prevents `customerJob` from running automatically at startup.
10. Spring registers common job infrastructure: `asyncJobLauncher` from [AsyncJobLauncherConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/AsyncJobLauncherConfig.java) (line 18), domain policy from [DomainPolicyConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/DomainPolicyConfig.java) (line 18), and `NamedParameterJdbcTemplate` from [JdbcConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/JdbcConfig.java) (line 18).
11. Spring registers `customerReader`, `customerJob`, and `customerStep` from [CustomerCsvItemReaderConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/batch/config/CustomerCsvItemReaderConfig.java) (line 30) and [CustomerImportJobConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/batch/config/CustomerImportJobConfig.java) (lines 64 and 79). These are job definitions; no CSV is read at startup.
12. Spring registers audit infrastructure: `JdbcImportAuditPortAdapter` from [JdbcImportAuditPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportAuditPortAdapter.java) (class line 18) and Step-scoped `CustomerImportAuditStepListener` from [CustomerImportAuditListenerConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/batch/config/CustomerImportAuditListenerConfig.java) (line 16).
13. Spring registers correlation persistence through `JdbcImportLaunchCorrelationAdapter` from [JdbcImportLaunchCorrelationAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportLaunchCorrelationAdapter.java) (class line 11).
14. Spring evaluates messaging conditions. Because messaging is enabled, it registers `AmqpCustomerImportCommandPublisher` from [AmqpCustomerImportCommandPublisher.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/messaging/AmqpCustomerImportCommandPublisher.java) (line 15) and `CustomerImportJobLaunchListener` from [CustomerImportJobLaunchListener.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/messaging/CustomerImportJobLaunchListener.java) (line 20). It does not register the direct publisher.
15. Spring enables Rabbit configuration via [CustomerImportRabbitConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/messaging/CustomerImportRabbitConfig.java) (line 31) and binds `CustomerImportMessagingProperties` from [CustomerImportMessagingProperties.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/messaging/CustomerImportMessagingProperties.java) (line 8).
16. Rabbit configuration registers durable exchange/queue/DLQ beans: command exchange (line 34), dead-letter exchange (line 39), DLQ (line 44), DLQ binding (line 49), work queue with DLX arguments (line 60), and command binding (line 68).
17. Rabbit configuration registers message conversion and runtime behavior: Jackson converter (line 79), `RabbitTemplate` customizer (line 84), retry interceptor (line 91), and listener container factory (line 107).
18. Spring AMQP processes `@RabbitListener` on `CustomerImportJobLaunchListener.onCustomerImportCommand` in [CustomerImportJobLaunchListener.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/messaging/CustomerImportJobLaunchListener.java) (line 34). It creates a listener container for `customer.import.queue` and starts consumer threads when the application context is ready.
19. Rabbit declarations are sent to the broker when the Rabbit infrastructure establishes a connection. If RabbitMQ is unavailable in `dev`, startup or later publish/consume behavior can fail depending on when the connection is attempted.

At the end of Phase 3 startup, the HTTP server is listening, the AMQP publisher is selected, Rabbit queue/listener infrastructure is ready, and the listener container is waiting for messages. No batch job has run yet, no command has been published yet, and no `IMPORT_LAUNCH_CORRELATION` row exists until a POST creates a command and the listener launches the job.

## Phase 3 data and contracts

Important types:

- `CustomerImportCommand` in [CustomerImportCommand.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/CustomerImportCommand.java) (line 14) is the queued message body.
- `CustomerImportCommand.of` in [CustomerImportCommand.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/CustomerImportCommand.java) (line 25) creates the command with schema version `1`.
- `CustomerImportEnqueueResponse` in [CustomerImportEnqueueResponse.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/CustomerImportEnqueueResponse.java) (line 12) is the `202 Accepted` response body.
- `CustomerImportCommandPublisher` in [CustomerImportCommandPublisher.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/CustomerImportCommandPublisher.java) (line 11) is the application port used by the controller.
- `ImportLaunchCorrelationPort` in [ImportLaunchCorrelationPort.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/ImportLaunchCorrelationPort.java) (line 8) maps `correlationId` to `jobExecutionId`.

Command payload:

```json
{
  "correlationId": "2f8f4f22-4c87-48e4-9de9-e53c4f4fe19d",
  "inputFile": "classpath:customers.csv",
  "schemaVersion": 1
}
```

## Endpoint 1: POST import, RabbitMQ successful flow

Request:

```http
POST /api/batch/customer/import?inputFile=classpath:customers.csv
```

Chronological call flow:

1. Spring MVC routes to `BatchJobController.importCustomers` in [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (line 52).
2. The controller validates `inputFile` through `CustomerImportInputFile.requireInputFileLocation` and then asks `CustomerImportInputFileValidator` to confirm the resource exists/readable by the app process.
3. If `inputFile` is missing/blank, `MissingInputFileException` is handled by `BatchJobApiExceptionHandler.onMissingInputFile`, returning `400`.
4. If the resource does not exist or is unreadable, `InvalidInputFileResourceException` is handled by `BatchJobApiExceptionHandler.onInvalidInputFileResource`, returning `400` before any RabbitMQ message is published.
5. If valid, the controller creates a UUID `correlationId`.
6. The controller builds a `CustomerImportCommand` by calling `CustomerImportCommand.of` in [CustomerImportCommand.java](../src/main/java/com/example/spring_batch_demo/application/customer/dto/CustomerImportCommand.java).
7. The controller calls `CustomerImportCommandPublisher.publish` in [CustomerImportCommandPublisher.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/CustomerImportCommandPublisher.java).
8. In `dev`, Spring selects [AmqpCustomerImportCommandPublisher.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/messaging/AmqpCustomerImportCommandPublisher.java) because `app.messaging.customer-import.enabled=true`.
9. `AmqpCustomerImportCommandPublisher.publish` asks `CustomerImportInputFileStagingPort` to prepare the file. Bundled `classpath:` inputs pass through unchanged; external local `file:` or plain path inputs are copied to `target/classes/customer-imports/` and become `classpath:customer-imports/<correlationId>-<file>.csv`.
10. `AmqpCustomerImportCommandPublisher.publish` calls `RabbitTemplate.convertAndSend(exchange, routingKey, stagedCommand)`.
11. Exchange and routing key come from `CustomerImportMessagingProperties` in [CustomerImportMessagingProperties.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/messaging/CustomerImportMessagingProperties.java).
12. If RabbitMQ accepts the message, `publish` returns `CustomerImportEnqueueResponse(correlationId, "QUEUED", null)`.
13. The controller returns `202 Accepted` immediately.
14. At this point, no `jobExecutionId` is available to the client yet. The happy flow continues asynchronously through RabbitMQ and the listener.

Successful `dev` response:

```json
{
  "correlationId": "2f8f4f22-4c87-48e4-9de9-e53c4f4fe19d",
  "status": "QUEUED",
  "jobExecutionId": null
}
```

## RabbitMQ topology and configuration flow

Configuration starts at [CustomerImportRabbitConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/messaging/CustomerImportRabbitConfig.java) (class line 31). It is active only when messaging is enabled.

Chronological startup wiring:

1. `MessagingConfigurationBeans` in [MessagingConfigurationBeans.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/messaging/MessagingConfigurationBeans.java) enables configuration properties for `CustomerImportMessagingProperties` (line 7).
2. `CustomerImportMessagingProperties` binds properties with prefix `app.messaging.customer-import` in [CustomerImportMessagingProperties.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/messaging/CustomerImportMessagingProperties.java) (line 8).
3. `CustomerImportRabbitConfig.customerImportCommandsExchange` creates the durable direct exchange in [CustomerImportRabbitConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/messaging/CustomerImportRabbitConfig.java) (line 34).
4. `customerImportDeadLetterExchange` creates the DLX (line 39).
5. `customerImportDeadLetterQueue` creates the DLQ (line 44).
6. `customerImportDeadLetterBinding` binds DLQ to DLX (line 49).
7. `customerImportWorkQueue` creates the main durable work queue and sets `x-dead-letter-exchange` and `x-dead-letter-routing-key` (line 60).
8. `customerImportCommandsBinding` binds the main queue to the command exchange with the routing key (line 68).
9. `customerImportJsonMessageConverter` creates the Jackson JSON converter (line 79).
10. `customerImportJacksonRabbitTemplateCustomizer` installs that converter into `RabbitTemplate` (line 84).
11. `customerImportRetryInterceptor` configures stateless retry and `RejectAndDontRequeueRecoverer` (line 91).
12. `rabbitListenerContainerFactory` sets connection factory, converter, prefetch, retry advice, and `defaultRequeueRejected=false` (line 107).

Default topology:

| Property | Default |
|----------|---------|
| exchange | `customer.import.commands` |
| routing key | `customer.import.command` |
| queue | `customer.import.queue` |
| dead-letter exchange | `customer.import.dlx` |
| dead-letter queue | `customer.import.dlq` |
| dead-letter routing key | `customer.import.dlq` |
| listener prefetch | `1` |
| retry max attempts | `4` |
| retry initial interval | `1000ms` |
| retry multiplier | `2.0` |
| retry max interval | `10000ms` |

## RabbitMQ listener successful flow

After the POST response returns, RabbitMQ delivers the command.

Chronological listener flow:

1. RabbitMQ delivers `CustomerImportCommand` from `customer.import.queue`.
2. Spring AMQP invokes `CustomerImportJobLaunchListener.onCustomerImportCommand` in [CustomerImportJobLaunchListener.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/messaging/CustomerImportJobLaunchListener.java) (line 39).
3. The listener logs `correlationId` and `inputFile`.
4. The listener calls `CustomerImportUseCase.launchImport(command.inputFile())` in [CustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/CustomerImportUseCase.java) (line 25).
5. The implementation is `SpringBatchCustomerImportUseCase.launchImport` in [SpringBatchCustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java) (line 54).
6. The use case validates input, creates `JobParameters`, and calls `jobLauncher.run(customerJob, params)`.
7. The job runs `customerJob` in [CustomerImportJobConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/batch/config/CustomerImportJobConfig.java) (line 64).
8. The job executes `customerStep` in [CustomerImportJobConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/batch/config/CustomerImportJobConfig.java) (line 79).
9. The batch step performs the same CSV read/process/write/audit behavior documented in Phase 1 and Phase 2.
10. `SpringBatchCustomerImportUseCase.launchImport` returns the `jobExecutionId` to the listener.
11. The listener calls `ImportLaunchCorrelationPort.registerLaunchedJob(command.correlationId(), jobExecutionId)` in [ImportLaunchCorrelationPort.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/ImportLaunchCorrelationPort.java) (line 10).
12. The implementation is `JdbcImportLaunchCorrelationAdapter.registerLaunchedJob` in [JdbcImportLaunchCorrelationAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportLaunchCorrelationAdapter.java) (line 31).
13. The adapter inserts into `IMPORT_LAUNCH_CORRELATION` if no row exists for that `correlationId`.
14. The listener returns normally.
15. With `ackMode=AUTO`, Spring AMQP acknowledges the message after successful listener return.

## Endpoint 2: correlation lookup flow

Request:

```http
GET /api/batch/customer/import/by-correlation/{correlationId}/job
```

Chronological call flow:

1. Spring MVC routes to `BatchJobController.getJobExecutionIdByCorrelation` in [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (line 75).
2. The controller validates the path variable with `requireUuidCorrelationId` in [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (line 136).
3. If the value is missing, blank, or not a UUID, `InvalidCorrelationIdException` is thrown.
4. `BatchJobApiExceptionHandler.onInvalidCorrelationId` in [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) (line 60) returns `400`.
5. If valid, the controller calls `ImportLaunchCorrelationPort.findJobExecutionId` in [ImportLaunchCorrelationPort.java](../src/main/java/com/example/spring_batch_demo/application/customer/port/ImportLaunchCorrelationPort.java) (line 12).
6. The implementation is `JdbcImportLaunchCorrelationAdapter.findJobExecutionId` in [JdbcImportLaunchCorrelationAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportLaunchCorrelationAdapter.java) (line 39).
7. The adapter queries `IMPORT_LAUNCH_CORRELATION`.
8. If no row exists yet, the controller returns `404 Not Found`. In Phase 3, this often means "the listener has not launched the job yet".
9. If a row exists, the controller returns `200 OK` with `{ "jobExecutionId": 41 }`.

Example response:

```json
{
  "jobExecutionId": 41
}
```

## Endpoint 3: status flow after correlation

After the client resolves `jobExecutionId`, status is the same endpoint as Phase 1/2:

```http
GET /api/batch/customer/import/{jobExecutionId}/status
```

Chronological call flow:

1. `BatchJobController.getImportStatus` in [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (line 98) receives the request.
2. It calls `SpringBatchCustomerImportUseCase.getImportStatus` in [SpringBatchCustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java) (line 74).
3. The use case reads `BATCH_*` metadata using `JobExplorer`.
4. It sums step counts.
5. It reads a rejected sample through `JdbcImportAuditPortAdapter.loadRows` in [JdbcImportAuditPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportAuditPortAdapter.java) (line 82).
6. It returns `CustomerImportResult`.
7. The controller returns `200`, `404`, or `500` based on execution existence and batch status.

## Endpoint 4: report flow after correlation

After the client resolves `jobExecutionId`, report is the Phase 2 report endpoint:

```http
GET /api/batch/customer/import/{jobExecutionId}/report?limit=50&offset=0
```

Chronological call flow:

1. `BatchJobController.getImportAuditReport` in [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (line 121) receives the request.
2. It calls `SpringBatchCustomerImportUseCase.getImportAuditReport` in [SpringBatchCustomerImportUseCase.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/batch/SpringBatchCustomerImportUseCase.java) (line 109).
3. The use case checks job existence through `JobExplorer`.
4. It clamps `limit` and `offset`.
5. It calls `JdbcImportAuditPortAdapter.countRejected` in [JdbcImportAuditPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportAuditPortAdapter.java) (line 76).
6. It calls `JdbcImportAuditPortAdapter.loadRows` in [JdbcImportAuditPortAdapter.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/persistence/JdbcImportAuditPortAdapter.java) (line 82).
7. It returns `ImportAuditReport`.
8. The controller returns `200`, `404`, or `500` based on execution existence and batch status.

## Failure and retry behavior

| Situation | Where it happens | Final behavior |
|-----------|------------------|----------------|
| Missing/blank `inputFile` | REST validation | `400 ProblemDetail` from `onMissingInputFile` |
| Non-existent/unreadable `inputFile` resource | REST resource validation or local staging | `400 ProblemDetail` from `onInvalidInputFileResource`; no queue message is published |
| RabbitMQ unavailable during POST | `AmqpCustomerImportCommandPublisher.publish` in [AmqpCustomerImportCommandPublisher.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/messaging/AmqpCustomerImportCommandPublisher.java) (line 30) catches `AmqpException` | `503 ProblemDetail` from `onImportCommandPublishFailed` in [BatchJobApiExceptionHandler.java](../src/main/java/com/example/spring_batch_demo/presentation/api/exceptions/BatchJobApiExceptionHandler.java) (line 49) |
| Listener launch fails | `CustomerImportJobLaunchListener.onCustomerImportCommand` in [CustomerImportJobLaunchListener.java](../src/main/java/com/example/spring_batch_demo/infrastructure/adapter/messaging/CustomerImportJobLaunchListener.java) (line 39) | listener exception triggers retry; after max attempts, message is rejected without requeue and dead-lettered |
| Correlation not registered yet | `JdbcImportLaunchCorrelationAdapter.findJobExecutionId` returns empty | `404`; client can retry |
| Invalid correlation id | `BatchJobController.requireUuidCorrelationId` in [BatchJobController.java](../src/main/java/com/example/spring_batch_demo/presentation/api/BatchJobController.java) (line 136) | `400 ProblemDetail` |
| Batch job later fails | status/report endpoint sees `FAILED` | `500` with result/report body |

Retry and DLQ are configured in `CustomerImportRabbitConfig.customerImportRetryInterceptor` in [CustomerImportRabbitConfig.java](../src/main/java/com/example/spring_batch_demo/infrastructure/config/messaging/CustomerImportRabbitConfig.java) (line 91) and `rabbitListenerContainerFactory` (line 107).

## Phase 3 database objects

Phase 3 adds:

- `IMPORT_LAUNCH_CORRELATION`, created in [schema.sql](../src/main/resources/schema.sql) (line 46) and [schema-h2-import-audit-it.sql](../src/main/resources/schema-h2-import-audit-it.sql) (line 18).

Columns:

- `CORRELATION_ID`: UUID returned by POST
- `JOB_EXECUTION_ID`: Spring Batch execution id created by the listener or direct publisher
- `CREATED_AT`: insertion timestamp

Phase 3 still uses:

- `CUSTOMER`, for accepted rows
- `BATCH_*`, for Spring Batch execution state
- `IMPORT_REJECTED_ROW`, for Phase 2 audit rows

## Phase 3 happy flow summary

```text
POST import
  -> BatchJobController validates input
  -> CustomerImportCommand is created
  -> AmqpCustomerImportCommandPublisher stages external local files when needed
  -> AmqpCustomerImportCommandPublisher publishes staged command to RabbitMQ
  -> API returns 202 QUEUED with correlationId and null jobExecutionId
  -> RabbitMQ delivers command to CustomerImportJobLaunchListener
  -> listener calls SpringBatchCustomerImportUseCase.launchImport
  -> customerJob/customerStep execute normal batch import and audit flow
  -> listener stores correlationId -> jobExecutionId
  -> client polls correlation endpoint until jobExecutionId is available
  -> client polls status/report endpoints using jobExecutionId
```

The successful POST flow goes ahead to RabbitMQ first, then to the listener, then to the same Spring Batch job used by Phases 1 and 2.
