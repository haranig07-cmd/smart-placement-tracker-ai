
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.sptai.backend.OfficerAccessPolicy;
import com.sptai.backend.entity.User;
import com.sptai.backend.service.OfficerService;
import com.sptai.backend.service.UserService;

@RestController
@RequestMapping("/api/officer")
@CrossOrigin(
    originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*",
        "https://haranig07-cmd.github.io"
    },
    allowCredentials = "true"
)
public class OfficerController {

    private final OfficerService officerService;
    private final UserService userService;
    private final OfficerAccessPolicy officerAccessPolicy;
    private final BCryptPasswordEncoder passwordEncoder =
        new BCryptPasswordEncoder();
    public OfficerController(
            OfficerService officerService,
            UserService userService,
            OfficerAccessPolicy officerAccessPolicy) {

        this.officerService = officerService;
        this.userService = userService;
        this.officerAccessPolicy = officerAccessPolicy;
    }

    // OFFICER DASHBOARD
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(HttpServletRequest request) {
        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(officerService.getDashboardStats());
    }

    // STUDENTS
    @GetMapping("/students")
    public ResponseEntity<?> getStudents(HttpServletRequest request) {
        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(officerService.getStudents());
    }

    // COMPANIES
    @GetMapping("/companies")
    public ResponseEntity<?> getCompanies(HttpServletRequest request) {
        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(officerService.getCompanies());
    }

    @GetMapping("/companies/pending")
    public ResponseEntity<?> getPendingCompanies(
            HttpServletRequest request) {

        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(officerService.getPendingCompanies());
    }

    @PutMapping("/companies/{id}/approve")
    public ResponseEntity<?> approveCompany(
            @PathVariable Long id,
            HttpServletRequest request) {

        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return officerService.approveCompany(id);
    }

    // RECRUITMENT DRIVES
    @GetMapping("/drives")
    public ResponseEntity<?> getDrives(HttpServletRequest request) {
        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(officerService.getDrives());
    }

    // ELIGIBLE STUDENTS
    @GetMapping("/drives/{id}/eligible-students")
    public ResponseEntity<?> getEligibleStudentsForDrive(
            @PathVariable Long id,
            HttpServletRequest request) {

        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(
                officerService.getEligibleStudentsForDrive(id));
    }

    // REPORTS
    @GetMapping("/reports")
    public ResponseEntity<?> getReports(HttpServletRequest request) {
        if (!hasOfficerAccess(request)) {
            return officerAccessRequired();
        }

        return ResponseEntity.ok(officerService.getReports());
    }
    // SET OR UPDATE OFFICER PASSWORD
@PostMapping("/set-password")
public ResponseEntity<?> setPassword(
        @RequestBody Map<String, String> body,
        HttpServletRequest request) {

    if (!hasOfficerAccess(request)) {
        return officerAccessRequired();
    }

    HttpSession session = request.getSession(false);
    Long userId = Long.valueOf(
            session.getAttribute("userId").toString());

    String password = body.get("password");

    if (password == null || password.length() < 12) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                    "error",
                    "Password must contain at least 12 characters."
                ));
    }

    Optional<User> userOptional = userService.findById(userId);

    if (userOptional.isEmpty()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Officer account not found."));
    }

    User user = userOptional.get();
    user.setPassword(passwordEncoder.encode(password));
    userService.saveUser(user);

    return ResponseEntity.ok(
            Map.of("message",
                   "Password set successfully. You can now use email and password login."));
}
    // VERIFY THE SESSION AND THE REAL DATABASE ACCOUNT
    private boolean hasOfficerAccess(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null) {
            return false;
        }

        Object userIdAttribute = session.getAttribute("userId");
        Object sessionRole = session.getAttribute("userRole");
        Object sessionEmail = session.getAttribute("userEmail");

        if (userIdAttribute == null
                || sessionEmail == null
                || !"OFFICER".equalsIgnoreCase(
                        String.valueOf(sessionRole))) {
            return false;
        }

        try {
            Long userId = Long.valueOf(userIdAttribute.toString());

            Optional<User> userOptional = userService.findById(userId);

            if (userOptional.isEmpty()) {
                return false;
            }

            User user = userOptional.get();

            // Verify the actual database role.
            if (!"OFFICER".equalsIgnoreCase(user.getRole())) {
                return false;
            }

            // Verify the actual database email is allowlisted.
            if (!officerAccessPolicy.isAuthorized(user.getEmail())) {
                return false;
            }

            // Verify the session email matches the database account.
            return Objects.equals(
                    user.getEmail().trim().toLowerCase(
                            java.util.Locale.ROOT),
                    sessionEmail.toString().trim().toLowerCase(
                            java.util.Locale.ROOT));

        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private ResponseEntity<Map<String, String>> officerAccessRequired() {
    return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "Officer access required"));
}
}
