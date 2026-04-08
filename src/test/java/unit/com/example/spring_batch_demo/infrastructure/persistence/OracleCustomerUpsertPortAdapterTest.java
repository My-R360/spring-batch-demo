package com.example.spring_batch_demo.infrastructure.persistence;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.example.spring_batch_demo.domain.customer.Customer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OracleCustomerUpsertPortAdapterTest {

    @Test
    void upsertSkipsNullOrEmptyInput() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        OracleCustomerUpsertPortAdapter adapter = new OracleCustomerUpsertPortAdapter(jdbc);

        adapter.upsert(null);
        adapter.upsert(List.of());

        verify(jdbc, never()).batchUpdate(anyString(), any(Map[].class));
    }

    @Test
    void upsertPerformsBatchUpdateForCustomers() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        OracleCustomerUpsertPortAdapter adapter = new OracleCustomerUpsertPortAdapter(jdbc);

        adapter.upsert(List.of(
                new Customer(1L, "A", "a@x.com"),
                new Customer(2L, "B", "b@x.com")
        ));

        verify(jdbc).batchUpdate(contains("MERGE INTO CUSTOMER"), any(Map[].class));
    }
}
