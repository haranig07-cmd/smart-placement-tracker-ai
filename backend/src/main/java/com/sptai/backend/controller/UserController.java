package com.sptai.backend.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import com.sptai.backend.service.PasswordResetService;
import com.sptai.backend.entity.PasswordResetToken;
import com.sptai.backend.repository.PasswordResetTokenRepository;
import java.time.LocalDateTime;

import com.sptai.backend.entity.StudentProfile;
import com.sptai.backend.entity.User;
import com.sptai.backend.service.StudentProfileService;
import com.sptai.backend.service.UserService;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(
    originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*",
        "https://haranig07-cmd.github.io"
    },
    allowCredentials = "true"
)
public class UserController {

    private final UserService userService;
private final StudentProfileService studentProfileService;
private final PasswordResetService passwordResetService;
private final PasswordResetTokenRepository passwordResetTokenRepository;

    
public UserController(
        UserService userService,
        StudentProfileService studentProfileService,
        PasswordResetService passwordResetService,
        PasswordResetTokenRepository passwordResetTokenRepository) {

    this.userService = userService;
    this.studentProfileService = studentProfileService;
    this.passwordResetService = passwordResetService;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {

        if ("OFFICER".equalsIgnoreCase(user.getRole())) {
            return officerRegistrationNotAllowed();
        }

        if (!"STUDENT".equalsIgnoreCase(user.getRole()) &&
                !"COMPANY".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Only STUDENT and COMPANY accounts can be registered"));
        }
        // Password security validation
String password = user.getPassword();

if (password == null ||
        password.length() < 8 ||
        !password.matches(".*[A-Z].*") ||
        !password.matches(".*[a-z].*") ||
        !password.matches(".*\\d.*") ||
        !password.matches(".*[^a-zA-Z0-9].*")) {

    return ResponseEntity
            .badRequest()
            .body(Map.of(
                    "error",
                    "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character"
            ));
}

        // Check whether email already exists
        Optional<User> existingUser =
                userService.findByEmail(user.getEmail());

        if (existingUser.isPresent()) {
            return ResponseEntity
                    .badRequest()
                    .body("Email already registered");
        }

        // Save the new user
        // Set account status based on role
if ("COMPANY".equalsIgnoreCase(user.getRole())) {
    user.setAccountStatus("PENDING_VERIFICATION");
} else {
    user.setAccountStatus("ACTIVE");
}

// Save the new user
User savedUser = userService.registerUser(user);

        // Automatically create a student profile
        if ("STUDENT".equalsIgnoreCase(savedUser.getRole())) {

            StudentProfile profile = new StudentProfile();

            profile.setUser(savedUser);
            profile.setPhone("");
            profile.setDepartment("");
            profile.setCollege("");
            profile.setCgpa("");
            profile.setSkills("");
            profile.setGraduationYear("");

            studentProfileService.saveProfile(profile);
        }

        return ResponseEntity.ok(savedUser);
    }

    @Value("${google.client.id:33547164691-qn54m0bmmtakm52huiq0i4uo9hge1bdt.apps.googleusercontent.com}")
    private String googleClientId;

    private final RestClient restClient = RestClient.create();

    @PostMapping("/login")
public ResponseEntity<?> login(
        @RequestBody User loginUser,
        HttpServletRequest request) {

    Optional<User> existingUser =
            userService.findByEmail(loginUser.getEmail());

    if (existingUser.isEmpty()) {
        return ResponseEntity
                .badRequest()
                .body("User not found");
    }

    User user = existingUser.get();

    if (!"ACTIVE".equalsIgnoreCase(user.getAccountStatus())) {
        return ResponseEntity
                .badRequest()
                .body("Your account is pending verification or has been restricted.");
    }

    if (!userService.verifyPassword(user, loginUser.getPassword())) {
        return ResponseEntity
                .badRequest()
                .body("Invalid password");
    }

    // Create/reuse authenticated session
    HttpSession session = request.getSession(true);

    session.setAttribute("userId", user.getId());
    session.setAttribute("userRole", user.getRole());
    session.setAttribute("companyName", user.getCompanyName());

    Map<String, Object> response = new HashMap<>();

    response.put("id", user.getId());
    response.put("name", user.getName());
    response.put("email", user.getEmail());
    response.put("role", user.getRole());
    response.put("companyName", user.getCompanyName());

    return ResponseEntity.ok(response);
}

@PostMapping("/forgot-password")
public ResponseEntity<?> forgotPassword(
        @RequestBody Map<String, String> request) {

    String email = request.get("email");

    if (email == null || email.isBlank()) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Email is required"));
    }

    String token = passwordResetService.createResetToken(
            email.trim().toLowerCase());

    if (token != null) {
        String resetLink =
    "http://127.0.0.1:5500/frontend/reset-password.html?token=" + token;

        // LOCAL DEVELOPMENT ONLY — never log reset tokens in production.
        System.out.println("Password reset link: " + resetLink);
    }

    // Same response whether the email exists or not.
    return ResponseEntity.ok(
            Map.of("message",
                    "If an account exists with that email, a reset link has been generated. For local testing, check the backend terminal."));
}
    @PostMapping("/change-password")
public ResponseEntity<?> changePassword(
        @RequestBody Map<String, String> request,
        HttpSession session) {

    Object userIdAttribute = session.getAttribute("userId");

    if (userIdAttribute == null) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "error",
                        "You must be logged in to change your password"
                ));
    }

    String currentPassword = request.get("currentPassword");
    String newPassword = request.get("newPassword");
    String confirmPassword = request.get("confirmPassword");

    if (currentPassword == null ||
            newPassword == null ||
            confirmPassword == null) {

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error",
                        "All password fields are required"
                ));
    }

    if (!Objects.equals(newPassword, confirmPassword)) {
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error",
                        "New password and confirmation password do not match"
                ));
    }

    // Strong password validation
    if (newPassword.length() < 8 ||
            !newPassword.matches(".*[A-Z].*") ||
            !newPassword.matches(".*[a-z].*") ||
            !newPassword.matches(".*\\d.*") ||
            !newPassword.matches(".*[^a-zA-Z0-9].*")) {

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error",
                        "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character"
                ));
    }

    Long userId;

    try {
        userId = Long.valueOf(userIdAttribute.toString());
    } catch (NumberFormatException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "error",
                        "Invalid session"
                ));
    }

    Optional<User> userOptional =
            userService.findById(userId);

    if (userOptional.isEmpty()) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "error",
                        "User account not found"
                ));
    }

    User user = userOptional.get();

    if (!userService.verifyPassword(user, currentPassword)) {
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error",
                        "Current password is incorrect"
                ));
    }

    if (userService.verifyPassword(user, newPassword)) {
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error",
                        "New password must be different from the current password"
                ));
    }
