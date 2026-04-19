package com.example.spring_batch_demo.domain.customer.policy;

import com.example.spring_batch_demo.domain.customer.Customer;

/**
 * Default import policy for the demo.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Filter out rows with missing/invalid email (must contain '@').</li>
 *   <li>Uppercase the customer's name.</li>
 * </ul>
 */
public class EmailAndNameCustomerImportPolicy implements CustomerImportPolicy {

    @Override
    public Customer apply(Customer input) {
        if (input == null) {
            return null;
        }
        if (input.email() == null || !input.email().contains("@")) {
            return null;
        }
        String uppercasedName = input.name() != null ? input.name().toUpperCase() : null;
        return new Customer(input.id(), uppercasedName, input.email());
    }
}
