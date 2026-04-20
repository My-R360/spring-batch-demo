package com.example.spring_batch_demo.infrastructure.adapter.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.spring_batch_demo.application.customer.port.CustomerUpsertPort;
import com.example.spring_batch_demo.domain.customer.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!audit-it")
@Slf4j
@RequiredArgsConstructor
public class OracleCustomerUpsertPortAdapter implements CustomerUpsertPort {

    private static final String UPSERT_SQL = """
            MERGE INTO CUSTOMER c
            USING (SELECT :id AS id, :name AS name, :email AS email FROM dual) s
            ON (c.id = s.id)
            WHEN MATCHED THEN
              UPDATE SET c.name = s.name, c.email = s.email
            WHEN NOT MATCHED THEN
              INSERT (id, name, email) VALUES (s.id, s.name, s.email)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void upsert(List<Customer> customers) {
        if (customers == null || customers.isEmpty()) {
            return;
        }

        log.debug("Upserting {} customer(s) into Oracle.", customers.size());

        @SuppressWarnings("unchecked")
        Map<String, ?>[] batch = customers.stream()
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.id());
                    m.put("name", c.name());
                    m.put("email", c.email());
                    return (Map<String, ?>) m;
                })
                .toArray(Map[]::new);

        jdbc.batchUpdate(UPSERT_SQL, batch);
    }
}
