package com.innowise.userservice.service;

import com.innowise.userservice.config.CacheConfig;
import com.innowise.userservice.dto.PaymentCardRequestDto;
import com.innowise.userservice.dto.PaymentCardResponseDto;
import com.innowise.userservice.exceptions.CardLimitException;
import com.innowise.userservice.exceptions.CardNotFoundException;
import com.innowise.userservice.exceptions.UserNotFoundException;
import com.innowise.userservice.mapper.PaymentCardMapper;
import com.innowise.userservice.model.PaymentCard;
import com.innowise.userservice.model.User;
import com.innowise.userservice.repository.PaymentCardRepository;
import com.innowise.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentCardService {
    private static final int MAX_CARDS = 5;

    private final PaymentCardRepository paymentCardRepository;
    private final UserRepository userRepository;
    private final PaymentCardMapper paymentCardMapper;
    private final CacheManager cacheManager;

    public void cacheEvictForUser(UUID userId)
    {
        Cache cache = cacheManager.getCache(CacheConfig.USERS_CACHE);
        if (cache != null) {
            cache.evict(userId);
        }
    }

    @Transactional
    @CacheEvict(value = CacheConfig.USERS_CACHE, key = "#userId")
    public PaymentCardResponseDto createPaymentCard(UUID userId, PaymentCardRequestDto paymentCardRequestDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if(paymentCardRepository.countCardsByUserId(user.getId()) >= MAX_CARDS)
            {throw new CardLimitException(MAX_CARDS);}
        PaymentCard card = paymentCardMapper.toEntity(user, paymentCardRequestDto);
        user.addPaymentCard(card);
        paymentCardRepository.flush();
        return paymentCardMapper.toDto(card);
    }

    public PaymentCardResponseDto getPaymentCardById(UUID id){
        PaymentCard card =  paymentCardRepository.findById(id)
                .orElseThrow(() -> new CardNotFoundException(id));
        return paymentCardMapper.toDto(card);
    }

    public List<PaymentCardResponseDto> getAllPaymentCardsByUserId(UUID userId){
        if(userRepository.findById(userId).isEmpty()) throw new UserNotFoundException(userId);
        return paymentCardRepository.findAllByUserId(userId)
                .stream()
                .map(paymentCardMapper::toDto)
                .toList();
    }

    @Transactional
    public PaymentCardResponseDto updatePaymentCard(UUID id, PaymentCardRequestDto paymentCardRequestDto) {
        PaymentCard card = paymentCardRepository.findById(id)
                .orElseThrow(() -> new CardNotFoundException(id));
        paymentCardMapper.updatePaymentCard(card, paymentCardRequestDto);
        cacheEvictForUser(card.getUser().getId());
        return paymentCardMapper.toDto(card);
    }

    @Transactional
    public void setActivePaymentCard(UUID id, boolean active) {
        UUID userId = paymentCardRepository.findUserIdByIdCard(id).orElseThrow(() -> new CardNotFoundException(id));
        paymentCardRepository.updateActiveStatusCardById(id, Instant.now(), active);
        cacheEvictForUser(userId);
    }

    @Transactional
    public void deletePaymentCardById(UUID id) {
        PaymentCard card = paymentCardRepository.findById(id)
                .orElseThrow(() -> new CardNotFoundException(id));
        UUID userId = card.getUser().getId();
        card.getUser().removePaymentCard(card);
        cacheEvictForUser(userId);
    }
}
