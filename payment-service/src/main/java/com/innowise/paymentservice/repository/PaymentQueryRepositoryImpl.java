package com.innowise.paymentservice.repository;

import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.model.PaymentTotal;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class PaymentQueryRepositoryImpl implements PaymentQueryRepository {

    private static final String TOTAL = "total";
    private static final String COUNT = "count";

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<Payment> search(UUID userId, UUID orderId, PaymentStatus status, Pageable pageable) {
        List<Criteria> conditions = new ArrayList<>();
        addIfPresent(conditions, "userId", userId);
        addIfPresent(conditions, "orderId", orderId);
        addIfPresent(conditions, "status", status);

        Query query = new Query(combine(conditions)).with(pageable);
        List<Payment> payments = mongoTemplate.find(query, Payment.class);
        return PageableExecutionUtils.getPage(payments, pageable,
                () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Payment.class));
    }

    @Override
    public PaymentTotal sumAmount(UUID userId, PaymentStatus status, Instant from, Instant to) {
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(Criteria.where("timestamp").gte(from).lte(to));
        addIfPresent(conditions, "userId", userId);
        addIfPresent(conditions, "status", status);
        TypedAggregation<Payment> aggregation = Aggregation.newAggregation(Payment.class,
                Aggregation.match(combine(conditions)),
                Aggregation.group()
                        .sum("paymentAmount").as(TOTAL)
                        .count().as(COUNT));

        Document result = mongoTemplate.aggregate(aggregation, Document.class).getUniqueMappedResult();
        if (result == null) {
            return PaymentTotal.EMPTY;
        }
        return new PaymentTotal(toBigDecimal(result.get(TOTAL)), toLong(result.get(COUNT)));
    }

    private static void addIfPresent(List<Criteria> conditions, String property, Object value) {
        if (value != null) {
            conditions.add(Criteria.where(property).is(value));
        }
    }

    private static Criteria combine(List<Criteria> conditions) {
        return conditions.isEmpty() ? new Criteria() : new Criteria().andOperator(conditions);
    }

    private static BigDecimal toBigDecimal(Object value) {
        return switch (value) {
            case null -> BigDecimal.ZERO;
            case Decimal128 decimal128 -> decimal128.bigDecimalValue();
            case BigDecimal bigDecimal -> bigDecimal;
            case String string -> new BigDecimal(string);
            case Number number -> new BigDecimal(number.toString());
            default -> throw new IllegalStateException(
                    "Unexpected type of the aggregated sum: " + value.getClass().getName());
        };
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
