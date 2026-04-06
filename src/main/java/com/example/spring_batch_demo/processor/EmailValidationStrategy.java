package com.example.spring_batch_demo.processor;

import com.example.spring_batch_demo.model.Customer;
import org.springframework.stereotype.Component;

@Component
public class EmailValidationStrategy implements ValidationStrategy {

    @Override
    public boolean isValid(Customer customer) {
        return customer.getEmail().contains("@");
    }
}