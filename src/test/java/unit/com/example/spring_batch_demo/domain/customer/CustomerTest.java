package com.example.spring_batch_demo.domain.customer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomerTest {

    @Test
    void lombokGeneratedMethodsWork() {
        Customer c = new Customer();
        c.setId(10L);
        c.setName("Neo");
        c.setEmail("neo@matrix.com");

        assertEquals(10L, c.getId());
        assertEquals("Neo", c.getName());
        assertEquals("neo@matrix.com", c.getEmail());
        assertTrue(c.toString().contains("Neo"));
    }

    @Test
    void allArgsConstructorAndEqualityWork() {
        Customer c1 = new Customer(1L, "A", "a@x.com");
        Customer c2 = new Customer(1L, "A", "a@x.com");

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }
}
