package com.example.spring_batch_demo.domain.customer;

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
        if (input.getEmail() == null || !input.getEmail().contains("@")) {
            return null;
        }
        if (input.getName() != null) {
            input.setName(input.getName().toUpperCase());
        }
        return input;
    }
}

