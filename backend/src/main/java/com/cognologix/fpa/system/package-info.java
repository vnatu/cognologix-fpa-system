/**
 * Full system backup/restore orchestration module (ADR-044 Tier 2).
 * Depends on all bounded contexts; no other module depends on system.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        allowedDependencies = {"general", "customer", "people", "budgeting", "revenue"}
)
package com.cognologix.fpa.system;
