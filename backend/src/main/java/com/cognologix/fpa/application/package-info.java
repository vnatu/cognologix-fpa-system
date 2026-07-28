/**
 * Application composition layer — thin REST endpoints that orchestrate multiple
 * bounded contexts. Declared OPEN and explicitly allowed to depend on revenue + budgeting
 * so those modules stay cycle-free with each other (ADR-043).
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        allowedDependencies = {"revenue", "budgeting", "customer", "general", "people"}
)
package com.cognologix.fpa.application;
