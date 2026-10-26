package com.snehal.neopay.merchant.mapper;

import com.snehal.neopay.merchant.dto.response.ApiKeyCreateResponse;
import com.snehal.neopay.merchant.dto.response.ApiKeyResponse;
import com.snehal.neopay.merchant.entity.ApiKey;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ApiKeyMapper {

    ApiKeyCreateResponse toCreateResponse(ApiKey apiKey);

    ApiKeyResponse toResponse(ApiKey apiKey);

    List<ApiKeyResponse> toResponseList(List<ApiKey> apiKeyList);
}
