package com.snehal.neopay.payment.dispute;

import jakarta.validation.constraints.Size;

/** Merchant statement accompanying a representment. */
public record RepresentRequest(@Size(max = 255) String statement) {
}
