package com.snehal.neopay.merchant.mapper;

import com.snehal.neopay.merchant.dto.request.MerchantSignupRequest;
import com.snehal.neopay.merchant.dto.response.MerchantResponse;
import com.snehal.neopay.merchant.entity.Merchant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MerchantMapper {

    Merchant toEntityFromSignUpRequest(MerchantSignupRequest request);

    @Mapping(source = "status", target = "merchantStatus")
    MerchantResponse toResponse(Merchant merchant);
}
