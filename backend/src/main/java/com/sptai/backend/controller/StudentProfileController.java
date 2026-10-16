package com.sptai.backend.controller;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sptai.backend.entity.StudentProfile;
import com.sptai.backend.entity.User;
import com.sptai.backend.repository.ApplicationRepository;
import com.sptai.backend.service.StudentProfileService;
import com.sptai.backend.service.UserService;

@RestController
@RequestMapping("/api/student-profile")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*", "null"}, allowCredentials = "true")
public class StudentProfileController {

    private final StudentProfileService studentProfileService;
    private final UserService userService;
        private final ApplicationRepository applicationRepository;

    public StudentProfileController(
            StudentProfileService studentProfileService,
            UserService userService,
            ApplicationRepository applicationRepository) {

        this.studentProfileService = studentProfileService;
        this.userService = userService;
        this.applicationRepository = applicationRepository;
    }

    // ===============================
    // GET STUDENT PROFILE
    // ===============================

    @GetMapping("/{userId}")
        public ResponseEntity<?> getProfile(
                        @PathVariable Long userId,
                        HttpServletRequest request) {

                Optional<ResponseEntity<Map<String, String>>> authorizationFailure =
                                authorizeProfileRead(userId, request);

                if (authorizationFailure.isPresent()) {
                        return authorizationFailure.get();
                }

        Optional<StudentProfile> profile =
                studentProfileService.findByUserId(userId);

        if (profile.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(profile.get());
    }


    // ===============================
    // CREATE STUDENT PROFILE
    // ===============================

    @PostMapping("/{userId}")
    public ResponseEntity<?> createProfile(
            @PathVariable Long userId,
            @RequestBody StudentProfile profile,
            HttpServletRequest request) {

        Optional<User> authenticatedUser =
                getAuthenticatedUser(request);

        if (authenticatedUser.isEmpty()) {
            return authenticationRequired();
        }

        User user = authenticatedUser.get();

        if (!isStudentOwner(userId, user)) {
            return profileAccessDenied();
        }

        User requestedUser = profile.getUser();

        if (requestedUser == null || requestedUser.getEmail() == null) {
            return ResponseEntity.badRequest().body("User not found");
        }

        if (!user.getEmail().equalsIgnoreCase(requestedUser.getEmail()) ||
                (requestedUser.getId() != null &&
                        !Objects.equals(user.getId(), requestedUser.getId()))) {
            return profileAccessDenied();
        }

        profile.setId(null);
        profile.setUser(user);

        StudentProfile savedProfile =
                studentProfileService.saveProfile(profile);

        return ResponseEntity.ok(savedProfile);
    }


    // ===============================
    // UPDATE STUDENT PROFILE
    // ===============================

    @PutMapping("/{userId}")
    public ResponseEntity<?> updateProfile(
            @PathVariable Long userId,
            @RequestBody StudentProfile updatedProfile,
            HttpServletRequest request) {

        Optional<User> authenticatedUser =
                getAuthenticatedUser(request);

        if (authenticatedUser.isEmpty()) {
            return authenticationRequired();
        }

        if (!isStudentOwner(userId, authenticatedUser.get())) {
            return profileAccessDenied();
        }

        Optional<StudentProfile> existingProfile =
                studentProfileService.findByUserId(userId);

        if (existingProfile.isEmpty()) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        StudentProfile profile = existingProfile.get();

        profile.setPhone(updatedProfile.getPhone());
        profile.setDepartment(updatedProfile.getDepartment());
        profile.setCollege(updatedProfile.getCollege());
        profile.setCgpa(updatedProfile.getCgpa());
        profile.setSkills(updatedProfile.getSkills());
        profile.setGraduationYear(updatedProfile.getGraduationYear());

        StudentProfile savedProfile =
                studentProfileService.saveProfile(profile);

        return ResponseEntity.ok(savedProfile);
    }

    private Optional<ResponseEntity<Map<String, String>>> authorizeProfileRead(
            Long userId,
            HttpServletRequest request) {

        Optional<User> authenticatedUser =
                getAuthenticatedUser(request);

        if (authenticatedUser.isEmpty()) {
            return Optional.of(authenticationRequired());
        }

        User user = authenticatedUser.get();

        if (isStudentOwner(userId, user)) {
            return Optional.empty();
        }

        if ("COMPANY".equalsIgnoreCase(user.getRole()) &&
                hasCompanyApplication(userId, user.getCompanyName())) {
            return Optional.empty();
        }

        return Optional.of(profileAccessDenied());
    }

    private boolean isStudentOwner(Long userId, User user) {
        return "STUDENT".equalsIgnoreCase(user.getRole()) &&
                Objects.equals(user.getId(), userId);
    }

    private Optional<User> getAuthenticatedUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return Optional.empty();
        }

        Object sessionUserId = session.getAttribute("userId");
        Object sessionRole = session.getAttribute("userRole");

        if (!(sessionUserId instanceof Number userId) ||
                !(sessionRole instanceof String role)) {
            return Optional.empty();
        }

        Optional<User> user =
                userService.findById(userId.longValue());

        if (user.isEmpty() || user.get().getRole() == null ||
                !role.equalsIgnoreCase(user.get().getRole())) {
            return Optional.empty();
        }

        return user;
    }

    private boolean hasCompanyApplication(Long studentId, String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return false;
        }

        return applicationRepository.findByStudentId(studentId)
                .stream()
                .map(application -> application.getJob())
                .filter(Objects::nonNull)
                .anyMatch(job ->
                        job.getCompanyName() != null &&
                                companyName.trim().equalsIgnoreCase(
                                        job.getCompanyName().trim()));
    }

    private ResponseEntity<Map<String, String>> authenticationRequired() {
        return profileError("Authentication required");
    }

    private ResponseEntity<Map<String, String>> profileAccessDenied() {
        return profileError("Profile access denied");
    }

    private ResponseEntity<Map<String, String>> profileError(String message) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", message));
    }
}