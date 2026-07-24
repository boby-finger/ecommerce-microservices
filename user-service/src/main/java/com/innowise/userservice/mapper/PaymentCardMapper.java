package com.innowise.userservice.mapper;

import com.innowise.userservice.dto.PaymentCardRequestDto;
import com.innowise.userservice.dto.PaymentCardResponseDto;
import com.innowise.userservice.model.PaymentCard;
import com.innowise.userservice.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface PaymentCardMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdAt",ignore = true)
    @Mapping(target = "active", constant = "true")
    PaymentCard toEntity(User user, PaymentCardRequestDto dto);


    @Mapping(target = "cardNumber", source = "cardNumber", qualifiedByName = "hideCardNumber")
    PaymentCardResponseDto toDto(PaymentCard paymentCard);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updatePaymentCard(@MappingTarget PaymentCard paymentCard, PaymentCardRequestDto paymentCardRequestDto);

    @Named("hideCardNumber")
    default String hideCardNumber(String cardNumber) {
        //мб добавить проверку но скорее всего такого просто невозможно изза валидации входящего requestdto
        return "**** **** **** " +  cardNumber.substring(cardNumber.length() - 4);
    }
}
