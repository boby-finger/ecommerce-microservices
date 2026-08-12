package com.innowise.orderservice.mapper;

import com.innowise.orderservice.dto.ItemResponseDto;
import com.innowise.orderservice.dto.OrderItemResponseDto;
import com.innowise.orderservice.dto.OrderResponseDto;
import com.innowise.orderservice.dto.OrderSummaryDto;
import com.innowise.orderservice.dto.UserInfoDto;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "userId", source = "order.userId")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "status", source = "order.status")
    @Mapping(target = "totalPrice", source = "order.totalPrice")
    @Mapping(target = "items", source = "order.items")
    @Mapping(target = "createdAt", source = "order.createdAt")
    @Mapping(target = "updatedAt", source = "order.updatedAt")
    OrderResponseDto toDto(Order order, UserInfoDto user);

    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "userId", source = "order.userId")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "status", source = "order.status")
    @Mapping(target = "totalPrice", source = "order.totalPrice")
    @Mapping(target = "createdAt", source = "order.createdAt")
    OrderSummaryDto toSummaryDto(Order order, UserInfoDto user);

    OrderItemResponseDto toDto(OrderItem orderItem);

    ItemResponseDto toDto(Item item);
}