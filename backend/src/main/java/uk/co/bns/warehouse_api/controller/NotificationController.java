package uk.co.bns.warehouse_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uk.co.bns.warehouse_api.dto.NotificationResponse;
import uk.co.bns.warehouse_api.dto.NotificationUnreadCount;
import uk.co.bns.warehouse_api.service.NotificationService;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** The bell's dropdown contents - most recent 30, newest first. */
    @GetMapping
    public List<NotificationResponse> list() {
        return notificationService.listRecent();
    }

    /** Polled every 30s to drive the bell's badge number. */
    @GetMapping("/unread-count")
    public NotificationUnreadCount unreadCount() {
        return new NotificationUnreadCount(notificationService.unreadCount());
    }

    /** Called the moment the bell is opened - clears the badge for everything currently unread. */
    @PostMapping("/mark-all-read")
    public void markAllRead() {
        notificationService.markAllRead();
    }
}
