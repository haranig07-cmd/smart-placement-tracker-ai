package com.sptai.backend.service;

import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sptai.backend.entity.User;
import com.sptai.backend.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public User registerUser(User user) {
    if (user.getPassword() == null || user.getPassword().isBlank()) {
        throw new IllegalArgumentException("Password is required");
    }

    user.setPassword(passwordEncoder.encode(user.getPassword()));

    return userRepository.save(user);
}

    public boolean verifyPassword(User user, String rawPassword) {

        if (user == null || user.getPassword() == null || rawPassword == null) {
            return false;
        }

        String storedHash = user.getPassword();

        // Standard BCrypt verification
        if (isBcryptHash(storedHash)) {
            return passwordEncoder.matches(rawPassword, storedHash);
        }

        // Backward compatibility for legacy plaintext passwords
        if (storedHash.equals(rawPassword)) {

            // Migrate legacy password to BCrypt
            user.setPassword(passwordEncoder.encode(rawPassword));
            userRepository.save(user);

            return true;
        }

        return false;
    }

    private boolean isBcryptHash(String password) {
        return password != null
                && (password.startsWith("$2a$")
                || password.startsWith("$2b$")
                || password.startsWith("$2y$"))
                && password.length() == 60;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByCompanyName(String companyName) {
        return userRepository.findByCompanyName(companyName);
    }

    public User save(User user) {
        return userRepository.save(user);
    }
    public boolean changePassword(
        User user,
        String currentPassword,
        String newPassword) {

    if (user == null ||
            currentPassword == null ||
            newPassword == null) {

        return false;
    }

    // Verify the current password
    if (!verifyPassword(user, currentPassword)) {
        return false;
    }

    // Prevent using the same password again
    if (verifyPassword(user, newPassword)) {
        return false;
    }

    // Hash and store the new password
    user.setPassword(passwordEncoder.encode(newPassword));

    userRepository.save(user);

    return true;
}
}