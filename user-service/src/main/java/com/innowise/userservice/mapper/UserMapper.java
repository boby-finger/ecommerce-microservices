package com.innowise.userservice.mapper;


import com.innowise.userservice.dto.UserRequestDto;
import com.innowise.userservice.dto.UserResponseDto;
import com.innowise.userservice.dto.UserWithCardsResponseDto;
import com.innowise.userservice.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {
    private final PaymentCardMapper paymentCardMapper;

    public User toEntity(UserRequestDto userRequestDto) {
        User user = new User();
        user.setName(userRequestDto.name());
        user.setSurname(userRequestDto.surname());
        user.setEmail(userRequestDto.email());
        user.setBirthDate(userRequestDto.birthDate());
        user.setActive(true);
        return user;
    }

    public UserResponseDto toDto(User user) {
        return new UserResponseDto(
                user.getId(), user.getEmail(), user.getName(),  user.getSurname(),
                user.getBirthDate(), user.getActive()
        );
    }

    public UserWithCardsResponseDto toDtoWithCards(User user) {
        return new UserWithCardsResponseDto(user.getId(), user.getEmail(),
                user.getName(), user.getSurname(), user.getBirthDate(), user.getActive(),
                user.getCards().stream().map(paymentCardMapper::toDto).toList());
    }

    public void updateUser(User user, UserRequestDto userRequestDto) {
        user.setName(userRequestDto.name());
        user.setSurname(userRequestDto.surname());
        user.setEmail(userRequestDto.email());
        user.setBirthDate(userRequestDto.birthDate());
    }
}
