package com.innowise.userservice.mapper;


import com.innowise.userservice.dto.UserRequestDto;
import com.innowise.userservice.dto.UserResponseDto;
import com.innowise.userservice.dto.UserWithCardsResponseDto;
import com.innowise.userservice.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = PaymentCardMapper.class) //не возвращает замаскированный номер карты,
// надо дать ссылку на класс в котором hidecardnumber
public interface UserMapper {

    @Mapping(target = "id",  ignore = true)
    @Mapping(target = "updatedAt",  ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "cards", ignore = true)
    @Mapping(target = "active", constant = "true")
    User toEntity(UserRequestDto dto);


    UserResponseDto toDto(User user);

    UserWithCardsResponseDto toDtoWithCards(User user);


    @Mapping(target = "id",  ignore = true)
    @Mapping(target = "updatedAt",  ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "cards", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateUser(@MappingTarget User user, UserRequestDto dto);
}
