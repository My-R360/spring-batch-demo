package com.example.spring_batch_demo.config;

import com.example.spring_batch_demo.model.Customer;
import javax.sql.DataSource;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WriterConfig {

    @Bean
    public ItemWriter<Customer> customerWriter(DataSource dataSource) {
        JdbcBatchItemWriter<Customer> delegate = new JdbcBatchItemWriterBuilder<Customer>()
                .dataSource(dataSource)
                .sql("INSERT INTO CUSTOMER (ID, NAME, EMAIL) VALUES (:id, :name, :email)")
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();

        delegate.afterPropertiesSet();
        return delegate;
    }
}

