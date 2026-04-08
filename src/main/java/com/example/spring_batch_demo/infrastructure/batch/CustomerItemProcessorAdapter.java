package com.example.spring_batch_demo.infrastructure.batch;

import com.example.spring_batch_demo.domain.customer.Customer;
import com.example.spring_batch_demo.domain.customer.CustomerImportPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerItemProcessorAdapter implements ItemProcessor<Customer, Customer> {

    private final CustomerImportPolicy importPolicy;

    /**
     * Spring Batch callback for transforming or filtering a single item.
     *
     * <p>Returning {@code null} signals to Spring Batch that the item should be filtered out and
     * not written.</p>
     */
    @Override
    public Customer process(Customer customer) {
        return importPolicy.apply(customer);
    }
}

