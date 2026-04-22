package com.example.spring_batch_demo.infrastructure.config.messaging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CustomerImportMessagingProperties.class)
public class MessagingConfigurationBeans {
}
