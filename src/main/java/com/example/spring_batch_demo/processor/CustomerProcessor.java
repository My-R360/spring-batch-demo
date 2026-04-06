package com.example.spring_batch_demo.processor;

import com.example.spring_batch_demo.model.Customer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class CustomerProcessor implements ItemProcessor<Customer, Customer> {

    private final ValidationStrategy validationStrategy;

    public CustomerProcessor(ValidationStrategy validationStrategy) {
        this.validationStrategy = validationStrategy;
    }

    @Override
    public Customer process(Customer customer) {
        if (!validationStrategy.isValid(customer)) {
            return null; // filtered
        }
        customer.setName(customer.getName().toUpperCase());
        return customer;
    }
}

