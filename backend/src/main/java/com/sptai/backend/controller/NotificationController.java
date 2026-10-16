package com.sptai.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.sptai.backend.entity.Notification;
import com.sptai.backend.entity.User;
import com.sptai.backend.service.NotificationService;
import com.sptai.backend.service.UserService;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(
        originPatterns = {
                "http://localhost:*",
                "http://127.0.0.1:*",
                "null"
        },
        allowCredentials = "true"
)
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(
            NotificationService notificationService,
            UserService userService) {

        this.notificationService = notificationService;
        this.userService = userService;
    }

    // Get all notifications for logged-in user
    @GetMapping
    public ResponseEntity<?> getNotifications(
            HttpSession session) {

        Optional<User> user = getAuthenticatedUser(session);

        if (user.isEmpty()) {
            return ResponseEntity
                    .status(403)
                    .body(Map.of(
                            "error",
                            "Authentication required"));
        }

        List<Notification> notifications =
                notificationService
                        .getNotifications(user.get().getId());

        return ResponseEntity.ok(notifications);
    }

    // Get unread notification count
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            HttpSession session) {

        Optional<User> user = getAuthenticatedUser(session);

        if (user.isEmpty()) {
            return ResponseEntity
                    .status(403)
                    .body(Map.of(
                            "error",
                            "Authentication required"));
        }

        long count =
                notificationService
                        .getUnreadCount(user.get().getId());

        return ResponseEntity.ok(
                Map.of("count", count));
    }

    // Mark one notification as read
    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(
            @PathVariable Long id,
            HttpSession session) {

        Optional<User> user = getAuthenticatedUser(session);

        if (user.isEmpty()) {
            return ResponseEntity
                    .status(403)
                    .body(Map.of(
                            "error",
                            "Authentication required"));
        }

        boolean updated =
                notificationService.markAsRead(
                        id,
                        user.get().getId());

        if (!updated) {
            return ResponseEntity
                    .status(404)
                    .body(Map.of(
                            "error",
                            "Notification not found"));
        }

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Notification marked as read"));
    }

    // Mark all notifications as read
    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(
            HttpSession session) {

        Optional<User> user = getAuthenticatedUser(session);

        if (user.isEmpty()) {
            return ResponseEntity
                    .status(403)
                    .body(Map.of(
                            "error",
                            "Authentication required"));
        }

        int count =
                notificationService
                        .markAllAsRead(user.get().getId());

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "All notifications marked as read",
                        "updated", count));
    }

    private Optional<User> getAuthenticatedUser(
            HttpSession session) {

        if (session == null) {
            return Optional.empty();
        }

        Object sessionUserId =
                session.getAttribute("userId");

        Object sessionRole =
                session.getAttribute("userRole");

        if (!(sessionUserId instanceof Number userId)
                || !(sessionRole instanceof String role)) {

            return Optional.empty();
        }

        Optional<User> user =
                userService.findById(userId.longValue());

        if (user.isEmpty()
                || user.get().getRole() == null
                || !role.equalsIgnoreCase(
                        user.get().getRole())) {

            return Optional.empty();
        }

        return user;
    }
}