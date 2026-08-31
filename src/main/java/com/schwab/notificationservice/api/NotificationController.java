package com.schwab.notificationservice.api;

import com.schwab.notificationservice.domain.Notification;
import com.schwab.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> submit(@Valid @RequestBody NotificationRequest request) {
        NotificationResponse response = notificationService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationResponse> getStatus(@PathVariable String notificationId) {
        NotificationResponse response = notificationService.getStatus(notificationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/idempotency/{idempotencyKey}")
    public ResponseEntity<NotificationResponse> getByIdempotencyKey(@PathVariable String idempotencyKey) {
        Optional<Notification> notification = notificationService.getByIdempotencyKey(idempotencyKey);
        if (notification.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        NotificationResponse response = notificationService.getStatus(notification.get().getNotificationId());
        return ResponseEntity.ok(response);
    }
}
