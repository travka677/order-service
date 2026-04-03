package com.innowise.orderservice.mapper;

import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.dto.response.UserResponse;
import com.innowise.orderservice.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = OrderItemMapper.class)
public interface OrderMapper {

    @Mapping(target = "items", source = "items")
    @Mapping(target = "user", ignore = true)
    OrderResponse toResponse(Order order);

    default OrderResponse toResponse(Order order, UserResponse user) {
        OrderResponse base = toResponse(order);
        return new OrderResponse(
                base.getId(),
                base.getUserId(),
                base.getStatus(),
                base.getTotalPrice(),
                base.getItems(),
                user,
                base.getCreatedAt(),
                base.getUpdatedAt()
        );
    }
}
