package com.innowise.userservice.service;


import com.innowise.userservice.dto.PaymentCardResponseDto;
import com.innowise.userservice.dto.UserRequestDto;
import com.innowise.userservice.dto.UserResponseDto;
import com.innowise.userservice.dto.UserWithCardsResponseDto;
import com.innowise.userservice.exceptions.UserAlreadyExistsException;
import com.innowise.userservice.exceptions.UserNotFoundException;
import com.innowise.userservice.mapper.UserMapper;
import com.innowise.userservice.model.User;
import com.innowise.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserService userService;
    @Mock
    private UserMapper userMapper;


    private UUID userId;
    private User user;
    private UserRequestDto  userRequestDto;

    @BeforeEach
    public void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setName("test_name");
        user.setSurname("test_surname");
        user.setEmail("test_email");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setActive(true);

        userRequestDto = new UserRequestDto("dto_test_email@gmail.com", "dto_test_name",
                "dto_test_surname", LocalDate.of(1990, 1, 1));
    }

    @Test
    public void createUserTest_shouldSaveUserAndReturnDto_whenEmailIsFree() {
        UserResponseDto expected = new UserResponseDto(userId, "dto_test_email@gmail.com", "dto_test_name",
                "dto_test_surname", LocalDate.of(1990, 1, 1), true);

        when(userRepository.existsByEmail("dto_test_email@gmail.com")).thenReturn(false);
        when(userMapper.toEntity(userRequestDto)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(expected);

        UserResponseDto actual = userService.createUser(userRequestDto);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void createUserTest_shouldThrow_whenEmailIsTaken() {
        when(userRepository.existsByEmail("dto_test_email@gmail.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(userRequestDto))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    public void getUserWithCardsById_shouldThrow_whenUserNotFound(){
        when(userRepository.findByIdWithCards(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserWithCardsById(userId)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    public void getUserWithCardsById_shouldReturnUserWithCards(){
        List<PaymentCardResponseDto> cards = new ArrayList<>();
        UserWithCardsResponseDto expected = new UserWithCardsResponseDto(userId, "dto_test_email@gmail.com", "dto_test_name",
                "dto_test_surname", LocalDate.of(1990, 1, 1), true,
                cards);
        when(userRepository.findByIdWithCards(userId)).thenReturn(Optional.of(user));
        when(userMapper.toDtoWithCards(user)).thenReturn(expected);

        UserWithCardsResponseDto actual = userService.getUserWithCardsById(userId);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void getAllUsers_shouldReturnPageOfUsers() {
        UserResponseDto expected = new UserResponseDto(userId, "dto_test_email@example.com",
                "dto_test_name", "dto_test_surname", LocalDate.of(1990, 1, 1), true);
        Pageable pageable = PageRequest.of(0, 5);

        when(userRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(userMapper.toDto(user)).thenReturn(expected);

        Page<UserResponseDto> actual = userService.getAllUsers("dto_test_name", "dto_test_surname", pageable);

        assertThat(actual.getContent()).containsExactly(expected);
    }

    @Test
    public void updateUserTest_shouldThrow_whenNewEmailIsTaken() {
        UserRequestDto dtoWithTakenEmail = new UserRequestDto("taken@gmail.com", "dto_test_name",
                "dto_test_surname", LocalDate.of(1990, 1, 1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@gmail.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser(userId, dtoWithTakenEmail))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userMapper, never()).updateUser(user, userRequestDto);
    }

    @Test
    public void updateUserTest_shouldUpdateUser() {
        UserRequestDto normalEmail = new UserRequestDto("dto_test_email@gmail.com", "dto_test_name",
                "dto_test_surname", LocalDate.of(1990, 1, 1));
        UserResponseDto expected = new UserResponseDto(userId,"dto_test_email@example.com", "dto_test_name",
                "dto_test_surname", LocalDate.of(1990, 1, 1), true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("dto_test_email@gmail.com")).thenReturn(false);
        when(userMapper.toDto(user)).thenReturn(expected);

        UserResponseDto actual = userService.updateUser(userId, normalEmail);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void setActiveUser_shouldThrow_whenNoRowsUpdated() {
        when(userRepository.updateActiveStatusUserById(eq(userId), any(Instant.class), eq(false))).thenReturn(0);

        assertThatThrownBy(() -> userService.setActiveUser(userId, false))
                .isInstanceOf(UserNotFoundException.class);

    }

    @Test
    public void deleteUser_shouldThrow_whenNoUserFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.deleteUser(userId)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    public void deleteUser_shouldDelete_whenUserExists() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.deleteUser(userId);

        verify(userRepository).delete(user);
    }
}
