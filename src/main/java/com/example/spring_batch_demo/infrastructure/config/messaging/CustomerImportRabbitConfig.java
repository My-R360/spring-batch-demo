package com.example.spring_batch_demo.infrastructure.config.messaging;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * Declares exchange, main queue (with DLX), dead-letter queue, JSON conversion, and listener retry.
 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "app.messaging.customer-import.enabled", havingValue = "true")
public class CustomerImportRabbitConfig {

    @Bean
    public DirectExchange customerImportCommandsExchange(CustomerImportMessagingProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public DirectExchange customerImportDeadLetterExchange(CustomerImportMessagingProperties properties) {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue customerImportDeadLetterQueue(CustomerImportMessagingProperties properties) {
        return new Queue(properties.getDeadLetterQueue(), true);
    }

    @Bean
    public Binding customerImportDeadLetterBinding(
            Queue customerImportDeadLetterQueue,
            DirectExchange customerImportDeadLetterExchange,
            CustomerImportMessagingProperties properties
    ) {
        return BindingBuilder.bind(customerImportDeadLetterQueue)
                .to(customerImportDeadLetterExchange)
                .with(properties.getDeadLetterRoutingKey());
    }

    @Bean
    public Queue customerImportWorkQueue(CustomerImportMessagingProperties properties) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", properties.getDeadLetterExchange());
        arguments.put("x-dead-letter-routing-key", properties.getDeadLetterRoutingKey());
        return new Queue(properties.getQueue(), true, false, false, arguments);
    }

    @Bean
    public Binding customerImportCommandsBinding(
            Queue customerImportWorkQueue,
            DirectExchange customerImportCommandsExchange,
            CustomerImportMessagingProperties properties
    ) {
        return BindingBuilder.bind(customerImportWorkQueue)
                .to(customerImportCommandsExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public MessageConverter customerImportJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplateCustomizer customerImportJacksonRabbitTemplateCustomizer(
            MessageConverter customerImportJsonMessageConverter
    ) {
        return rabbitTemplate -> rabbitTemplate.setMessageConverter(customerImportJsonMessageConverter);
    }

    @Bean
    public RetryOperationsInterceptor customerImportRetryInterceptor(
            CustomerImportMessagingProperties properties
    ) {
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval((int) Math.min(Integer.MAX_VALUE, properties.getRetryInitialIntervalMs()));
        backOffPolicy.setMultiplier(properties.getRetryMultiplier());
        backOffPolicy.setMaxInterval((int) Math.min(Integer.MAX_VALUE, properties.getRetryMaxIntervalMs()));
        MessageRecoverer recoverer = new RejectAndDontRequeueRecoverer();
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(properties.getRetryMaxAttempts())
                .backOffPolicy(backOffPolicy)
                .recoverer(recoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter customerImportJsonMessageConverter,
            RetryOperationsInterceptor customerImportRetryInterceptor,
            CustomerImportMessagingProperties properties
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(customerImportJsonMessageConverter);
        factory.setPrefetchCount(properties.getListenerPrefetch());
        factory.setAdviceChain(customerImportRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
