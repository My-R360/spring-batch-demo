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
                .sql("""
                        MERGE INTO CUSTOMER c
                        USING (SELECT :id AS id, :name AS name, :email AS email FROM dual) s
                        ON (c.id = s.id)
                        WHEN MATCHED THEN
                          UPDATE SET c.name = s.name, c.email = s.email
                        WHEN NOT MATCHED THEN
                          INSERT (id, name, email) VALUES (s.id, s.name, s.email)
                        """)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();

        delegate.afterPropertiesSet();
        return delegate;
    }
}

