package com.example.spring_batch_demo.service;

import com.example.spring_batch_demo.model.Customer;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    public void saveAll(List<? extends Customer> customers) {
        customers.forEach(c -> {
            System.out.println("Saving customer: " + c);
        });
    }
}
