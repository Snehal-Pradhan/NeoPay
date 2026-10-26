package com.snehal.neopay.merchant.service.impl;

import com.snehal.neopay.common.enums.MerchantStatus;
import com.snehal.neopay.common.enums.UserRole;
import com.snehal.neopay.common.exception.BusinessRuleViolationException;
import com.snehal.neopay.common.exception.DuplicateResourceException;
import com.snehal.neopay.common.exception.ResourceNotFoundException;
import com.snehal.neopay.merchant.dto.request.LoginRequest;
import com.snehal.neopay.merchant.dto.request.MerchantSignupRequest;
import com.snehal.neopay.merchant.dto.response.LoginResponse;
import com.snehal.neopay.merchant.dto.response.MerchantResponse;
import com.snehal.neopay.merchant.entity.AppUser;
import com.snehal.neopay.merchant.entity.Merchant;
import com.snehal.neopay.merchant.mapper.MerchantMapper;
import com.snehal.neopay.merchant.repository.AppUserRepository;
import com.snehal.neopay.merchant.repository.MerchantRepository;
import com.snehal.neopay.merchant.security.JwtUtil;
import com.snehal.neopay.merchant.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AppUserRepository appUserRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantMapper merchantMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public MerchantResponse signup(MerchantSignupRequest request) {
        if (merchantRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("DUPLICATE_MERCHANT_EMAIL",
                    "Merchant with email already exists: " + request.email());
        }

        Merchant merchant = merchantMapper.toEntityFromSignUpRequest(request);
        merchant.setStatus(MerchantStatus.PENDING_KYC);

        merchant = merchantRepository.save(merchant);

        AppUser appUser = AppUser.builder()
                .email(request.email())
                .merchant(merchant)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.OWNER)
                .build();
        appUserRepository.save(appUser);

        return merchantMapper.toResponse(merchant);
    }

    @Override
    public LoginResponse login(LoginRequest request) {

        AppUser appUser = appUserRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.email()));

        if (!passwordEncoder.matches(request.password(), appUser.getPasswordHash())) {
            throw new BusinessRuleViolationException("INVALID_CREDENTIALS", "Invalid email or password");
        }

        String token = jwtUtil.generateAccessToken(request.email(), appUser.getMerchant().getId(), appUser.getRole().toString());

        return new LoginResponse(token);
    }
}















