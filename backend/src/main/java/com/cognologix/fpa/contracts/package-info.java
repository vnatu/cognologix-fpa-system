/**
 * Contract Management (Module 6, ADR-067).
 * Public API is this root package ({@code ContractService} and its request/response types).
 * Entities and repositories stay in sub-packages.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"general", "customer"}
)
package com.cognologix.fpa.contracts;
