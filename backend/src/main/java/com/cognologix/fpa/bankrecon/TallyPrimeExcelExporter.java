package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.domain.DebitCredit;
import com.cognologix.fpa.bankrecon.domain.ReconRun;
import com.cognologix.fpa.bankrecon.domain.ReconTransaction;
import com.cognologix.fpa.bankrecon.domain.VoucherType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class TallyPrimeExcelExporter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String[] HEADERS = {
            "Voucher Date", "Voucher Type", "Voucher No", "Debit Ledger", "Credit Ledger",
            "Amount", "Narration"
    };

    record ExportFile(byte[] bytes, String filename) {}

    ExportFile export(ReconRun run, List<ReconTransaction> transactions, String bankLedger, int exportNumber) {
        List<ReconTransaction> payments = transactions.stream()
                .filter(t -> t.getVoucherType() == VoucherType.PAYMENT).toList();
        List<ReconTransaction> receipts = transactions.stream()
                .filter(t -> t.getVoucherType() == VoucherType.RECEIPT).toList();
        List<ReconTransaction> contras = transactions.stream()
                .filter(t -> t.getVoucherType() == VoucherType.CONTRA).toList();
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeSheet(wb, "Payment", payments, run, bankLedger, "PV");
            writeSheet(wb, "Receipt", receipts, run, bankLedger, "RV");
            writeSheet(wb, "Contra", contras, run, bankLedger, "CV");
            wb.write(out);
            String filename = "finsync-" + run.getRunNumber() + "-export-" + exportNumber + ".xlsx";
            return new ExportFile(out.toByteArray(), filename);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate TallyPrime export", e);
        }
    }

    private void writeSheet(XSSFWorkbook wb, String name, List<ReconTransaction> rows,
                            ReconRun run, String bankLedger, String prefix) {
        Sheet sheet = wb.createSheet(name);
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            header.createCell(i).setCellValue(HEADERS[i]);
        }
        int seq = 1;
        int r = 1;
        for (ReconTransaction tx : rows) {
            String voucherNo = prefix + "-" + run.getRunNumber() + "-" + String.format("%03d", seq++);
            String debit;
            String credit;
            if (tx.getVoucherType() == VoucherType.PAYMENT) {
                debit = tx.getMappedLedger();
                credit = bankLedger;
            } else if (tx.getVoucherType() == VoucherType.RECEIPT) {
                debit = bankLedger;
                credit = tx.getMappedLedger();
            } else if (tx.getDebitCredit() == DebitCredit.D) {
                debit = tx.getMappedLedger();
                credit = bankLedger;
            } else {
                debit = bankLedger;
                credit = tx.getMappedLedger();
            }
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(formatDate(tx));
            row.createCell(1).setCellValue(tx.getVoucherType().name());
            row.createCell(2).setCellValue(voucherNo);
            row.createCell(3).setCellValue(debit != null ? debit : "");
            row.createCell(4).setCellValue(credit != null ? credit : "");
            row.createCell(5).setCellValue(tx.getAmount() != null ? tx.getAmount().doubleValue() : 0);
            row.createCell(6).setCellValue(tx.getDescription());
        }
        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static String formatDate(ReconTransaction tx) {
        LocalDate date = tx.getValueDate();
        if (date == null && tx.getTransactionDate() != null) {
            date = tx.getTransactionDate().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return date != null ? DATE.format(date) : "";
    }
}
