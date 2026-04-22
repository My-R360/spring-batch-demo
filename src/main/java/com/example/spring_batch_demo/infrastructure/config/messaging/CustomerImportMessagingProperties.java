package com.example.spring_batch_demo.infrastructure.config.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RabbitMQ topology and tuning for customer import commands.
 */
@ConfigurationProperties(prefix = "app.messaging.customer-import")
public class CustomerImportMessagingProperties {

    /**
     * When false, commands are launched in-process and Rabbit auto-configuration is excluded.
     */
    private boolean enabled = false;

    private String exchange = "customer.import.commands";
    private String routingKey = "customer.import.command";
    private String queue = "customer.import.queue";
    private String deadLetterExchange = "customer.import.dlx";
    private String deadLetterQueue = "customer.import.dlq";
    private String deadLetterRoutingKey = "customer.import.dlq";

    private int listenerPrefetch = 1;
    private int retryMaxAttempts = 4;
    private long retryInitialIntervalMs = 1_000L;
    private double retryMultiplier = 2.0D;
    private long retryMaxIntervalMs = 10_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getDeadLetterExchange() {
        return deadLetterExchange;
    }

    public void setDeadLetterExchange(String deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public void setDeadLetterQueue(String deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }

    public String getDeadLetterRoutingKey() {
        return deadLetterRoutingKey;
    }

    public void setDeadLetterRoutingKey(String deadLetterRoutingKey) {
        this.deadLetterRoutingKey = deadLetterRoutingKey;
    }

    public int getListenerPrefetch() {
        return listenerPrefetch;
    }

    public void setListenerPrefetch(int listenerPrefetch) {
        this.listenerPrefetch = listenerPrefetch;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public long getRetryInitialIntervalMs() {
        return retryInitialIntervalMs;
    }

    public void setRetryInitialIntervalMs(long retryInitialIntervalMs) {
        this.retryInitialIntervalMs = retryInitialIntervalMs;
    }

    public double getRetryMultiplier() {
        return retryMultiplier;
    }

    public void setRetryMultiplier(double retryMultiplier) {
        this.retryMultiplier = retryMultiplier;
    }

    public long getRetryMaxIntervalMs() {
        return retryMaxIntervalMs;
    }

    public void setRetryMaxIntervalMs(long retryMaxIntervalMs) {
        this.retryMaxIntervalMs = retryMaxIntervalMs;
    }
}