boolean changed = userService.changePassword(
        user,
        currentPassword,
        newPassword
);

if (!changed) {
    return ResponseEntity.badRequest()
            .body(Map.of(
                    "error",
                    "Unable to change password. Check your current password and choose a different new password."
            ));
}

return ResponseEntity.ok(
        Map.of(
                "message",
                "Password changed successfully"
        )
);
        }
        
@PostMapping("/reset-password")
public ResponseEntity<?> resetPassword(
        @RequestBody Map<String, String> request) {

    String token = request.get("token");
    String newPassword = request.get("newPassword");
    String confirmPassword = request.get("confirmPassword");

    if (token == null || token.isBlank() ||
            newPassword == null || confirmPassword == null) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "All fields are required"));
    }

    if (!Objects.equals(newPassword, confirmPassword)) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Passwords do not match"));
    }

    if (newPassword.length() < 8 ||
            !newPassword.matches(".*[A-Z].*") ||
            !newPassword.matches(".*[a-z].*") ||
            !newPassword.matches(".*\\d.*") ||
            !newPassword.matches(".*[^a-zA-Z0-9].*")) {
        return ResponseEntity.badRequest()
                .body(Map.of("error",
                        "Password must be at least 8 characters and contain uppercase, lowercase, number, and special character"));
    }

    String tokenHash = passwordResetService.hashToken(token);

    Optional<PasswordResetToken> tokenOptional =
            passwordResetTokenRepository.findByTokenHash(tokenHash);

    if (tokenOptional.isEmpty()) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid or expired reset link"));
    }

    PasswordResetToken resetToken = tokenOptional.get();

    if (resetToken.isUsed() ||
            resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid or expired reset link"));
    }

    User user = resetToken.getUser();
    user.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
            .encode(newPassword));

    userService.save(user);

    resetToken.setUsed(true);
    passwordResetTokenRepository.save(resetToken);

    return ResponseEntity.ok(
            Map.of("message", "Password reset successfully. You can now log in."));
}
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok("Logged out successfully");
    }

