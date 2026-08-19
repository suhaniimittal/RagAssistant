package com.calfus.ragassistant.service;

import com.calfus.ragassistant.dto.UserProfileResponse;
import com.calfus.ragassistant.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<UserProfileResponse> getProfile(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> new UserProfileResponse(user.getId(), user.getIdentifier()));
    }
}
