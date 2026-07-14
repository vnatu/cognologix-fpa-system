package com.cognologix.fpa.customer;

import com.cognologix.fpa.customer.domain.*;
import com.cognologix.fpa.customer.repository.CommercialTermsRepository;
import com.cognologix.fpa.customer.repository.CustomerProjectCodeRepository;
import com.cognologix.fpa.customer.repository.CustomerRepository;
import com.cognologix.fpa.customer.repository.RateCardRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates Customer Management Excel exports matching import column layouts (ADR-027/ADR-028).
 */
@Component
@RequiredArgsConstructor
public class CustomerExcelExporter {

    private final CustomerRepository customerRepository;
    private final CommercialTermsRepository commercialTermsRepository;
    private final RateCardRepository rateCardRepository;
    private final CustomerProjectCodeRepository projectCodeRepository;

    public byte[] exportCustomers() {
        List<Customer> customers = customerRepository.findAll().stream()
                .sorted(Comparator.comparing(Customer::getCustomerCode))
                .toList();
        Map<UUID, Integer> dsoByCustomerId = commercialTermsRepository.findAll().stream()
                .collect(Collectors.toMap(t -> t.getCustomer().getId(), CommercialTerms::getDsoDays));

        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Customers");
            String[] headers = {
                    CustomerImportParser.COL_CUSTOMER_CODE,
                    CustomerImportParser.COL_CUSTOMER_NAME,
                    CustomerImportParser.COL_ZOHO_BOOKS_REF,
                    CustomerImportParser.COL_LIFECYCLE_STATUS,
                    CustomerImportParser.COL_DSO_DAYS,
                    CustomerImportParser.COL_RELATIONSHIP_OWNER
            };
            writeHeaderRow(sheet, headers);

            int rowIdx = 1;
            for (Customer customer : customers) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(customer.getCustomerCode());
                row.createCell(col++).setCellValue(customer.getCustomerName());
                setOptionalString(row, col++, customer.getZohoBooksCustomerRef());
                row.createCell(col++).setCellValue(customer.getLifecycleStatus().name());
                int dsoDays = dsoByCustomerId.getOrDefault(customer.getId(), 0);
                row.createCell(col++).setCellValue(dsoDays);
                setOptionalString(row, col, customer.getRelationshipOwnerEmployeeId());
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new CustomerBadRequestException("Failed to generate customer export: " + e.getMessage());
        }
    }

    public byte[] exportRateCards() {
        List<RateCard> cards = rateCardRepository.findAllForExport();
        cards.forEach(card -> {
            Hibernate.initialize(card.getCustomer());
            Hibernate.initialize(card.getLines());
        });
        cards.sort(Comparator
                .comparing((RateCard rc) -> rc.getCustomer().getCustomerCode())
                .thenComparing(RateCard::getEffectiveFrom));

        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Rate Cards");
            String[] headers = {
                    RateCardImportParser.COL_CUSTOMER_CODE,
                    RateCardImportParser.COL_RATE_CARD_NAME,
                    RateCardImportParser.COL_RATE_CARD_TYPE,
                    RateCardImportParser.COL_CURRENCY,
                    RateCardImportParser.COL_EFFECTIVE_FROM,
                    RateCardImportParser.COL_JOB_LEVEL,
                    RateCardImportParser.COL_RATE_AMOUNT
            };
            writeHeaderRow(sheet, headers);

            int rowIdx = 1;
            for (RateCard card : cards) {
                List<RateCardLine> lines = card.getLines().stream()
                        .sorted(Comparator.comparing(
                                l -> l.getJobLevel() == null ? "" : l.getJobLevel()))
                        .toList();
                for (RateCardLine line : lines) {
                    Row row = sheet.createRow(rowIdx++);
                    int col = 0;
                    row.createCell(col++).setCellValue(card.getCustomer().getCustomerCode());
                    row.createCell(col++).setCellValue(card.getName());
                    row.createCell(col++).setCellValue(card.getRateCardType().name());
                    row.createCell(col++).setCellValue(card.getCurrency().name());
                    row.createCell(col++).setCellValue(card.getEffectiveFrom().toString());
                    setOptionalString(row, col++, line.getJobLevel());
                    setNumericCell(row, col, line.getRateAmount());
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new CustomerBadRequestException("Failed to generate rate card export: " + e.getMessage());
        }
    }

    public byte[] exportProjectCodes() {
        List<CustomerProjectCode> codes = projectCodeRepository.findAllForExport();
        codes.forEach(pc -> Hibernate.initialize(pc.getCustomer()));

        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Project Codes");
            String[] headers = {
                    ProjectCodeImportParser.COL_CUSTOMER_CODE,
                    ProjectCodeImportParser.COL_PROJECT_CODE,
                    ProjectCodeImportParser.COL_DESCRIPTION
            };
            writeHeaderRow(sheet, headers);

            int rowIdx = 1;
            for (CustomerProjectCode pc : codes) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(pc.getCustomer().getCustomerCode());
                row.createCell(1).setCellValue(pc.getProjectCode());
                setOptionalString(row, 2, pc.getDescription());
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new CustomerBadRequestException("Failed to generate project code export: " + e.getMessage());
        }
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
}
