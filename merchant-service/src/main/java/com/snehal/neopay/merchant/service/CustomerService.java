package com.snehal.neopay.merchant.service;

import java.util.UUID;

public interface CustomerService {

    UUID findOrCreate(UUID merchantId, String email, String name, String phone);

}
