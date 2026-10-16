package com.sptai.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sptai.backend.entity.Notification;
import com.sptai.backend.entity.User;
import com.sptai.backend.repository.NotificationRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(
            NotificationRepository notificationRepository) {

        this.notificationRepository = notificationRepository;
    }

    public Notification createNotification(
            User user,
            String message,
            String type,
            String link) {

        Notification notification =
                new Notification(user, message, type, link);

        return notificationRepository.save(notification);
    }

    public List<Notification> getNotifications(Long userId) {

        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getUnreadNotifications(Long userId) {

        return notificationRepository
                .findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(Long userId) {

        return notificationRepository
                .countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public boolean markAsRead(Long notificationId, Long userId) {

        return notificationRepository.findById(notificationId)
                .filter(notification ->
                        notification.getUser() != null
                        && notification.getUser().getId().equals(userId))
                .map(notification -> {

                    notification.setRead(true);
                    notificationRepository.save(notification);

                    return true;

                })
                .orElse(false);
    }

    @Transactional
    public int markAllAsRead(Long userId) {

        List<Notification> notifications =
                notificationRepository
                        .findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);

        for (Notification notification : notifications) {
            notification.setRead(true);
        }

        notificationRepository.saveAll(notifications);

        return notifications.size();
    }
}