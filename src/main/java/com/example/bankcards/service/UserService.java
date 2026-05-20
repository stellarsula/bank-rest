package com.example.bankcards.service;

import com.example.bankcards.dto.PageResponse;
import com.example.bankcards.dto.UserResponse;
import org.springframework.data.domain.Pageable;

public interface UserService {
    PageResponse<UserResponse> getAllUsers(Pageable pageable);
    UserResponse getUserById(Long id);
    void deleteUser(Long id);
    UserResponse toggleUserEnabled(Long id);
    UserResponse getMyProfile(String username);
}