package com.innowise.paymentservice.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Document(collection = "payments")
@NoArgsConstructor
@Getter
@Setter
public class Payment {

    @Id
    private UUID id;

    @Field("order_id")
    private UUID orderId;

    @Field("user_id")
    private UUID userId;

    @Field("status")
    private PaymentStatus status;

    @CreatedDate
    @Field("timestamp")
    private Instant timestamp;

    @Field("payment_amount")
    private BigDecimal paymentAmount;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Payment that = (Payment) obj;
        return this.id != null && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
