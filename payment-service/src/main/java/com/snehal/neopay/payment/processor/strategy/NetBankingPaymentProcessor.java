package com.snehal.neopay.payment.processor.strategy;

import com.snehal.neopay.common.dto.PaymentProcessorRequest;
import com.snehal.neopay.common.dto.PaymentProcessorResponse;
import com.snehal.neopay.common.util.RandomizerUtil;
import com.snehal.neopay.payment.processor.PaymentProcessor;
import org.springframework.stereotype.Component;

@Component
public class NetBankingPaymentProcessor implements PaymentProcessor {

    @Override
    public PaymentProcessorResponse charge(PaymentProcessorRequest request) {

        final String BANK_CODE_FAIL = "BANK_CODE_FAIL";

        String bankCode = request.methodDetails() != null ?
                request.methodDetails().get("bank").toString() : null;

        // simulation
        if (BANK_CODE_FAIL.equals(bankCode)) {
            return new PaymentProcessorResponse.Failure("BANK_REJECTED",
                    "Banked rejected the transaction registration"
                    );
        }

        String processorRef = "NBK_PROCESSOR_"+ RandomizerUtil.randomBase64(16);

//        String redirectRef = "http://REDIRECT_BANK.com/"+processorRef;

        return new PaymentProcessorResponse.Pending(processorRef);
    }
}
