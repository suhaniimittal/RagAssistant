package com.calfus.ragassistant.service;

import com.calfus.ragassistant.dto.LoginRequest;
import com.calfus.ragassistant.dto.RegisterRequest;
import com.calfus.ragassistant.exception.AuthException;
import com.calfus.ragassistant.model.User;
import com.calfus.ragassistant.repository.UserRepository;
import com.calfus.ragassistant.security.JwtUtil;
import com.calfus.ragassistant.util.IdentifierValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * All the actual authentication business logic -- identifier
 * validation/normalization, duplicate checks, password hashing/verification,
 * and JWT issuance. AuthController just calls into this and translates the
 * result (or a thrown AuthException) into HTTP.
 */
@RequiredArgsConstructor
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public void register(RegisterRequest request) {
        String identifier = IdentifierValidator.normalize(request.getIdentifier());

        if (!IdentifierValidator.isValid(identifier)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, IdentifierValidator.validationMessage());
        }
        if (userRepository.existsByIdentifier(identifier)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "An account with this identifier already exists");
        }

        User user = new User();
        user.setIdentifier(identifier);
        // Never store the raw password -- BCrypt hash only.
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
    }

    /** Returns the signed JWT on success; AuthController puts it in the cookie. */
    public String login(LoginRequest request) {
        String identifier = IdentifierValidator.normalize(request.getIdentifier());
        User user = userRepository.findByIdentifier(identifier).orElse(null);

        // Same generic error whether the identifier doesn't exist or the
        // password is wrong -- avoids revealing which accounts exist.
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid identifier or password");
        }

        return jwtUtil.generateToken(user.getId());
    }
}
