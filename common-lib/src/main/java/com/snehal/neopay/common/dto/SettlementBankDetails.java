package com.snehal.neopay.common.dto;

public record SettlementBankDetails(String accountNumber, String ifsc, String accountHolderName) {

    @Override
    public String toString() {
        String masked = accountNumber == null || accountNumber.length() < 4
                ? null
                : "****" + accountNumber.substring(accountNumber.length() - 4);
        return "SettlementBankDetails[accountNumber=" + masked + ", ifsc=" + ifsc
                + ", accountHolderName=" + accountHolderName + "]";
    }
}
