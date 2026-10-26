package com.snehal.neopay.merchant.service.impl;

import com.snehal.neopay.common.enums.BusinessType;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final MerchantRepository merchantRepository = mock(MerchantRepository.class);
    private final MerchantMapper merchantMapper = mock(MerchantMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);

    private final AuthServiceImpl service = new AuthServiceImpl(appUserRepository, merchantRepository,
            merchantMapper, passwordEncoder, jwtUtil);

    private static MerchantSignupRequest signupRequest() {
        return new MerchantSignupRequest("Acme", "owner@acme.test", "password123",
                "Acme Payments", BusinessType.LLP);
    }

    @Test
    void signupRejectsDuplicateEmailBeforeWriting() {
        when(merchantRepository.existsByEmail("owner@acme.test")).thenReturn(true);

        DuplicateResourceException error = assertThrows(DuplicateResourceException.class,
                () -> service.signup(signupRequest()));

        assertEquals("DUPLICATE_MERCHANT_EMAIL", error.getErrorCode());
        verify(merchantRepository, never()).save(any());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void signupCreatesPendingKycMerchantAndOwnerUser() {
        Merchant merchant = Merchant.builder().id(UUID.randomUUID()).email("owner@acme.test").build();
        when(merchantRepository.existsByEmail("owner@acme.test")).thenReturn(false);
        when(merchantMapper.toEntityFromSignUpRequest(any())).thenReturn(merchant);
        when(merchantRepository.save(merchant)).thenReturn(merchant);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");
        MerchantResponse expected = new MerchantResponse(merchant.getId(), "Acme", "owner@acme.test",
                "Acme Payments", BusinessType.LLP, MerchantStatus.PENDING_KYC);
        when(merchantMapper.toResponse(merchant)).thenReturn(expected);

        MerchantResponse response = service.signup(signupRequest());

        assertEquals(expected, response);
        assertEquals(MerchantStatus.PENDING_KYC, merchant.getStatus());

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
        AppUser saved = userCaptor.getValue();
        assertEquals("owner@acme.test", saved.getEmail());
        assertEquals("encoded-hash", saved.getPasswordHash());
        assertEquals(UserRole.OWNER, saved.getRole());
        assertEquals(merchant, saved.getMerchant());
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(appUserRepository.findByEmail("missing@acme.test")).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class,
                () -> service.login(new LoginRequest("missing@acme.test", "password123")));

        assertEquals("User", error.getResourceName());
    }

    @Test
    void loginRejectsWrongPassword() {
        AppUser user = AppUser.builder()
                .email("owner@acme.test")
                .passwordHash("encoded-hash")
                .role(UserRole.OWNER)
                .merchant(Merchant.builder().id(UUID.randomUUID()).build())
                .build();
        when(appUserRepository.findByEmail("owner@acme.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-hash")).thenReturn(false);

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> service.login(new LoginRequest("owner@acme.test", "wrong-password")));

        assertEquals("INVALID_CREDENTIALS", error.getErrorCode());
        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void loginIssuesTokenForValidCredentials() {
        UUID merchantId = UUID.randomUUID();
        AppUser user = AppUser.builder()
                .email("owner@acme.test")
                .passwordHash("encoded-hash")
                .role(UserRole.OWNER)
                .merchant(Merchant.builder().id(merchantId).build())
                .build();
        when(appUserRepository.findByEmail("owner@acme.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-hash")).thenReturn(true);
        when(jwtUtil.generateAccessToken("owner@acme.test", merchantId, "OWNER")).thenReturn("signed-token");

        LoginResponse response = service.login(new LoginRequest("owner@acme.test", "password123"));

        assertEquals("signed-token", response.accessToken());
    }
}
