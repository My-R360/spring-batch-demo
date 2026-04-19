package com.example.spring_batch_demo.infrastructure.adapter.batch;

import com.example.spring_batch_demo.application.customer.port.CustomerUpsertPort;
import com.example.spring_batch_demo.domain.customer.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerUpsertItemWriterAdapter implements ItemWriter<Customer> {

    private final CustomerUpsertPort upsertPort;

    /**
     * Spring Batch callback for writing a chunk of items.
     *
     * <p>Spring Batch controls the chunk loop; it calls this method once per chunk. We adapt that
     * callback into the application port {@link CustomerUpsertPort} so persistence stays behind an
     * interface.</p>
     */
    @Override
    public void write(Chunk<? extends Customer> chunk) {
        upsertPort.upsert(chunk.getItems().stream().map(c -> (Customer) c).toList());
    }
}
