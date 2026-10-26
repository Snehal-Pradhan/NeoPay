package com.snehal.neopay.operations.iso20022;

import com.snehal.neopay.common.iso20022.CreditTransfer;
import com.snehal.neopay.common.iso20022.Iso20022Codec;
import com.snehal.neopay.common.iso20022.PayoutInstruction;
import com.snehal.neopay.common.iso20022.StatementEntry;
import com.snehal.neopay.operations.recon.StatementRow;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/iso20022")
public class Iso20022Controller {

    @PostMapping(value = "/pacs008", consumes = MediaType.APPLICATION_XML_VALUE)
    public CreditTransfer parsePacs008(@RequestBody String xml) {
        return Iso20022Codec.parsePacs008(xml);
    }

    @PostMapping(value = "/pacs008/build", produces = MediaType.APPLICATION_XML_VALUE)
    public String buildPacs008(@RequestBody CreditTransfer transfer) {
        return Iso20022Codec.writePacs008(transfer);
    }

    @PostMapping(value = "/camt053", consumes = MediaType.APPLICATION_XML_VALUE)
    public List<StatementRow> parseCamt053(@RequestBody String xml) {
        return Iso20022Codec.parseCamt053(xml).stream()
                .map(entry -> new StatementRow(entry.reference(), entry.amountUnits(), entry.currency()))
                .toList();
    }

    @PostMapping(value = "/pain001", consumes = MediaType.APPLICATION_XML_VALUE)
    public List<PayoutInstruction> parsePain001(@RequestBody String xml) {
        return Iso20022Codec.parsePain001(xml);
    }

    @PostMapping(value = "/pain001/build", produces = MediaType.APPLICATION_XML_VALUE)
    public String buildPain001(@RequestBody List<PayoutInstruction> instructions) {
        return Iso20022Codec.writePain001(instructions);
    }

    @PostMapping(value = "/camt053/build", produces = MediaType.APPLICATION_XML_VALUE)
    public String buildCamt053(@RequestBody List<StatementEntry> entries) {
        return Iso20022Codec.writeCamt053(entries);
    }
}
