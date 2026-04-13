package com.example.spring_batch_demo.infrastructure.batch;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;

import com.example.spring_batch_demo.application.customer.port.CustomerUpsertPort;
import com.example.spring_batch_demo.domain.customer.Customer;

import static org.mockito.Mockito.*;

class CustomerUpsertItemWriterAdapterTest {

    @Test
    void writeDelegatesChunkItemsToUpsertPort() throws Exception {
        CustomerUpsertPort upsertPort = mock(CustomerUpsertPort.class);
        CustomerUpsertItemWriterAdapter writer = new CustomerUpsertItemWriterAdapter(upsertPort);

        List<Customer> customers = List.of(
                new Customer(1L, "A", "a@x.com"),
                new Customer(2L, "B", "b@x.com")
        );
        Chunk<Customer> chunk = new Chunk<>(customers);

        writer.write(chunk);

        verify(upsertPort).upsert(customers);
    }
}
