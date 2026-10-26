package com.snehal.neopay.merchant.service;


import com.snehal.neopay.merchant.dto.request.LoginRequest;
import com.snehal.neopay.merchant.dto.request.MerchantSignupRequest;
import com.snehal.neopay.merchant.dto.response.LoginResponse;
import com.snehal.neopay.merchant.dto.response.MerchantResponse;

public interface AuthService {
    MerchantResponse signup(MerchantSignupRequest request);

    LoginResponse login(LoginRequest request);
}
