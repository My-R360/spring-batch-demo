/**
 * Cross-cutting validation that does not belong to a single aggregate package.
 *
 * <p>Aggregate-specific rules stay next to that aggregate (e.g. {@code domain.customer.policy}).
 * Use this package for shared, framework-free validators when they appear.</p>
 */
package com.example.spring_batch_demo.domain.validation;
