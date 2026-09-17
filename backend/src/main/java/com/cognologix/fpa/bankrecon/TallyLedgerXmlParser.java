package com.cognologix.fpa.bankrecon;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class TallyLedgerXmlParser {

    record ParsedLedger(String name, String parentGroup) {}

    record ParsedGroup(String name, String parentGroup) {}

    record ParseResult(List<ParsedGroup> groups, List<ParsedLedger> ledgers) {}

    ParseResult parse(MultipartFile xmlFile) {
        try {
            byte[] bytes = xmlFile.getBytes();
            String xml = readTallyXml(bytes);
            String cleanXml = sanitizeTallyXml(xml);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = factory.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(cleanXml)));
            return new ParseResult(namedElements(doc, "GROUP"), namedElements(doc, "LEDGER").stream()
                    .map(g -> new ParsedLedger(g.name(), g.parentGroup()))
                    .toList());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse TallyPrime XML: " + e.getMessage(), e);
        }
    }

    static String readTallyXml(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        // Detect UTF-16 LE BOM (FF FE)
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        // Detect UTF-16 BE BOM (FE FF)
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        // Detect UTF-8 BOM (EF BB BF)
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        // Default UTF-8
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * TallyPrime exports sometimes include XML 1.0-illegal control characters
     * as both raw bytes and literal character references ({@code &#4;}, {@code &#8;},
     * {@code &#11;}, etc.).
     */
    static String sanitizeTallyXml(String xml) {
        if (xml == null) {
            return "";
        }
        // Remove invalid XML 1.0 character references (&#0; through &#8;, &#11;, &#12;, &#14; through &#31;)
        // These are literal strings like "&#4;" that TallyPrime writes in the XML
        String cleaned = xml.replaceAll("&#(?:[0-8]|1[124]|1[5-9]|2[0-9]|3[01]);", "");
        // Also remove actual control character bytes (except tab=9, newline=10, carriage return=13)
        cleaned = cleaned.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        return cleaned;
    }

    static String cleanXmlString(String value) {
        if (value == null) {
            return null;
        }
        String unescaped = StringEscapeUtils.unescapeXml(value);
        String cleanName = unescaped.replace("\r\n", "").replace("\r", "").replace("\n", "").trim();
        return cleanName.isEmpty() ? null : cleanName;
    }

    private static List<ParsedGroup> namedElements(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        List<ParsedGroup> out = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element element)) {
                continue;
            }
            String name = firstNonBlank(
                    cleanXmlString(element.getAttribute("NAME")),
                    cleanXmlString(element.getAttribute("name")));
            if (name != null) {
                out.add(new ParsedGroup(name, directParent(element)));
            }
        }
        return out;
    }

    private static String directParent(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && "PARENT".equals(child.getTagName())) {
                return cleanXmlString(child.getTextContent());
            }
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
