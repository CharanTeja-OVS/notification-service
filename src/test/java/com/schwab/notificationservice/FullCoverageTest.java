package com.schwab.notificationservice;

import com.schwab.notificationservice.api.NotificationController;
import com.schwab.notificationservice.api.GlobalExceptionHandler;
import com.schwab.notificationservice.api.NotificationRequest;
import com.schwab.notificationservice.api.NotificationResponse;
import jakarta.validation.ConstraintViolationException;
import com.schwab.notificationservice.config.ChannelProperties;
import com.schwab.notificationservice.config.NotificationDeliveryConfig;
import com.schwab.notificationservice.config.NotificationDeliveryProperties;
import com.schwab.notificationservice.delivery.MessageNotificationPublisher;
import com.schwab.notificationservice.delivery.NotificationDeliveryPublisher;
import com.schwab.notificationservice.delivery.NotificationRateLimiter;
import com.schwab.notificationservice.delivery.ResilientNotificationSender;
import com.schwab.notificationservice.domain.AuditEvent;
import com.schwab.notificationservice.domain.AuditEventType;
import com.schwab.notificationservice.domain.ChannelType;
import com.schwab.notificationservice.domain.DeliveryAttempt;
import com.schwab.notificationservice.domain.DeliveryStatus;
import com.schwab.notificationservice.domain.FailureCategory;
import com.schwab.notificationservice.domain.Notification;
import com.schwab.notificationservice.domain.NotificationPriority;
import com.schwab.notificationservice.domain.NotificationRecipient;
import com.schwab.notificationservice.domain.NotificationSeverity;
import com.schwab.notificationservice.domain.NotificationStatus;
import com.schwab.notificationservice.domain.NotificationType;
import com.schwab.notificationservice.repository.AuditEventRepository;
import com.schwab.notificationservice.repository.DeliveryAttemptRepository;
import com.schwab.notificationservice.repository.NotificationRecipientRepository;
import com.schwab.notificationservice.repository.NotificationRepository;
import com.schwab.notificationservice.service.DeliveryProcessorService;
import com.schwab.notificationservice.service.NotificationRoutingService;
import com.schwab.notificationservice.service.NotificationScheduler;
import com.schwab.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FullCoverageTest {

    @Test
    void notificationRateLimiterCoversBranches() throws Exception {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new NotificationRateLimiter(-1, Duration.ofMinutes(1)));
        assertTrue(ex.getMessage().contains(">= 0"));

        NotificationRateLimiter limiter = new NotificationRateLimiter(1, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        NotificationRateLimiter zeroLimiter = new NotificationRateLimiter(0, Duration.ofMinutes(1));
        assertFalse(zeroLimiter.tryAcquire());

        NotificationRateLimiter windowResetLimiter = new NotificationRateLimiter(1, Duration.ofMinutes(1));
        Field field = NotificationRateLimiter.class.getDeclaredField("windowStart");
        field.setAccessible(true);
        Instant now = Instant.now();
        field.set(windowResetLimiter, now.minus(Duration.ofMinutes(2)));
        assertTrue(windowResetLimiter.tryAcquire());

        NotificationRateLimiter exactBoundaryLimiter = new NotificationRateLimiter(1, Duration.ofSeconds(30));
        Field exactField = NotificationRateLimiter.class.getDeclaredField("windowStart");
        exactField.setAccessible(true);
        exactField.set(exactBoundaryLimiter, Instant.now().minus(Duration.ofSeconds(30)));
        assertTrue(exactBoundaryLimiter.tryAcquire());
    }

    @Test
    void messagePublisherCoversBranches() {
        MessageNotificationPublisher defaultPublisher = new MessageNotificationPublisher(null);
        assertEquals("kafka", defaultPublisher.getBrokerType());

        MessageNotificationPublisher blankPublisher = new MessageNotificationPublisher("   ");
        assertEquals("kafka", blankPublisher.getBrokerType());

        MessageNotificationPublisher configured = new MessageNotificationPublisher("rabbitmq");
        assertEquals("rabbitmq", configured.getBrokerType());

        Notification notification = new Notification();
        notification.setNotificationId("n-1");
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setRecipientId("r-1");
        assertDoesNotThrow(() -> configured.publish(notification, recipient, ChannelType.EMAIL));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> configured.publish(null, null, null));
        assertTrue(ex.getMessage().contains("required"));
        assertThrows(IllegalArgumentException.class, () -> configured.publish(null, recipient, ChannelType.EMAIL));
        assertThrows(IllegalArgumentException.class, () -> configured.publish(notification, null, ChannelType.EMAIL));
        assertThrows(IllegalArgumentException.class, () -> configured.publish(notification, recipient, null));
        assertThrows(IllegalArgumentException.class, () -> configured.publish(notification, recipient, null));
    }

    @Test
    void resilientNotificationSenderCoversBranches() throws Exception {
        Notification notification = new Notification();
        notification.setNotificationId("n-2");
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setRecipientId("r-2");

        NotificationRateLimiter limiter = new NotificationRateLimiter(10, Duration.ofMinutes(1));
        NotificationDeliveryPublisher successPublisher = (n, r, c) -> { };
        ResilientNotificationSender sender = new ResilientNotificationSender(limiter, successPublisher, 2, 0);
        DeliveryAttempt attempt = sender.send(notification, recipient, ChannelType.EMAIL);
        assertEquals(DeliveryStatus.DELIVERED, attempt.getStatus());

        NotificationRateLimiter blockedLimiter = new NotificationRateLimiter(0, Duration.ofMinutes(1));
        RuntimeException rateEx = assertThrows(RuntimeException.class,
                () -> new ResilientNotificationSender(blockedLimiter, successPublisher, 2, 0)
                        .send(notification, recipient, ChannelType.SMS));
        assertTrue(rateEx.getMessage().contains("Rate limit"));

        NotificationDeliveryPublisher flakyPublisher = new NotificationDeliveryPublisher() {
            int attempts = 0;
            @Override
            public void publish(Notification n, NotificationRecipient r, ChannelType channel) {
                attempts++;
                if (attempts == 1) {
                    throw new IllegalStateException("transient");
                }
            }
        };
        ResilientNotificationSender retrySender = new ResilientNotificationSender(new NotificationRateLimiter(10, Duration.ofMinutes(1)), flakyPublisher, 3, 0);
        DeliveryAttempt retryAttempt = retrySender.send(notification, recipient, ChannelType.EMAIL);
        assertEquals(DeliveryStatus.DELIVERED, retryAttempt.getStatus());

        NotificationDeliveryPublisher alwaysFailingPublisher = (n, r, c) -> { throw new IllegalStateException("persistent"); };
        ResilientNotificationSender failSender = new ResilientNotificationSender(new NotificationRateLimiter(10, Duration.ofMinutes(1)), alwaysFailingPublisher, 2, 0);
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> failSender.send(notification, recipient, ChannelType.EMAIL));
        assertTrue(failure.getMessage().contains("Delivery failed after retries"));

        ResilientNotificationSender nullLimiterSender = new ResilientNotificationSender(null, successPublisher, 2, 0);
        DeliveryAttempt nullLimiterAttempt = nullLimiterSender.send(notification, recipient, ChannelType.SMS);
        assertEquals(DeliveryStatus.DELIVERED, nullLimiterAttempt.getStatus());

        ResilientNotificationSender queuedSender = new ResilientNotificationSender(new NotificationRateLimiter(10, Duration.ofMinutes(1)), successPublisher, 0, 0);
        DeliveryAttempt queuedAttempt = queuedSender.send(notification, recipient, ChannelType.SMS);
        assertEquals(DeliveryStatus.QUEUED, queuedAttempt.getStatus());

        AtomicReference<Throwable> interruptedRef = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                new ResilientNotificationSender(new NotificationRateLimiter(10, Duration.ofMinutes(1)), alwaysFailingPublisher, 2, 50)
                        .send(notification, recipient, ChannelType.SMS);
            } catch (Throwable th) {
                interruptedRef.set(th);
            }
        });
        t.start();
        Thread.sleep(30);
        t.interrupt();
        t.join();
        assertNotNull(interruptedRef.get());

        assertThrows(IllegalArgumentException.class, () -> sender.send(null, null, ChannelType.EMAIL));
        assertThrows(IllegalArgumentException.class, () -> sender.send(notification, null, ChannelType.EMAIL));
        assertThrows(IllegalArgumentException.class, () -> sender.send(notification, recipient, null));
        assertThrows(IllegalArgumentException.class, () -> sender.send(null, recipient, ChannelType.EMAIL));
    }

    @Test
    void routingServiceCoversBranches() throws Exception {
        NotificationRoutingService routing = new NotificationRoutingService(null);
        Notification notification = new Notification();
        notification.setSeverity(NotificationSeverity.CRITICAL);
        assertTrue(routing.determineChannels(notification).contains(ChannelType.EMAIL));

        notification.setRecipients(new ArrayList<>());
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setPreferredChannels(Set.of(ChannelType.EMAIL, ChannelType.SMS));
        notification.setRecipients(List.of(recipient));
        assertEquals(List.of(ChannelType.EMAIL, ChannelType.SMS), routing.determineChannels(notification));

        ChannelProperties config = new ChannelProperties();
        Map<String, ChannelProperties.ChannelConfig> channels = new HashMap<>();
        ChannelProperties.ChannelConfig email = new ChannelProperties.ChannelConfig();
        email.setEnabled(false);
        channels.put("email", email);
        ChannelProperties.ChannelConfig sms = new ChannelProperties.ChannelConfig();
        sms.setEnabled(true);
        channels.put("sms", sms);
        config.setChannels(channels);

        NotificationRoutingService filteredRouting = new NotificationRoutingService(config);
        Notification n = new Notification();
        n.setSeverity(NotificationSeverity.MEDIUM);
        NotificationRecipient r = new NotificationRecipient();
        r.setPreferredChannels(Set.of(ChannelType.EMAIL, ChannelType.SMS));
        n.setRecipients(List.of(r));
        List<ChannelType> selected = filteredRouting.determineChannels(n);
        assertTrue(selected.contains(ChannelType.SMS));
        assertFalse(selected.contains(ChannelType.EMAIL));

        ChannelProperties missingChannelConfig = new ChannelProperties();
        missingChannelConfig.setChannels(Map.of("sms", sms));
        NotificationRoutingService defaultEnabledRouting = new NotificationRoutingService(missingChannelConfig);
        Notification missingConfigNotification = new Notification();
        missingConfigNotification.setSeverity(NotificationSeverity.LOW);
        NotificationRecipient missingRecipient = new NotificationRecipient();
        missingRecipient.setPreferredChannels(Set.of(ChannelType.EMAIL, ChannelType.SMS));
        missingConfigNotification.setRecipients(List.of(missingRecipient));
        assertTrue(defaultEnabledRouting.determineChannels(missingConfigNotification).contains(ChannelType.EMAIL));

        Notification highSeverityNotification = new Notification();
        highSeverityNotification.setSeverity(NotificationSeverity.HIGH);
        assertEquals(List.of(ChannelType.EMAIL, ChannelType.SMS), new NotificationRoutingService(null).determineChannels(highSeverityNotification));

        var isEnabledMethod = NotificationRoutingService.class.getDeclaredMethod("isEnabled", ChannelType.class);
        isEnabledMethod.setAccessible(true);
        assertFalse((boolean) isEnabledMethod.invoke(new NotificationRoutingService(null), new Object[]{null}));
        assertTrue((boolean) isEnabledMethod.invoke(new NotificationRoutingService(null), ChannelType.EMAIL));

        Notification mediumSeverityNotification = new Notification();
        mediumSeverityNotification.setSeverity(NotificationSeverity.MEDIUM);
        assertEquals(List.of(ChannelType.EMAIL), new NotificationRoutingService(null).determineChannels(mediumSeverityNotification));

        Notification lowSeverityNotification = new Notification();
        lowSeverityNotification.setSeverity(NotificationSeverity.LOW);
        assertEquals(List.of(ChannelType.EMAIL), new NotificationRoutingService(null).determineChannels(lowSeverityNotification));

        Notification recipientsNullNotification = new Notification();
        recipientsNullNotification.setSeverity(NotificationSeverity.CRITICAL);
        recipientsNullNotification.setRecipients(null);
        assertEquals(List.of(ChannelType.EMAIL, ChannelType.SMS, ChannelType.PUSH), new NotificationRoutingService(null).determineChannels(recipientsNullNotification));

        NotificationRecipient nullPreferredRecipient = new NotificationRecipient();
        nullPreferredRecipient.setPreferredChannels(null);
        Notification notificationWithNullPreferredChannels = new Notification();
        notificationWithNullPreferredChannels.setRecipients(List.of(nullPreferredRecipient));
        notificationWithNullPreferredChannels.setSeverity(NotificationSeverity.HIGH);
        assertEquals(List.of(ChannelType.EMAIL, ChannelType.SMS), new NotificationRoutingService(null).determineChannels(notificationWithNullPreferredChannels));

        NotificationRecipient nullRecipient = null;
        Notification notificationWithNullRecipient = new Notification();
        List<NotificationRecipient> recipientsWithNull = new ArrayList<>();
        recipientsWithNull.add(nullRecipient);
        notificationWithNullRecipient.setRecipients(recipientsWithNull);
        notificationWithNullRecipient.setSeverity(NotificationSeverity.HIGH);
        assertEquals(List.of(ChannelType.EMAIL, ChannelType.SMS), new NotificationRoutingService(null).determineChannels(notificationWithNullRecipient));

        NotificationRecipient nullChannelRecipient = new NotificationRecipient();
        Set<ChannelType> channelSet = new HashSet<>();
        channelSet.add(null);
        channelSet.add(ChannelType.EMAIL);
        nullChannelRecipient.setPreferredChannels(channelSet);
        Notification notificationWithNullChannel = new Notification();
        notificationWithNullChannel.setRecipients(List.of(nullChannelRecipient));
        assertEquals(List.of(ChannelType.EMAIL), new NotificationRoutingService(new ChannelProperties()).determineChannels(notificationWithNullChannel));

        assertEquals(List.of(), new NotificationRoutingService(config).determineChannels(null));
    }

    @Test
    void notificationServiceCoversCrudAndSubmitFlows() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationRecipientRepository recipientRepository = mock(NotificationRecipientRepository.class);
        DeliveryAttemptRepository deliveryAttemptRepository = mock(DeliveryAttemptRepository.class);
        AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
        NotificationRoutingService routingService = mock(NotificationRoutingService.class);
        ResilientNotificationSender sender = mock(ResilientNotificationSender.class);

        DeliveryProcessorService processor = mock(DeliveryProcessorService.class);
        NotificationService service = new NotificationService(notificationRepository, recipientRepository, deliveryAttemptRepository, auditEventRepository, routingService, processor, sender);

        Notification existing = new Notification();
        existing.setNotificationId("n-existing");
        existing.setIdempotencyKey("dup");
        existing.setStatus(NotificationStatus.DELIVERED);
        when(notificationRepository.findByIdempotencyKey("dup")).thenReturn(Optional.of(existing));
        when(routingService.determineChannels(existing)).thenReturn(List.of(ChannelType.EMAIL));

        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class, () -> service.submit(notificationRequestWith("dup")));
        assertTrue(duplicate.getMessage().startsWith("Duplicate idempotency key"));

        NotificationRequest requestWithoutPreferredChannels = notificationRequestWith("new-key");
        requestWithoutPreferredChannels.getRecipients().get(0).setPreferredChannels(null);
        NotificationRequest requestWithEmptyPreferredChannels = notificationRequestWith("new-key-empty");
        requestWithEmptyPreferredChannels.getRecipients().get(0).setPreferredChannels(new ArrayList<>());
        NotificationRequest requestWithExplicitPreferredChannels = notificationRequestWith("new-key-explicit");
        requestWithExplicitPreferredChannels.getRecipients().get(0).setPreferredChannels(List.of(ChannelType.EMAIL, ChannelType.SMS));
        NotificationRequest requestWithEmptyRecipients = notificationRequestWith("new-key-empty-recipients");
        requestWithEmptyRecipients.setRecipients(new ArrayList<>());
        Notification newNotification = new Notification();
        newNotification.setNotificationId("n-req");
        newNotification.setIdempotencyKey("new-key");
        newNotification.setStatus(NotificationStatus.ROUTED);
        when(notificationRepository.findByIdempotencyKey("new-key")).thenReturn(Optional.empty());
        when(notificationRepository.findByIdempotencyKey("new-key-empty")).thenReturn(Optional.empty());
        when(notificationRepository.findByIdempotencyKey("new-key-explicit")).thenReturn(Optional.empty());
        when(notificationRepository.findByIdempotencyKey("new-key-empty-recipients")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setCreatedAt(Instant.now());
            return n;
        });
        when(routingService.determineChannels(any(Notification.class))).thenReturn(List.of(ChannelType.EMAIL));
        when(sender.send(any(Notification.class), any(NotificationRecipient.class), any(ChannelType.class)))
                .thenReturn(new DeliveryAttempt());

        NotificationResponse created = service.submit(requestWithoutPreferredChannels);
        NotificationResponse createdEmpty = service.submit(requestWithEmptyPreferredChannels);
        NotificationResponse createdExplicit = service.submit(requestWithExplicitPreferredChannels);
        NotificationResponse emptyRecipients = service.submit(requestWithEmptyRecipients);
        assertEquals("n-req", created.getNotificationId());
        assertEquals("n-req", createdEmpty.getNotificationId());
        assertEquals("n-req", createdExplicit.getNotificationId());
        assertEquals("n-req", emptyRecipients.getNotificationId());
        verify(recipientRepository, atLeastOnce()).saveAll(any());
        verify(auditEventRepository, atLeastOnce()).save(any(AuditEvent.class));

        when(notificationRepository.findByNotificationId("n-req")).thenReturn(Optional.of(newNotification));
        when(notificationRepository.findByIdempotencyKey("new-key")).thenReturn(Optional.of(newNotification));
        when(deliveryAttemptRepository.findByNotification(newNotification)).thenReturn(List.of(new DeliveryAttempt()));
        assertTrue(service.getByNotificationId("n-req").isPresent());
        assertTrue(service.getByIdempotencyKey("new-key").isPresent());
        assertNotNull(service.getStatus("n-req"));
        assertEquals(1, service.getDeliveryAttempts("n-req").size());
        assertThrows(IllegalArgumentException.class, () -> service.getStatus("missing"));
        assertThrows(IllegalArgumentException.class, () -> service.getDeliveryAttempts("missing"));
    }

    @Test
    void notificationServiceRejectsMissingInputsGeneratesBlankIdentifiersAndDispatchesUrgentWork() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationRecipientRepository recipientRepository = mock(NotificationRecipientRepository.class);
        DeliveryAttemptRepository attemptRepository = mock(DeliveryAttemptRepository.class);
        AuditEventRepository auditRepository = mock(AuditEventRepository.class);
        NotificationRoutingService routingService = mock(NotificationRoutingService.class);
        DeliveryProcessorService processor = mock(DeliveryProcessorService.class);
        ResilientNotificationSender sender = mock(ResilientNotificationSender.class);
        NotificationService service = new NotificationService(notificationRepository, recipientRepository, attemptRepository,
                auditRepository, routingService, processor, sender);

        assertThrows(IllegalArgumentException.class, () -> service.submit(null));
        NotificationRequest missingRecipients = notificationRequestWith("missing-recipients");
        missingRecipients.setRecipients(null);
        assertThrows(IllegalArgumentException.class, () -> service.submit(missingRecipients));

        when(notificationRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(routingService.determineChannels(any(Notification.class))).thenReturn(List.of(ChannelType.EMAIL));

        NotificationRequest generatedIds = notificationRequestWith(" ");
        generatedIds.setNotificationId(null);
        generatedIds.setSeverity(NotificationSeverity.HIGH);
        NotificationResponse highResponse = service.submit(generatedIds);
        assertNotNull(highResponse.getNotificationId());
        assertFalse(highResponse.getNotificationId().isBlank());
        assertNotNull(highResponse.getIdempotencyKey());
        assertFalse(highResponse.getIdempotencyKey().isBlank());
        verify(processor).process(any(Notification.class));

        NotificationRequest critical = notificationRequestWith("critical-key");
        critical.setSeverity(NotificationSeverity.CRITICAL);
        service.submit(critical);
        verify(processor, org.mockito.Mockito.times(2)).process(any(Notification.class));

        NotificationRequest nullSeverity = notificationRequestWith("null-severity-key");
        nullSeverity.setSeverity(null);
        service.submit(nullSeverity);
        verify(processor, org.mockito.Mockito.times(2)).process(any(Notification.class));
    }

    @Test
    void controllerCoversEndpoints() {
        NotificationService notificationService = mock(NotificationService.class);
        NotificationController controller = new NotificationController(notificationService);

        NotificationRequest request = notificationRequestWith("ctrl-key");
        NotificationResponse response = new NotificationResponse("n-ctrl", NotificationStatus.ACCEPTED, "ctrl-key", Instant.now(), List.of("EMAIL"));
        when(notificationService.submit(request)).thenReturn(response);

        ResponseEntity<NotificationResponse> created = controller.submit(request);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());

        when(notificationService.getStatus("n-ctrl")).thenReturn(response);
        ResponseEntity<NotificationResponse> status = controller.getStatus("n-ctrl");
        assertEquals(HttpStatus.OK, status.getStatusCode());

        when(notificationService.getByIdempotencyKey("ctrl-key")).thenReturn(Optional.of(new Notification()));
        when(notificationService.getStatus("n-ctrl")).thenReturn(response);
        ResponseEntity<NotificationResponse> byKey = controller.getByIdempotencyKey("ctrl-key");
        assertEquals(HttpStatus.OK, byKey.getStatusCode());

        when(notificationService.getByIdempotencyKey("missing")).thenReturn(Optional.empty());
        ResponseEntity<NotificationResponse> missing = controller.getByIdempotencyKey("missing");
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
    }

    @Test
    void deliveryProcessorServiceCoversLooping() {
        DeliveryAttemptRepository deliveryAttemptRepository = mock(DeliveryAttemptRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        DeliveryProcessorService processor = new DeliveryProcessorService(deliveryAttemptRepository, notificationRepository, new NotificationRoutingService(null));

        Notification notification = new Notification();
        notification.setNotificationId("n-proc");
        notification.setSeverity(NotificationSeverity.HIGH);
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setRecipientId("r-proc");
        recipient.setPreferredChannels(Set.of(ChannelType.EMAIL));
        notification.setRecipients(List.of(recipient));

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        processor.process(notification);
        verify(deliveryAttemptRepository, atLeastOnce()).save(any(DeliveryAttempt.class));
        assertEquals(NotificationStatus.DELIVERED, notification.getStatus());

        notification.setSeverity(NotificationSeverity.CRITICAL);
        processor.process(notification);
        assertEquals(NotificationStatus.DELIVERED, notification.getStatus());
    }

    @Test
    void deliveryProcessorQueuesDeferredWorkAndRecordsProcessingFailures() {
        DeliveryAttemptRepository attemptRepository = mock(DeliveryAttemptRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationRoutingService routingService = mock(NotificationRoutingService.class);
        DeliveryProcessorService processor = new DeliveryProcessorService(attemptRepository, notificationRepository, routingService);

        Notification deferred = new Notification();
        deferred.setId(UUID.randomUUID());
        deferred.setNotificationId("deferred");
        deferred.setSeverity(NotificationSeverity.LOW);
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setRecipientId("deferred-recipient");
        deferred.setRecipients(List.of(recipient));
        when(notificationRepository.findById(deferred.getId())).thenReturn(Optional.of(deferred));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(routingService.determineChannels(deferred)).thenReturn(List.of(ChannelType.EMAIL, ChannelType.SMS));

        processor.process(deferred);

        assertEquals(NotificationStatus.QUEUED, deferred.getStatus());
        verify(attemptRepository).saveAll(any());

        Notification failed = new Notification();
        failed.setId(UUID.randomUUID());
        failed.setNotificationId("failed");
        failed.setSeverity(NotificationSeverity.MEDIUM);
        failed.setRecipients(List.of(recipient));
        when(notificationRepository.findById(failed.getId())).thenReturn(Optional.of(failed));
        when(routingService.determineChannels(failed)).thenThrow(new IllegalStateException("delivery unavailable"));

        assertThrows(IllegalStateException.class, () -> processor.process(failed));
        assertEquals(NotificationStatus.FAILED, failed.getStatus());
        verify(notificationRepository, atLeastOnce()).save(failed);
    }

    @Test
    void deliveryProcessorUsesBusinessIdFallbackAndHandlesNoRecipients() {
        DeliveryAttemptRepository attemptRepository = mock(DeliveryAttemptRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationRoutingService routingService = mock(NotificationRoutingService.class);
        DeliveryProcessorService processor = new DeliveryProcessorService(attemptRepository, notificationRepository, routingService);

        Notification submitted = new Notification();
        submitted.setId(UUID.randomUUID());
        submitted.setNotificationId("business-id");
        submitted.setSeverity(NotificationSeverity.LOW);
        submitted.setRecipients(null);
        Notification managed = new Notification();
        managed.setNotificationId("business-id");
        managed.setSeverity(NotificationSeverity.LOW);
        managed.setRecipients(new ArrayList<>());
        when(notificationRepository.findById(submitted.getId())).thenReturn(Optional.empty());
        when(notificationRepository.findByNotificationId("business-id")).thenReturn(Optional.of(managed));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(routingService.determineChannels(managed)).thenReturn(List.of(ChannelType.EMAIL));

        processor.process(submitted);

        assertEquals(NotificationStatus.QUEUED, managed.getStatus());
        assertNotNull(managed.getRecipients());
        assertTrue(managed.getRecipients().isEmpty());
        verify(attemptRepository, never()).saveAll(any());
        processor.process(null);
    }

    @Test
    void routingUsesLowercaseSeverityOverridesAndDefaultChannels() {
        ChannelProperties properties = new ChannelProperties();
        ChannelProperties.RoutingConfig routing = new ChannelProperties.RoutingConfig();
        Map<String, List<String>> severityOverrides = new HashMap<>();
        severityOverrides.put("critical", new ArrayList<>(java.util.Arrays.asList(" SMS ", null, " ")));
        routing.setSeverityChannelOverrides(severityOverrides);
        routing.setDefaultChannels(List.of(" push "));
        properties.setRouting(routing);

        Notification critical = new Notification();
        critical.setSeverity(NotificationSeverity.CRITICAL);
        Notification low = new Notification();
        low.setSeverity(NotificationSeverity.LOW);

        NotificationRoutingService routingService = new NotificationRoutingService(properties);
        assertEquals(List.of(ChannelType.SMS), routingService.determineChannels(critical));
        assertEquals(List.of(ChannelType.PUSH), routingService.determineChannels(low));
    }

    @Test
    void schedulerSkipsEmptyAndNotDueRecoveryWork() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        DeliveryProcessorService processorService = mock(DeliveryProcessorService.class);
        NotificationScheduler scheduler = new NotificationScheduler(notificationRepository, processorService);

        when(notificationRepository.findByStatusIn(any())).thenReturn(List.of());
        scheduler.processPendingNotifications();
        scheduler.processQueuedNotifications();
        scheduler.retryFailedNotifications();
        verify(processorService, never()).process(any());

        Notification futureFailure = new Notification();
        futureFailure.setNotificationId("future-failure");
        futureFailure.setSeverity(NotificationSeverity.HIGH);
        futureFailure.setScheduledAt(Instant.now().plusSeconds(60));
        when(notificationRepository.findByStatusIn(any())).thenReturn(List.of(futureFailure));
        scheduler.retryFailedNotifications();
        verify(processorService, never()).process(futureFailure);
    }

    @Test
    void schedulerFinalizesEverySeverityAndRetriesDueProcessingWork() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        DeliveryProcessorService processorService = mock(DeliveryProcessorService.class);
        NotificationScheduler scheduler = new NotificationScheduler(notificationRepository, processorService);

        List<Notification> queued = new ArrayList<>();
        for (NotificationSeverity severity : NotificationSeverity.values()) {
            Notification notification = new Notification();
            notification.setNotificationId("queued-" + severity.name());
            notification.setSeverity(severity);
            notification.setStatus(NotificationStatus.QUEUED);
            queued.add(notification);
        }
        Notification noSeverity = new Notification();
        noSeverity.setNotificationId("queued-none");
        queued.add(noSeverity);
        when(notificationRepository.findByStatusIn(any())).thenReturn(queued);

        scheduler.processQueuedNotifications();

        assertTrue(queued.stream().allMatch(notification -> notification.getStatus() == NotificationStatus.DELIVERED));
        verify(notificationRepository, org.mockito.Mockito.times(queued.size())).save(any(Notification.class));

        Notification processing = new Notification();
        processing.setNotificationId("stuck");
        processing.setSeverity(NotificationSeverity.HIGH);
        processing.setStatus(NotificationStatus.PROCESSING);
        when(notificationRepository.findByStatusIn(any())).thenReturn(List.of(processing));
        scheduler.retryFailedNotifications();
        assertEquals(NotificationStatus.ROUTED, processing.getStatus());
        verify(processorService).process(processing);
    }

    @Test
    void globalExceptionHandlerMapsBusinessFailuresToExpectedStatuses() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertEquals(HttpStatus.NOT_FOUND, handler.handleIllegalArgument(
                new IllegalArgumentException("Notification not found: missing")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, handler.handleIllegalArgument(
                new IllegalArgumentException("Duplicate idempotency key: repeated")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, handler.handleIllegalArgument(
                new IllegalArgumentException("Invalid request")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, handler.handleIllegalArgument(
            new IllegalArgumentException()).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, handler.handleConstraintViolation(
                new ConstraintViolationException("Invalid input", Set.of())).getStatusCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, handler.handleUnexpected(new RuntimeException("internal")).getStatusCode());
    }

    @Test
    void schedulerProcessesUrgentNotificationsFirst() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        DeliveryProcessorService processorService = mock(DeliveryProcessorService.class);
        NotificationScheduler scheduler = new NotificationScheduler(notificationRepository, processorService);

        Notification lowPriority = new Notification();
        lowPriority.setNotificationId("low");
        lowPriority.setSeverity(NotificationSeverity.LOW);
        lowPriority.setStatus(NotificationStatus.ACCEPTED);

        Notification critical = new Notification();
        critical.setNotificationId("critical");
        critical.setSeverity(NotificationSeverity.CRITICAL);
        critical.setStatus(NotificationStatus.ACCEPTED);

        when(notificationRepository.findByStatusIn(any())).thenReturn(List.of(lowPriority, critical));

        scheduler.processPendingNotifications();

        verify(processorService).process(lowPriority);
        verify(processorService, never()).process(critical);
        assertEquals(NotificationStatus.ACCEPTED, lowPriority.getStatus());
        assertEquals(NotificationStatus.ACCEPTED, critical.getStatus());
    }

    @Test
    void schedulerDoesNotProcessLowPriorityNotificationsBeforeDueTime() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        DeliveryProcessorService processorService = mock(DeliveryProcessorService.class);
        NotificationScheduler scheduler = new NotificationScheduler(notificationRepository, processorService);

        Notification lowPriority = new Notification();
        lowPriority.setNotificationId("low-delayed");
        lowPriority.setSeverity(NotificationSeverity.LOW);
        lowPriority.setStatus(NotificationStatus.ACCEPTED);
        lowPriority.setScheduledAt(Instant.now().plusSeconds(120));

        when(notificationRepository.findByStatusIn(any())).thenReturn(List.of(lowPriority));

        scheduler.processPendingNotifications();

        verify(processorService, never()).process(lowPriority);
        assertEquals(NotificationStatus.ACCEPTED, lowPriority.getStatus());
    }

    @Test
    void schedulerFinalizesQueuedNotifications() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        DeliveryProcessorService processorService = mock(DeliveryProcessorService.class);
        NotificationScheduler scheduler = new NotificationScheduler(notificationRepository, processorService);

        Notification queued = new Notification();
        queued.setNotificationId("queued");
        queued.setSeverity(NotificationSeverity.MEDIUM);
        queued.setStatus(NotificationStatus.QUEUED);
        queued.setScheduledAt(Instant.now().minusSeconds(30));

        when(notificationRepository.findByStatusIn(any())).thenReturn(List.of(queued));

        scheduler.processQueuedNotifications();

        assertEquals(NotificationStatus.DELIVERED, queued.getStatus());
        verify(notificationRepository).save(queued);
    }

    @Test
    void schedulerRetriesFailedNotifications() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        DeliveryProcessorService processorService = mock(DeliveryProcessorService.class);
        NotificationScheduler scheduler = new NotificationScheduler(notificationRepository, processorService);

        Notification failed = new Notification();
        failed.setNotificationId("failed");
        failed.setSeverity(NotificationSeverity.MEDIUM);
        failed.setStatus(NotificationStatus.FAILED);

        when(notificationRepository.findByStatusIn(any())).thenReturn(List.of(failed));

        scheduler.retryFailedNotifications();

        verify(processorService).process(failed);
    }

    @Test
    void domainAndRequestObjectsCoverGettersSetters() {
        NotificationRequest request = new NotificationRequest();
        request.setNotificationId("id");
        request.setSourceSystem("erp");
        request.setCorrelationId("corr");
        request.setNotificationType(NotificationType.ALERT);
        request.setSeverity(NotificationSeverity.HIGH);
        request.setPriority(NotificationPriority.URGENT);
        request.setRecipients(List.of(new NotificationRequest.RecipientRequest()));
        request.setRequestedChannels(List.of(ChannelType.EMAIL));
        request.setIdempotencyKey("k");
        request.setScheduledAt(Instant.now());
        request.setExpiresAt(Instant.now());
        assertEquals("id", request.getNotificationId());
        assertEquals("erp", request.getSourceSystem());
        assertEquals("corr", request.getCorrelationId());
        assertEquals(NotificationType.ALERT, request.getNotificationType());
        assertEquals(NotificationSeverity.HIGH, request.getSeverity());
        assertEquals(NotificationPriority.URGENT, request.getPriority());
        assertEquals("k", request.getIdempotencyKey());
        assertNotNull(request.getRecipients());
        assertEquals(List.of(ChannelType.EMAIL), request.getRequestedChannels());

        NotificationRequest.RecipientRequest recipientRequest = new NotificationRequest.RecipientRequest();
        recipientRequest.setRecipientId("rr");
        recipientRequest.setContactValue("mail@example.com");
        recipientRequest.setPreferredChannels(List.of(ChannelType.EMAIL));
        assertEquals("rr", recipientRequest.getRecipientId());
        assertEquals("mail@example.com", recipientRequest.getContactValue());
        assertEquals(List.of(ChannelType.EMAIL), recipientRequest.getPreferredChannels());

        NotificationResponse response = new NotificationResponse();
        Instant createdAt = Instant.now();
        response.setNotificationId("n");
        response.setStatus("QUEUED");
        response.setIdempotencyKey("key");
        response.setCreatedAt(createdAt);
        response.setSelectedChannels(List.of("EMAIL"));
        assertEquals("n", response.getNotificationId());
        assertEquals("QUEUED", response.getStatus());
        assertEquals("key", response.getIdempotencyKey());
        assertEquals(createdAt, response.getCreatedAt());
        assertEquals(List.of("EMAIL"), response.getSelectedChannels());

        NotificationResponse constructed = new NotificationResponse("n-constructed", NotificationStatus.ACCEPTED, "key-constructed", createdAt, List.of("EMAIL"));
        assertEquals(createdAt, constructed.getCreatedAt());

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setNotificationId("n");
        notification.setSourceSystem("system");
        notification.setCorrelationId("corr");
        notification.setNotificationType(NotificationType.INFO);
        notification.setSeverity(NotificationSeverity.MEDIUM);
        notification.setPriority(NotificationPriority.HIGH);
        notification.setStatus(NotificationStatus.ACCEPTED);
        notification.setIdempotencyKey("key");
        notification.setCreatedAt(Instant.now());
        notification.setScheduledAt(Instant.now());
        notification.setExpiresAt(Instant.now());
        notification.setRecipients(List.of(new NotificationRecipient()));
        notification.setDeliveryAttempts(List.of(new DeliveryAttempt()));
        notification.setAuditEvents(List.of(new AuditEvent()));
        assertNotNull(notification.getId());
        assertEquals("n", notification.getNotificationId());
        assertEquals("system", notification.getSourceSystem());
        assertEquals("corr", notification.getCorrelationId());
        assertEquals(NotificationType.INFO, notification.getNotificationType());
        assertEquals(NotificationSeverity.MEDIUM, notification.getSeverity());
        assertEquals(NotificationPriority.HIGH, notification.getPriority());
        assertEquals(NotificationStatus.ACCEPTED, notification.getStatus());
        assertEquals("key", notification.getIdempotencyKey());
        assertNotNull(notification.getCreatedAt());
        assertNotNull(notification.getScheduledAt());
        assertNotNull(notification.getExpiresAt());
        assertEquals(1, notification.getRecipients().size());
        assertEquals(1, notification.getDeliveryAttempts().size());
        assertEquals(1, notification.getAuditEvents().size());

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(UUID.randomUUID());
        recipient.setNotification(notification);
        recipient.setRecipientId("recipient");
        recipient.setContactValue("a@b.com");
        recipient.setPreferredChannels(Set.of(ChannelType.EMAIL));
        assertNotNull(recipient.getId());
        assertEquals(notification, recipient.getNotification());
        assertEquals("recipient", recipient.getRecipientId());
        assertEquals("a@b.com", recipient.getContactValue());
        assertTrue(recipient.getPreferredChannels().contains(ChannelType.EMAIL));

        DeliveryAttempt deliveryAttempt = new DeliveryAttempt();
        deliveryAttempt.setId(UUID.randomUUID());
        deliveryAttempt.setNotification(notification);
        deliveryAttempt.setRecipient(recipient);
        deliveryAttempt.setChannel(ChannelType.EMAIL);
        deliveryAttempt.setStatus(DeliveryStatus.QUEUED);
        deliveryAttempt.setAttemptNumber(2);
        deliveryAttempt.setProviderName("smtp");
        deliveryAttempt.setFailureCategory(FailureCategory.TIMEOUT);
        deliveryAttempt.setFailureReason("timeout");
        deliveryAttempt.setCreatedAt(Instant.now());
        deliveryAttempt.setProcessedAt(Instant.now());
        assertNotNull(deliveryAttempt.getId());
        assertEquals(notification, deliveryAttempt.getNotification());
        assertEquals(recipient, deliveryAttempt.getRecipient());
        assertEquals(ChannelType.EMAIL, deliveryAttempt.getChannel());
        assertEquals(DeliveryStatus.QUEUED, deliveryAttempt.getStatus());
        assertEquals(2, deliveryAttempt.getAttemptNumber());
        assertEquals("smtp", deliveryAttempt.getProviderName());
        assertEquals(FailureCategory.TIMEOUT, deliveryAttempt.getFailureCategory());
        assertEquals("timeout", deliveryAttempt.getFailureReason());
        assertNotNull(deliveryAttempt.getCreatedAt());
        assertNotNull(deliveryAttempt.getProcessedAt());

        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setNotification(notification);
        event.setEventType(AuditEventType.DELIVERY_FAILED);
        event.setDetails("details");
        event.setOccurredAt(Instant.now());
        assertNotNull(event.getId());
        assertEquals(notification, event.getNotification());
        assertEquals(AuditEventType.DELIVERY_FAILED, event.getEventType());
        assertEquals("details", event.getDetails());
        assertNotNull(event.getOccurredAt());

        ChannelProperties.ChannelConfig config = new ChannelProperties.ChannelConfig();
        config.setEnabled(true);
        config.setMaxAttempts(5);
        config.setTimeoutMs(123L);
        assertTrue(config.isEnabled());
        assertEquals(5, config.getMaxAttempts());
        assertEquals(123L, config.getTimeoutMs());

        ChannelProperties props = new ChannelProperties();
        props.setChannels(Map.of("email", config));
        assertEquals(config, props.getChannels().get("email"));

        ChannelProperties.RoutingConfig routingConfig = new ChannelProperties.RoutingConfig();
        routingConfig.setSeverityChannelOverrides(null);
        routingConfig.setDefaultChannels(null);
        props.setChannels(null);
        props.setRouting(null);
        assertTrue(props.getChannels().isEmpty());
        assertNotNull(props.getRouting());
        assertTrue(routingConfig.getSeverityChannelOverrides().isEmpty());
        assertTrue(routingConfig.getDefaultChannels().isEmpty());
    }

    @Test
    void deliveryConfigurationCoversPropertiesAndBeans() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        properties.setMode("rabbitmq");
        properties.setTopic("notification-events");
        properties.setMaxAttempts(5);
        properties.setRetryDelayMs(333L);
        properties.setRateLimitPerMinute(77);
        properties.setRateLimitWindowSeconds(15);
        properties.setCircuitBreakerEnabled(false);
        properties.setCircuitBreakerFailureRateThreshold(42);
        properties.setCircuitBreakerWaitDurationMs(9000L);

        assertEquals("rabbitmq", properties.getMode());
        assertEquals("notification-events", properties.getTopic());
        assertEquals(5, properties.getMaxAttempts());
        assertEquals(333L, properties.getRetryDelayMs());
        assertEquals(77, properties.getRateLimitPerMinute());
        assertEquals(15, properties.getRateLimitWindowSeconds());
        assertFalse(properties.isCircuitBreakerEnabled());
        assertEquals(42, properties.getCircuitBreakerFailureRateThreshold());
        assertEquals(9000L, properties.getCircuitBreakerWaitDurationMs());

        NotificationDeliveryConfig config = new NotificationDeliveryConfig();
        MessageNotificationPublisher publisher = (MessageNotificationPublisher) config.notificationDeliveryPublisher(properties);
        assertEquals("rabbitmq", publisher.getBrokerType());

        NotificationRateLimiter limiter = config.notificationRateLimiter(properties);
        assertTrue(limiter.tryAcquire());

        ResilientNotificationSender sender = config.resilientNotificationSender(limiter, publisher, properties);
        assertNotNull(sender);
    }

    @Test
    void applicationMainStartsWithoutWebServer() {
        NotificationServiceApplication.main(new String[]{"--spring.main.web-application-type=none"});
    }

    private NotificationRequest notificationRequestWith(String idempotencyKey) {
        NotificationRequest request = new NotificationRequest();
        request.setNotificationId("n-req");
        request.setSourceSystem("system");
        request.setCorrelationId("corr");
        request.setNotificationType(NotificationType.INFO);
        request.setSeverity(NotificationSeverity.MEDIUM);
        request.setPriority(NotificationPriority.LOW);

        NotificationRequest.RecipientRequest recipient = new NotificationRequest.RecipientRequest();
        recipient.setRecipientId("r-1");
        recipient.setContactValue("user@example.com");
        recipient.setPreferredChannels(List.of(ChannelType.EMAIL));
        request.setRecipients(List.of(recipient));
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }
}
