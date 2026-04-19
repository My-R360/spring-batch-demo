package com.example.spring_batch_demo.domain.customer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerTest {

    @Test
    void recordAccessorsWork() {
        Customer c = new Customer(10L, "Neo", "neo@matrix.com");

        assertEquals(10L, c.id());
        assertEquals("Neo", c.name());
        assertEquals("neo@matrix.com", c.email());
        assertTrue(c.toString().contains("Neo"));
    }

    @Test
    void recordEqualityAndHashCode() {
        Customer c1 = new Customer(1L, "A", "a@x.com");
        Customer c2 = new Customer(1L, "A", "a@x.com");

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void recordInequalityForDifferentValues() {
        Customer c1 = new Customer(1L, "A", "a@x.com");
        Customer c2 = new Customer(2L, "B", "b@x.com");

        assertNotEquals(c1, c2);
    }
}
