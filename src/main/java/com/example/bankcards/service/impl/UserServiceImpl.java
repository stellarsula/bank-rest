package com.example.bankcards.service.impl;

import com.example.bankcards.dto.PageResponse;
import com.example.bankcards.dto.UserResponse;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.ResourceNotFoundException;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public PageResponse<UserResponse> getAllUsers(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable).map(this::toResponse));
    }

    @Override
    public UserResponse getUserById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        findOrThrow(id);
        userRepository.deleteById(id);
    }

    @Override
    @Transactional
    public UserResponse toggleUserEnabled(Long id) {
        User user = findOrThrow(id);
        user.setEnabled(!user.getEnabled());
        return toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse getMyProfile(String username) {
        return toResponse(userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден: " + username)));
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден: id=" + id));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId()).username(user.getUsername())
                .email(user.getEmail()).role(user.getRole())
                .enabled(user.getEnabled()).createdAt(user.getCreatedAt())
                .build();
    }
}