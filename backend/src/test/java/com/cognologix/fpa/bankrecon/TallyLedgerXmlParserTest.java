package com.cognologix.fpa.bankrecon;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TallyLedgerXmlParserTest {

    private final TallyLedgerXmlParser parser = new TallyLedgerXmlParser();

    @Test
    void parsesLedgersWhenExportContainsIllegalXmlControlCharacters() {
        String xml = """
                <?xml version="1.0"?>
                <ENVELOPE>
                  <LEDGER NAME="HDFC Bank">
                    <PARENT>Bank Accounts</PARENT>
                  </LEDGER>
                  <LEDGER NAME="Office Rent">
                    <PARENT>Indirect Expenses</PARENT>
                  </LEDGER>
                </ENVELOPE>
                """
                .replace("HDFC Bank", "HDFC Bank" + "\u0004")
                .replace("Office Rent", "\u0008Office Rent\u000B");
        MockMultipartFile file = new MockMultipartFile(
                "file", "ledgers.xml", "text/xml", xml.getBytes(StandardCharsets.UTF_8));

        TallyLedgerXmlParser.ParseResult parsed = parser.parse(file);

        assertThat(parsed.ledgers()).containsExactly(
                new TallyLedgerXmlParser.ParsedLedger("HDFC Bank", "Bank Accounts"),
                new TallyLedgerXmlParser.ParsedLedger("Office Rent", "Indirect Expenses"));
    }

    @Test
    void parsesUtf16LeBomExportWithControlCharacters() {
        String xml = """
                <?xml version="1.0"?>
                <ENVELOPE>
                  <LEDGER NAME="HDFC Bank">
                    <PARENT>Bank Accounts</PARENT>
                  </LEDGER>
                </ENVELOPE>
                """.replace("HDFC Bank", "HDFC Bank\u0004");
        byte[] body = xml.getBytes(StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[2 + body.length];
        withBom[0] = (byte) 0xFF;
        withBom[1] = (byte) 0xFE;
        System.arraycopy(body, 0, withBom, 2, body.length);
        MockMultipartFile file = new MockMultipartFile(
                "file", "ledgers.xml", "text/xml", withBom);

        TallyLedgerXmlParser.ParseResult parsed = parser.parse(file);

        assertThat(parsed.ledgers()).containsExactly(
                new TallyLedgerXmlParser.ParsedLedger("HDFC Bank", "Bank Accounts"));
    }

    @Test
    void parsesWhenExportContainsLiteralInvalidCharacterReferences() {
        String xml = """
                <?xml version="1.0"?>
                <ENVELOPE>
                  <LEDGER NAME="HDFC Bank&#4;">
                    <PARENT>Bank Accounts</PARENT>
                  </LEDGER>
                  <LEDGER NAME="Office&#8; Rent&#11;">
                    <PARENT>Indirect Expenses</PARENT>
                  </LEDGER>
                </ENVELOPE>
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "ledgers.xml", "text/xml", xml.getBytes(StandardCharsets.UTF_8));

        TallyLedgerXmlParser.ParseResult parsed = parser.parse(file);

        assertThat(parsed.ledgers()).containsExactly(
                new TallyLedgerXmlParser.ParsedLedger("HDFC Bank", "Bank Accounts"),
                new TallyLedgerXmlParser.ParsedLedger("Office Rent", "Indirect Expenses"));
    }

    @Test
    void stripsCarriageReturnAndNewlineFromLedgerNames() {
        String xml = """
                <?xml version="1.0"?>
                <ENVELOPE>
                  <LEDGER NAME="A1 Group&#13;&#10;">
                    <PARENT>Sundry Debtors</PARENT>
                  </LEDGER>
                  <LEDGER NAME="Cash&#13;On&#10;Hand">
                    <PARENT>Cash-in-Hand</PARENT>
                  </LEDGER>
                </ENVELOPE>
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "ledgers.xml", "text/xml", xml.getBytes(StandardCharsets.UTF_8));

        TallyLedgerXmlParser.ParseResult parsed = parser.parse(file);

        assertThat(parsed.ledgers()).containsExactly(
                new TallyLedgerXmlParser.ParsedLedger("A1 Group", "Sundry Debtors"),
                new TallyLedgerXmlParser.ParsedLedger("CashOnHand", "Cash-in-Hand"));
    }

    @Test
    void unescapesXmlEntitiesInLedgerNames() {
        String xml = """
                <?xml version="1.0"?>
                <ENVELOPE>
                  <LEDGER NAME="P&amp;L Appropriation">
                    <PARENT>Reserves &amp; Surplus</PARENT>
                  </LEDGER>
                </ENVELOPE>
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "ledgers.xml", "text/xml", xml.getBytes(StandardCharsets.UTF_8));

        TallyLedgerXmlParser.ParseResult parsed = parser.parse(file);

        assertThat(parsed.ledgers()).containsExactly(
                new TallyLedgerXmlParser.ParsedLedger("P&L Appropriation", "Reserves & Surplus"));
    }

    @Test
    void treatsEmptyParentAsNull() {
        String xml = """
                <?xml version="1.0"?>
                <ENVELOPE>
                  <LEDGER NAME="Orphan Ledger">
                    <PARENT/>
                  </LEDGER>
                  <LEDGER NAME="Blank Parent">
                    <PARENT></PARENT>
                  </LEDGER>
                </ENVELOPE>
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "ledgers.xml", "text/xml", xml.getBytes(StandardCharsets.UTF_8));

        TallyLedgerXmlParser.ParseResult parsed = parser.parse(file);

        assertThat(parsed.ledgers()).containsExactly(
                new TallyLedgerXmlParser.ParsedLedger("Orphan Ledger", null),
                new TallyLedgerXmlParser.ParsedLedger("Blank Parent", null));
    }

    @Test
    void parsesCustomGroupsAndLedgersWithParentChain() {
        String xml = """
                <?xml version="1.0"?>
                <ENVELOPE>
                  <GROUP NAME="Salary Payable">
                    <PARENT>Current Liabilities</PARENT>
                  </GROUP>
                  <GROUP NAME="Contract Salaries">
                    <PARENT>Salary Payable</PARENT>
                  </GROUP>
                  <LEDGER NAME="Salary Payable">
                    <PARENT>Salary Payable</PARENT>
                  </LEDGER>
                  <LEDGER NAME="Contractor A">
                    <PARENT>Contract Salaries</PARENT>
                  </LEDGER>
                </ENVELOPE>
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "ledgers.xml", "text/xml", xml.getBytes(StandardCharsets.UTF_8));

        TallyLedgerXmlParser.ParseResult parsed = parser.parse(file);

        assertThat(parsed.groups()).containsExactly(
                new TallyLedgerXmlParser.ParsedGroup("Salary Payable", "Current Liabilities"),
                new TallyLedgerXmlParser.ParsedGroup("Contract Salaries", "Salary Payable"));
        assertThat(parsed.ledgers()).containsExactly(
                new TallyLedgerXmlParser.ParsedLedger("Salary Payable", "Salary Payable"),
                new TallyLedgerXmlParser.ParsedLedger("Contractor A", "Contract Salaries"));
    }
}
