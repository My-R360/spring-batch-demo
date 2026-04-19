package com.example.spring_batch_demo.infrastructure.adapter.batch;

import org.junit.jupiter.api.Test;

import com.example.spring_batch_demo.domain.customer.Customer;
import com.example.spring_batch_demo.domain.customer.policy.CustomerImportPolicy;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerItemProcessorAdapterTest {

    @Test
    void processDelegatesToImportPolicy() throws Exception {
        CustomerImportPolicy policy = mock(CustomerImportPolicy.class);
        Customer input = new Customer(1L, "Alice", "a@x.com");
        Customer output = new Customer(1L, "ALICE", "a@x.com");
        when(policy.apply(input)).thenReturn(output);

        CustomerItemProcessorAdapter adapter = new CustomerItemProcessorAdapter(policy);
        Customer result = adapter.process(input);

        assertSame(output, result);
        verify(policy).apply(input);
    }
}
