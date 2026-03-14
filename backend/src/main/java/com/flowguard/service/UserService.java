package com.flowguard.service;

import com.flowguard.domain.UserEntity;
import com.flowguard.dto.UserDto;
import com.flowguard.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class UserService {

    @Inject
    UserRepository userRepository;

    public UserDto getUserById(UUID userId) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("Utilisateur introuvable");
        }
        return UserDto.from(user);
    }
}
