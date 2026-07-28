package com.cognologix.fpa.customer;

import com.cognologix.fpa.customer.domain.*;
import com.cognologix.fpa.customer.repository.CommercialTermsRepository;
import com.cognologix.fpa.customer.repository.CustomerProjectCodeRepository;
import com.cognologix.fpa.customer.repository.CustomerRepository;
import com.cognologix.fpa.customer.repository.RateCardProjectCodeRepository;
import com.cognologix.fpa.customer.repository.RateCardRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
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
    private final RateCardProjectCodeRepository rateCardProjectCodeRepository;
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
            writeHeaderRow(sheet, CustomerImportParser.IMPORT_EXPORT_HEADERS);

            int rowIdx = 1;
            for (Customer customer : customers) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(customer.getCustomerCode());
                row.createCell(col++).setCellValue(customer.getCustomerName());
                setOptionalString(row, col++, customer.getZohoBooksCustomerRef());
                row.createCell(col++).setCellValue(customer.getLifecycleStatus().name());
                row.createCell(col++).setCellValue(customer.isInternal() ? "true" : "false");
                setOptionalString(row, col++, customer.getRelationshipOwnerEmployeeId());
                int dsoDays = dsoByCustomerId.getOrDefault(customer.getId(), 0);
                row.createCell(col).setCellValue(dsoDays);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new CustomerBadRequestException("Failed to generate customer export: " + e.getMessage());
        }
    }

    /**
     * Export every rate card for every customer (no UI/customer filter).
     * Rows expand rate_card → rate_card_line; project codes come from
     * rate_card_project_code → customer_project_code. Sorted by Customer Code ASC,
     * Effective From ASC for stable re-import.
     */
    public byte[] exportRateCards() {
        List<RateCard> cards = new ArrayList<>(rateCardRepository.findAllForExport());
        cards.sort(Comparator
                .comparing((RateCard rc) -> rc.getCustomer().getCustomerCode(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RateCard::getEffectiveFrom));

        Map<UUID, String> projectCodeById = projectCodeRepository.findAll().stream()
                .collect(Collectors.toMap(CustomerProjectCode::getId, CustomerProjectCode::getProjectCode));
        Map<UUID, List<UUID>> projectIdsByRateCard = rateCardProjectCodeRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        RateCardProjectCode::getRateCardId,
                        Collectors.mapping(RateCardProjectCode::getProjectCodeId, Collectors.toList())));

        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Rate Cards");
            String[] headers = {
                    RateCardImportParser.COL_CUSTOMER_CODE,
                    RateCardImportParser.COL_PROJECT_CODE,
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
                List<String> codes = projectIdsByRateCard.getOrDefault(card.getId(), List.of()).stream()
                        .map(projectCodeById::get)
                        .filter(c -> c != null && !c.isBlank())
                        .sorted()
                        .toList();
                String projectCodeCell = codes.isEmpty() ? null : String.join(";", codes);
                // Flat/blended cards with no line rows still emit one data row so the card is not dropped.
                if (lines.isEmpty()) {
                    Row row = sheet.createRow(rowIdx++);
                    writeRateCardRow(row, card, projectCodeCell, null, null);
                    continue;
                }
                for (RateCardLine line : lines) {
                    Row row = sheet.createRow(rowIdx++);
                    writeRateCardRow(row, card, projectCodeCell, line.getJobLevel(), line.getRateAmount());
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new CustomerBadRequestException("Failed to generate rate card export: " + e.getMessage());
        }
    }

    private static void writeRateCardRow(
            Row row, RateCard card, String projectCodeCell, String jobLevel, BigDecimal rateAmount) {
        int col = 0;
        row.createCell(col++).setCellValue(card.getCustomer().getCustomerCode());
        setOptionalString(row, col++, projectCodeCell);
        row.createCell(col++).setCellValue(card.getName());
        row.createCell(col++).setCellValue(card.getRateCardType().name());
        row.createCell(col++).setCellValue(card.getCurrency().name());
        row.createCell(col++).setCellValue(card.getEffectiveFrom().toString());
        setOptionalString(row, col++, jobLevel);
        setNumericCell(row, col, rateAmount);
    }

    /**
     * Export every project code for every customer (no UI/customer filter).
     * Sorted by Customer Code ASC, Project Code ASC.
     */
    public byte[] exportProjectCodes() {
        List<CustomerProjectCode> codes = new ArrayList<>(projectCodeRepository.findAllForExport());
        codes.sort(Comparator
                .comparing((CustomerProjectCode pc) -> pc.getCustomer().getCustomerCode(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CustomerProjectCode::getProjectCode, String.CASE_INSENSITIVE_ORDER));

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
