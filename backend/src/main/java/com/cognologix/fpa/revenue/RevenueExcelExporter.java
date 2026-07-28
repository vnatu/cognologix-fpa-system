package com.cognologix.fpa.revenue;

import com.cognologix.fpa.customer.CustomerService;
import com.cognologix.fpa.revenue.dto.RevenueDtos.InvoiceListItem;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Generates Revenue Excel exports for invoices and credit notes.
 */
@Component
@RequiredArgsConstructor
public class RevenueExcelExporter {

    static final String COL_INVOICE_NUMBER = "Invoice Number";
    static final String COL_CREDIT_NOTE_NUMBER = "Credit Note Number";
    static final String COL_CUSTOMER_CODE = "Customer Code";
    static final String COL_CUSTOMER_NAME = "Customer Name";
    static final String COL_PERIOD_MONTH = "Period Month";
    static final String COL_PERIOD_YEAR = "Period Year";
    static final String COL_INVOICE_DATE = "Invoice Date";
    static final String COL_CREDIT_NOTE_DATE = "Credit Note Date";
    static final String COL_STATUS = "Status";
    static final String COL_AMOUNT = "Amount";
    static final String COL_CURRENCY = "Currency";
    static final String COL_BALANCE = "Balance";
    static final String COL_DUE_DATE = "Due Date";
    static final String COL_INR_EQUIVALENT = "INR Equivalent";
    static final String COL_PROJECT_CODE = "Project Code";

    private final CustomerService customerService;

    public byte[] exportInvoices(List<InvoiceListItem> items) {
        String[] headers = {
                COL_INVOICE_NUMBER,
                COL_CUSTOMER_CODE,
                COL_CUSTOMER_NAME,
                COL_PERIOD_MONTH,
                COL_PERIOD_YEAR,
                COL_INVOICE_DATE,
                COL_STATUS,
                COL_AMOUNT,
                COL_CURRENCY,
                COL_BALANCE,
                COL_DUE_DATE,
                COL_INR_EQUIVALENT,
                COL_PROJECT_CODE
        };
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Invoices");
            writeHeaderRow(sheet, headers);

            int rowIdx = 1;
            for (InvoiceListItem item : items) {
                CustomerRef customer = resolveCustomer(item.customerId());
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(item.documentNumber());
                row.createCell(col++).setCellValue(customer.code());
                setOptionalString(row, col++, customer.name());
                row.createCell(col++).setCellValue(item.periodMonth());
                row.createCell(col++).setCellValue(item.periodYear());
                setDateCell(row, col++, item.documentDate());
                setOptionalString(row, col++, item.status());
                setNumericCell(row, col++, item.amount());
                setOptionalString(row, col++, item.currency() != null ? item.currency().name() : null);
                setNumericCell(row, col++, item.balance());
                setDateCell(row, col++, item.dueDate());
                setNumericCell(row, col++, item.amountInr());
                setOptionalString(row, col, item.projectCode());
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RevenueBadRequestException("Failed to generate invoice export: " + e.getMessage());
        }
    }

    public byte[] exportCreditNotes(List<InvoiceListItem> items) {
        String[] headers = {
                COL_CREDIT_NOTE_NUMBER,
                COL_CUSTOMER_CODE,
                COL_CUSTOMER_NAME,
                COL_PERIOD_MONTH,
                COL_PERIOD_YEAR,
                COL_CREDIT_NOTE_DATE,
                COL_STATUS,
                COL_AMOUNT,
                COL_CURRENCY,
                COL_INR_EQUIVALENT
        };
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Credit Notes");
            writeHeaderRow(sheet, headers);

            int rowIdx = 1;
            for (InvoiceListItem item : items) {
                CustomerRef customer = resolveCustomer(item.customerId());
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(item.documentNumber());
                row.createCell(col++).setCellValue(customer.code());
                setOptionalString(row, col++, customer.name());
                row.createCell(col++).setCellValue(item.periodMonth());
                row.createCell(col++).setCellValue(item.periodYear());
                setDateCell(row, col++, item.documentDate());
                setOptionalString(row, col++, item.status());
                setNumericCell(row, col++, item.amount());
                setOptionalString(row, col++, item.currency() != null ? item.currency().name() : null);
                setNumericCell(row, col, item.amountInr());
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RevenueBadRequestException("Failed to generate credit note export: " + e.getMessage());
        }
    }

    private CustomerRef resolveCustomer(String customerId) {
        Optional<CustomerService.BuCustomerRef> ref = customerService.resolveBuCustomer(customerId);
        if (ref.isPresent()) {
            return new CustomerRef(ref.get().customerCode(), ref.get().customerName());
        }
        return new CustomerRef(customerId, null);
    }

    private static void writeHeaderRow(Sheet sheet, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private static void setOptionalString(Row row, int col, String value) {
        if (value != null && !value.isBlank()) {
            row.createCell(col).setCellValue(value);
        } else {
            row.createCell(col).setBlank();
        }
    }

    private static void setNumericCell(Row row, int col, BigDecimal value) {
        if (value == null) {
            row.createCell(col).setBlank();
        } else {
            row.createCell(col).setCellValue(value.doubleValue());
        }
    }

    private static void setDateCell(Row row, int col, LocalDate value) {
        if (value == null) {
            row.createCell(col).setBlank();
        } else {
            row.createCell(col).setCellValue(value.toString());
        }
    }

    private record CustomerRef(String code, String name) {}
}
