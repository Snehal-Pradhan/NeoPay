package com.snehal.neopay.common.iso20022;

import com.snehal.neopay.common.enums.Currency;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import javax.xml.validation.SchemaFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal ISO 20022 codec for the subset used by this platform.
 *
 * <p>Documents are validated against bundled XSD profiles before parsing. The parser
 * disables DTDs and external entities, so entity-expansion attacks are rejected.
 */
public final class Iso20022Codec {

    private static final String PACS_008_NS = "urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08";
    private static final String CAMT_053_NS = "urn:iso:std:iso:20022:tech:xsd:camt.053.001.08";
    private static final String PAIN_001_NS = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09";

    private Iso20022Codec() {
    }

    public static CreditTransfer parsePacs008(String xml) {
        Document document = validate(xml, "/iso20022/pacs.008.xsd");
        return new CreditTransfer(
                text(document, "EndToEndId"),
                amount(document, "IntrBkSttlmAmt"),
                currency(document, "IntrBkSttlmAmt"),
                text(document, "Dbtr", "Nm"),
                text(document, "Cdtr", "Nm"));
    }

    public static List<StatementEntry> parseCamt053(String xml) {
        Document document = validate(xml, "/iso20022/camt.053.xsd");
        NodeList entries = document.getElementsByTagNameNS("*", "Ntry");
        List<StatementEntry> rows = new ArrayList<>();
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            String reference = firstChildText(entry, "AcctSvcrRef");
            Element amount = firstChildElement(entry, "Amt");
            rows.add(new StatementEntry(reference, minorUnits(amount.getTextContent(),
                    amount.getAttribute("Ccy")), amount.getAttribute("Ccy")));
        }
        return rows;
    }

    public static List<PayoutInstruction> parsePain001(String xml) {
        Document document = validate(xml, "/iso20022/pain.001.xsd");
        NodeList transactions = document.getElementsByTagNameNS("*", "CdtTrfTxInf");
        List<PayoutInstruction> instructions = new ArrayList<>();
        for (int i = 0; i < transactions.getLength(); i++) {
            Element transaction = (Element) transactions.item(i);
            Element amount = firstChildElement(transaction, "InstdAmt");
            Element creditor = firstChildElement(transaction, "Cdtr");
            instructions.add(new PayoutInstruction(
                    firstChildText(transaction, "EndToEndId"),
                    minorUnits(amount.getTextContent(), amount.getAttribute("Ccy")),
                    amount.getAttribute("Ccy"),
                    firstChildText(creditor, "Nm")));
        }
        return instructions;
    }

    public static String writePacs008(CreditTransfer transfer) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<Document xmlns=\"").append(PACS_008_NS).append("\">\n")
                .append("  <FIToFICstmrCdtTrf>\n    <CdtTrfTxInf>\n")
                .append("      <PmtId><EndToEndId>").append(escape(transfer.endToEndId()))
                .append("</EndToEndId></PmtId>\n")
                .append("      <IntrBkSttlmAmt Ccy=\"").append(transfer.currency()).append("\">")
                .append(majorUnits(transfer.amountUnits(), transfer.currency()))
                .append("</IntrBkSttlmAmt>\n")
                .append("      <Dbtr><Nm>").append(escape(transfer.debtorName()))
                .append("</Nm></Dbtr>\n")
                .append("      <Cdtr><Nm>").append(escape(transfer.creditorName()))
                .append("</Nm></Cdtr>\n")
                .append("    </CdtTrfTxInf>\n  </FIToFICstmrCdtTrf>\n</Document>");
        return xml.toString();
    }

    public static String writeCamt053(List<StatementEntry> entries) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<Document xmlns=\"").append(CAMT_053_NS).append("\">\n")
                .append("  <BkToCstmrStmt>\n    <Stmt>\n");
        for (StatementEntry entry : entries) {
            xml.append("      <Ntry>\n")
                    .append("        <Amt Ccy=\"").append(entry.currency()).append("\">")
                    .append(majorUnits(entry.amountUnits(), entry.currency())).append("</Amt>\n")
                    .append("        <NtryDtls><TxDtls><Refs><AcctSvcrRef>")
                    .append(escape(entry.reference()))
                    .append("</AcctSvcrRef></Refs></TxDtls></NtryDtls>\n")
                    .append("      </Ntry>\n");
        }
        xml.append("    </Stmt>\n  </BkToCstmrStmt>\n</Document>");
        return xml.toString();
    }

    public static String writePain001(List<PayoutInstruction> instructions) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<Document xmlns=\"").append(PAIN_001_NS).append("\">\n")
                .append("  <CstmrCdtTrfInitn>\n    <PmtInf>\n");
        for (PayoutInstruction instruction : instructions) {
            xml.append("      <CdtTrfTxInf>\n")
                    .append("        <PmtId><EndToEndId>").append(escape(instruction.endToEndId()))
                    .append("</EndToEndId></PmtId>\n")
                    .append("        <Amt><InstdAmt Ccy=\"").append(instruction.currency())
                    .append("\">").append(majorUnits(instruction.amountUnits(), instruction.currency()))
                    .append("</InstdAmt></Amt>\n")
                    .append("        <Cdtr><Nm>").append(escape(instruction.creditorName()))
                    .append("</Nm></Cdtr>\n")
                    .append("      </CdtTrfTxInf>\n");
        }
        xml.append("    </PmtInf>\n  </CstmrCdtTrfInitn>\n</Document>");
        return xml.toString();
    }

    private static Document validate(String xml, String xsd) {
        Document document = parse(xml);
        try (InputStream schemaStream = Iso20022Codec.class.getResourceAsStream(xsd)) {
            if (schemaStream == null) {
                throw new Iso20022Exception("Schema not found: " + xsd);
            }
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Schema schema = factory.newSchema(
                    new javax.xml.transform.stream.StreamSource(schemaStream));

            Validator validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new DOMSource(document));
            return document;
        } catch (Iso20022Exception e) {
            throw e;
        } catch (Exception e) {
            throw new Iso20022Exception("ISO 20022 validation failed: " + e.getMessage(), e);
        }
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new Iso20022Exception("XML parse failed: " + e.getMessage(), e);
        }
    }

    private static String text(Document document, String name) {
        NodeList nodes = document.getElementsByTagNameNS("*", name);
        if (nodes.getLength() == 0) {
            throw new Iso20022Exception("Missing element: " + name);
        }
        return nodes.item(0).getTextContent();
    }

    private static String text(Document document, String parentName, String name) {
        Element parent = firstElement(document, parentName);
        String value = firstChildText(parent, name);
        if (value == null) {
            throw new Iso20022Exception("Missing element: " + parentName + "/" + name);
        }
        return value;
    }

    private static long amount(Document document, String name) {
        return minorUnits(text(document, name), currency(document, name));
    }

    private static String currency(Document document, String name) {
        Element element = firstElement(document, name);
        String currency = element.getAttribute("Ccy");
        if (currency == null || currency.isBlank()) {
            throw new Iso20022Exception("Missing currency on " + name);
        }
        return currency;
    }

    private static Element firstElement(Document document, String name) {
        NodeList nodes = document.getElementsByTagNameNS("*", name);
        if (nodes.getLength() == 0) {
            throw new Iso20022Exception("Missing element: " + name);
        }
        return (Element) nodes.item(0);
    }

    private static Element firstChildElement(Element parent, String name) {
        NodeList children = parent.getElementsByTagNameNS("*", name);
        if (children.getLength() == 0) {
            throw new Iso20022Exception("Missing child element: " + name);
        }
        return (Element) children.item(0);
    }

    private static String firstChildText(Element parent, String name) {
        NodeList children = parent.getElementsByTagNameNS("*", name);
        return children.getLength() == 0 ? null : children.item(0).getTextContent();
    }

    private static long minorUnits(String major, String currency) {
        try {
            int scale = Currency.from(currency).getScale();
            return new BigDecimal(major.trim()).setScale(scale, RoundingMode.UNNECESSARY)
                    .movePointRight(scale).longValueExact();
        } catch (Exception e) {
            throw new Iso20022Exception("Invalid amount '" + major + "' for " + currency, e);
        }
    }

    private static String majorUnits(long units, String currency) {
        int scale = Currency.from(currency).getScale();
        return BigDecimal.valueOf(units).movePointLeft(scale).toPlainString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
