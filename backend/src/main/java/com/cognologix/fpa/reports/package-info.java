/**
 * Reports module — Standard Excel report generation.
 * Public API is this root package ({@code ReportService}). Calls other modules'
 * public services in-process; never touches foreign repositories.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"budgeting", "people"}
)
package com.cognologix.fpa.reports;
