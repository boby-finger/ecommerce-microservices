package com.innowise.userservice.mapper;

import com.innowise.userservice.dto.PaymentCardRequestDto;
import com.innowise.userservice.dto.PaymentCardResponseDto;
import com.innowise.userservice.model.PaymentCard;
import com.innowise.userservice.model.User;
import org.springframework.stereotype.Component;

@Component
public class PaymentCardMapper {
    public PaymentCard toEntity(User user, PaymentCardRequestDto paymentCardRequestDto) {
        PaymentCard paymentCard = new PaymentCard();
        paymentCard.setUser(user);
        paymentCard.setCardNumber(paymentCardRequestDto.cardNumber());
        paymentCard.setCardHolder(paymentCardRequestDto.cardHolder());
        paymentCard.setExpirationDate(paymentCardRequestDto.expirationDate());
        paymentCard.setActive(true);
        return paymentCard;
    }

    public PaymentCardResponseDto toDto(PaymentCard paymentCard) {
        return new PaymentCardResponseDto(paymentCard.getId(),
                hideCardNumber(paymentCard.getCardNumber()),
                paymentCard.getCardHolder(),
                paymentCard.getExpirationDate(),
                paymentCard.getActive());
    }

    public void updatePaymentCard(PaymentCard paymentCard, PaymentCardRequestDto paymentCardRequestDto) {
        paymentCard.setCardNumber(paymentCardRequestDto.cardNumber());
        paymentCard.setCardHolder(paymentCardRequestDto.cardHolder());
        paymentCard.setExpirationDate(paymentCardRequestDto.expirationDate());
    }

    public String hideCardNumber(String cardNumber) {
        //мб добавить проверку но скорее всего такого просто невозможно изза валидации входящего requestdto
        return "**** **** ****" +  cardNumber.substring(cardNumber.length() - 4);
    }
}
