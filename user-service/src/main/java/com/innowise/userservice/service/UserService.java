package com.innowise.userservice.service;

import com.innowise.userservice.dto.UserRequestDto;
import com.innowise.userservice.dto.UserResponseDto;
import com.innowise.userservice.dto.UserWithCardsResponseDto;
import com.innowise.userservice.exceptions.UserAlreadyExistsException;
import com.innowise.userservice.exceptions.UserNotFoundException;
import com.innowise.userservice.mapper.UserMapper;
import com.innowise.userservice.model.User;
import com.innowise.userservice.repository.UserRepository;
import com.innowise.userservice.spec.UserSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserResponseDto createUser(UserRequestDto userRequestDto) {
        if(userRepository.existsByEmail(userRequestDto.email())) {
            throw new UserAlreadyExistsException(userRequestDto.email());
        }
        User user = userRepository.save(userMapper.toEntity(userRequestDto));
        return userMapper.toDto(user);
    }

    public UserWithCardsResponseDto getUserWithCardsById(UUID id) {
        User user = userRepository.findByIdWithCards(id).orElseThrow(()
                -> new UserNotFoundException(id));
        return userMapper.toDtoWithCards(user);
    }

    public Page<UserResponseDto> getAllUsers(String name, String surname, Pageable pageable) {
        Specification<User> userSpecification = Specification.allOf(
                UserSpecifications.searchUserByName(name),
                UserSpecifications.searchUserBySurname(surname)
        );
        return userRepository.findAll(userSpecification, pageable).map(userMapper::toDto);
    }

    @Transactional
    public UserResponseDto updateUser(UUID id, UserRequestDto userRequestDto) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if(!user.getEmail().equals(userRequestDto.email()) && userRepository.existsByEmail(userRequestDto.email()))
            {throw new UserAlreadyExistsException(userRequestDto.email());}
        userMapper.updateUser(user, userRequestDto);
        return userMapper.toDto(user);
    }

    @Transactional
    public void setActiveUser(UUID id, boolean active) {
        int update = userRepository.updateActiveStatusUserById(id, Instant.now(), active);
        if(update==0) {
            throw new UserNotFoundException(id);
        }
    }

    @Transactional
    public void deleteUser(UUID id){
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        userRepository.delete(user);
    }
}
