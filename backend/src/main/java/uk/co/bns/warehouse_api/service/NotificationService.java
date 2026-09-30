package uk.co.bns.warehouse_api.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.bns.warehouse_api.dto.NotificationResponse;
import uk.co.bns.warehouse_api.entity.Notification;
import uk.co.bns.warehouse_api.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Backs the bell icon (top-right, Layout.tsx). Deliberately general-purpose -
 * GdmsChannelService is the only writer today (see notifyFailure there), but
 * the table/API here isn't GDMS-specific, so another feature (or a future
 * reminder system) can create notifications through the same `create()` call
 * without any rework here. Like the other write-only log services in this
 * codebase, a write failure here is swallowed rather than thrown - a missed
 * notification must never break the GDMS call (or whatever else) that
 * triggered it.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public void create(String type, String message, String link) {
        try {
            Notification n = new Notification();
            n.setType(type);
            n.setMessage(message);
            n.setLink(link);
            n.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(n);
        } catch (Exception e) {
            log.warn("Failed to write notification ({}): {}", type, e.getMessage());
        }
    }

    public long unreadCount() {
        return notificationRepository.countByReadAtIsNull();
    }

    public List<NotificationResponse> listRecent() {
        return notificationRepository.findTop30ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Marks every currently-unread notification as read, in one go - the
     * frontend calls this the moment the bell is opened (Dan's explicit
     * choice: the badge clears on open, not on visiting each individual
     * notification's target page).
     */
    @Transactional
    public void markAllRead() {
        List<Notification> unread = notificationRepository.findByReadAtIsNull();
        LocalDateTime now = LocalDateTime.now();
        for (Notification n : unread) {
            n.setReadAt(now);
        }
        notificationRepository.saveAll(unread);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.getLink(),
                n.getCreatedAt(), n.getReadAt() != null);
    }
}
