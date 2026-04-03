package com.innowise.orderservice.mapper;

import com.innowise.orderservice.dto.response.OrderItemResponse;
import com.innowise.orderservice.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = ItemMapper.class)
public interface OrderItemMapper {

    @Mapping(target = "item", source = "item")
    OrderItemResponse toResponse(OrderItem orderItem);
}
