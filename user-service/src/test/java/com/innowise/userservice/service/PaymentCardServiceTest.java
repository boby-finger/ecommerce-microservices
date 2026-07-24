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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class PaymentCardServiceTest {


    @Mock
    private PaymentCardRepository paymentCardRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private PaymentCardMapper paymentCardMapper;

    @InjectMocks
    private PaymentCardService paymentCardService;

    private UUID cardId;
    private UUID userId;
    private PaymentCard paymentCard;
    private User user;
    private PaymentCardRequestDto  paymentCardRequestDto;


    @BeforeEach
    public void setUp() {
        userId = UUID.randomUUID();
        cardId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        paymentCard = new PaymentCard();
        paymentCard.setCardNumber("4111111111111111");
        paymentCard.setUser(user);

        paymentCardRequestDto = new PaymentCardRequestDto("4111111111111111", "TEST TEST",
                LocalDate.of(2030, 12, 12));
    }

    @Test
    public void createPaymentCard_shouldThrow_whenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentCardService.createPaymentCard(userId, paymentCardRequestDto))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    public void createPaymentCard_shouldThrow_whenUserAlreadyHasFiveCards() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(paymentCardRepository.countCardsByUserId(userId)).thenReturn(5L);

        assertThatThrownBy(() -> paymentCardService.createPaymentCard(userId, paymentCardRequestDto))
                .isInstanceOf(CardLimitException.class);
    }

    @Test
    public void createPaymentCard_shouldCreateCard_whenUserHasLessThanFiveCards() {
        PaymentCardResponseDto expected = new PaymentCardResponseDto(cardId,
                "**** **** **** 1111", "TEST TEST",
                LocalDate.of(2030, 12, 12), true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(paymentCardRepository.countCardsByUserId(userId)).thenReturn(4L);
        when(paymentCardMapper.toEntity(user, paymentCardRequestDto)).thenReturn(paymentCard);
        when(paymentCardMapper.toDto(paymentCard)).thenReturn(expected);

        PaymentCardResponseDto actual = paymentCardService.createPaymentCard(userId, paymentCardRequestDto);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void getPaymentCardById_shouldThrow_whenNoCardFound() {
        when(paymentCardRepository.findById(cardId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> paymentCardService.getPaymentCardById(cardId))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    public void getPaymentCardById_shouldGetPaymentCardById_whenCardFound() {
        PaymentCardResponseDto expected = new PaymentCardResponseDto(cardId,
                "**** **** **** 1111", "TEST TEST", LocalDate.of(2030, 12, 12), true);
        when(paymentCardRepository.findById(cardId)).thenReturn(Optional.of(paymentCard));
        when(paymentCardMapper.toDto(paymentCard)).thenReturn(expected);

        PaymentCardResponseDto actual = paymentCardService.getPaymentCardById(cardId);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void getAllPaymentCardsByUserId_shouldThrow_whenNoUserFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentCardService.getAllPaymentCardsByUserId(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    public void getAllPaymentCardsByUserId_shouldReturnEmptyList_whenUserHasNoCards() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(paymentCardRepository.findAllByUserId(userId)).thenReturn(List.of());

        assertThat(paymentCardService.getAllPaymentCardsByUserId(userId)).isEmpty();
    }

    @Test
    public void getAllPaymentCardsByUserId_shouldReturnCards_whenUserExists() {
        PaymentCardResponseDto expected = new PaymentCardResponseDto(cardId,
                "**** **** **** 1111", "TEST TEST", LocalDate.of(2030, 12, 12), true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(paymentCardRepository.findAllByUserId(userId)).thenReturn(List.of(paymentCard));
        when(paymentCardMapper.toDto(paymentCard)).thenReturn(expected);

        List<PaymentCardResponseDto> actual = paymentCardService.getAllPaymentCardsByUserId(userId);

        assertThat(actual).hasSize(1);
        assertThat(actual.get(0)).isEqualTo(expected);
    }

    @Test
    public void updatePaymentCard_shouldEvictUserCache(){
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache(CacheConfig.USERS_CACHE)).thenReturn(cache);
        when(paymentCardRepository.findById(cardId)).thenReturn(Optional.of(paymentCard));

        paymentCardService.updatePaymentCard(cardId, paymentCardRequestDto);
        verify(cache).evict(userId);
    }

    @Test
    public void setActivePaymentCard_shouldEvictUserCache() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache(CacheConfig.USERS_CACHE)).thenReturn(cache);
        when(paymentCardRepository.findUserIdByCardId(cardId)).thenReturn(Optional.of(userId));

        paymentCardService.setActivePaymentCard(cardId, false);
        verify(cache).evict(userId);
    }

    @Test
    public void setActivePaymentCard_shouldThrow_whenCardNotFound() {
        when(paymentCardRepository.findUserIdByCardId(cardId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentCardService.setActivePaymentCard(cardId, false))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    public void deletePaymentCardById_shouldEvictUserCache() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache(CacheConfig.USERS_CACHE)).thenReturn(cache);
        when(paymentCardRepository.findById(cardId)).thenReturn(Optional.of(paymentCard));
        paymentCardService.deletePaymentCardById(cardId);
        verify(cache).evict(userId);
    }

    @Test
    public void deletePaymentCardById_shouldThrow_whenCardNotFound() {
        when(paymentCardRepository.findById(cardId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentCardService.deletePaymentCardById(cardId))
                .isInstanceOf(CardNotFoundException.class);
    }
}
