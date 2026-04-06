package com.example.spring_batch_demo.factory;

import com.example.spring_batch_demo.model.Customer;
import com.example.spring_batch_demo.processor.CustomerProcessor;
import com.example.spring_batch_demo.processor.EmailValidationStrategy;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class ProcessorFactory {

    public ItemProcessor<Customer, Customer> getProcessor(String type) {
        if ("default".equals(type)) {
            return new CustomerProcessor(new EmailValidationStrategy());
        }
        throw new IllegalArgumentException("Unknown processor type");
    }
}
