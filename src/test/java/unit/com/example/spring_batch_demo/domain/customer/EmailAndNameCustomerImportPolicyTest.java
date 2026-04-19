package com.example.spring_batch_demo.domain.customer;

import com.example.spring_batch_demo.domain.customer.policy.EmailAndNameCustomerImportPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailAndNameCustomerImportPolicyTest {

    private final EmailAndNameCustomerImportPolicy policy = new EmailAndNameCustomerImportPolicy();

    @Test
    void applyReturnsNullForNullInput() {
        assertNull(policy.apply(null));
    }

    @Test
    void applyReturnsNullForInvalidEmail() {
        Customer input = new Customer(1L, "Alice", "aliceexample.com");
        assertNull(policy.apply(input));
    }

    @Test
    void applyReturnsNullForNullEmail() {
        Customer input = new Customer(1L, "Alice", null);
        assertNull(policy.apply(input));
    }

    @Test
    void applyUppercasesNameForValidEmail() {
        Customer input = new Customer(2L, "Bob", "bob@example.com");
        Customer out = policy.apply(input);

        assertNotNull(out);
        assertEquals("BOB", out.name());
        assertEquals("bob@example.com", out.email());
    }

    @Test
    void applyHandlesNullName() {
        Customer input = new Customer(3L, null, "x@y.com");
        Customer out = policy.apply(input);

        assertNotNull(out);
        assertNull(out.name());
    }
}
