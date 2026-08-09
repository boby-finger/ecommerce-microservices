package com.innowise.orderservice.mapper;

import com.innowise.orderservice.dto.ItemResponseDto;
import com.innowise.orderservice.dto.OrderItemResponseDto;
import com.innowise.orderservice.dto.OrderResponseDto;
import com.innowise.orderservice.dto.OrderSummaryDto;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderResponseDto toDto(Order order);

    OrderSummaryDto toSummaryDto(Order order);

    OrderItemResponseDto toDto(OrderItem orderItem);

    ItemResponseDto toDto(Item item);
}