package com.roottrace.user;

import com.roottrace.common.exception.BadRequestException;
import com.roottrace.common.exception.ResourceNotFoundException;
import com.roottrace.user.dto.CreateUserRequest;
import com.roottrace.user.dto.UpdateUserRequest;
import com.roottrace.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.findByEmailAndNotDeleted(request.email()).isPresent()) {
            throw new BadRequestException("Email already in use");
        }

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName(),
                request.role()
        );

        user = userRepository.save(user);
        return UserMapper.toResponse(user);
    }

    public UserResponse getById(UUID id) {
        User user = userRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return UserMapper.toResponse(user);
    }

    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
                .filter(u -> !u.isDeleted())
                .map(UserMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = userRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        if (request.firstName() != null && !request.firstName().isBlank()) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            user.setLastName(request.lastName());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }

        user = userRepository.save(user);
        return UserMapper.toResponse(user);
    }

    @Transactional
    public void delete(UUID id) {
        User user = userRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }
}
