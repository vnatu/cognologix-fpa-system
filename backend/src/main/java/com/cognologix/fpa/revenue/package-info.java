/**
 * Revenue module is OPEN so application-root composition and Budgeting can use
 * summary/dashboard DTOs without a separate named-interface package (ADR-043).
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package com.cognologix.fpa.revenue;
