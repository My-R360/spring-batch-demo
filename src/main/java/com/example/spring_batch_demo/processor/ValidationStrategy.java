package com.example.spring_batch_demo.processor;

import com.example.spring_batch_demo.model.Customer;

public interface ValidationStrategy {
    boolean isValid(Customer customer);
}