@PostMapping("/google-login")
public ResponseEntity<?> googleLogin(
        @RequestBody Map<String, String> request,
        HttpSession session) {

    String idToken = request.get("credential");
    String requestedRole = request.getOrDefault("role", "STUDENT");

    if (idToken == null || idToken.isBlank()) {
        return ResponseEntity.badRequest()
                .body("Google ID token credential is required.");
    }

    try {
        // Verify token server-side with Google OAuth API
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenInfo = restClient.get()
                .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken)
                .retrieve()
                .body(Map.class);

        if (tokenInfo == null || !tokenInfo.containsKey("email")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid Google ID token.");
        }

        String email = (String) tokenInfo.get("email");
        String name = (String) tokenInfo.getOrDefault("name", "Google User");
        String emailVerified =
                String.valueOf(tokenInfo.get("email_verified"));

        if (!"true".equalsIgnoreCase(emailVerified)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Google email is not verified.");
        }

        Optional<User> existingUser = userService.findByEmail(email);
        User user;

        if (existingUser.isPresent()) {

            user = existingUser.get();

            // Block Google login for accounts that are not active
            if (!"ACTIVE".equalsIgnoreCase(user.getAccountStatus())) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body("Your account is pending verification or has been restricted.");
            }

        } else {

            // Officer registration is completely blocked
            if ("OFFICER".equalsIgnoreCase(requestedRole)) {
                return officerRegistrationNotAllowed();
            }

            // Determine valid role
            String role = "STUDENT";

            if ("COMPANY".equalsIgnoreCase(requestedRole)) {
                role = "COMPANY";
            }

            user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setRole(role);
            user.setPassword(UUID.randomUUID().toString());

            // Set account status based on role
            if ("COMPANY".equalsIgnoreCase(role)) {
                user.setAccountStatus("PENDING_VERIFICATION");
            } else {
                user.setAccountStatus("ACTIVE");
            }

            user = userService.registerUser(user);

            if ("STUDENT".equalsIgnoreCase(role)) {
                StudentProfile profile = new StudentProfile();
                profile.setUser(user);
                profile.setPhone("");
                profile.setDepartment("");
                profile.setCollege("");
                profile.setCgpa("");
                profile.setSkills("");
                profile.setGraduationYear("");

                studentProfileService.saveProfile(profile);
            }

            // New Google company accounts must wait for approval
            if ("COMPANY".equalsIgnoreCase(role)) {
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(Map.of(
                                "message",
                                "Company account created successfully. Awaiting verification.",
                                "id",
                                user.getId(),
                                "name",
                                user.getName(),
                                "email",
                                user.getEmail(),
                                "role",
                                user.getRole(),
                                "companyName",
                                user.getCompanyName(),
                                "accountStatus",
                                user.getAccountStatus()
                        ));
            }
        }

        session.setAttribute("userId", user.getId());
        session.setAttribute("userRole", user.getRole());
        session.setAttribute("companyName", user.getCompanyName());

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("name", user.getName());
        response.put("email", user.getEmail());
        response.put("role", user.getRole());
        response.put("companyName", user.getCompanyName());
        response.put("accountStatus", user.getAccountStatus());

        return ResponseEntity.ok(response);

    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Google token verification failed.");
    }
}

    private ResponseEntity<Map<String, String>> officerRegistrationNotAllowed() {
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error",
                        "Officer accounts cannot be created through public registration"));
    }
}