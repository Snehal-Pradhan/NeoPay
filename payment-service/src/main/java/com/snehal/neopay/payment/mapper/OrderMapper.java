package com.snehal.neopay.payment.mapper;


import com.snehal.neopay.payment.dto.response.OrderResponse;
import com.snehal.neopay.payment.entity.OrderRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderMapper {

    @Mapping(source = "orderStatus", target = "status")
    OrderResponse toResponse(OrderRecord orderRecord);
}
