package com.eclectics.collaboration.Tool.service.Impl;

;
import com.eclectics.collaboration.Tool.dto.NotificationPreferenceResponse;
import com.eclectics.collaboration.Tool.dto.NotificationResponse;
import com.eclectics.collaboration.Tool.dto.UpdateNotificationPreferenceRequest;
import com.eclectics.collaboration.Tool.model.Notification;
import com.eclectics.collaboration.Tool.model.NotificationPreference;
import com.eclectics.collaboration.Tool.model.User;
import com.eclectics.collaboration.Tool.repository.NotificationPreferenceRepository;
import com.eclectics.collaboration.Tool.repository.NotificationRepository;
import com.eclectics.collaboration.Tool.repository.UserRespository;
import com.eclectics.collaboration.Tool.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRespository userRepository;

    // ── Preferences ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Override
    public NotificationPreferenceResponse getPreferences(Long userId) {
        NotificationPreference pref = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> defaultPreferences(userId));
        return toResponse(pref);
    }

    @Transactional
    @Override
    public NotificationPreferenceResponse updatePreferences(Long userId, UpdateNotificationPreferenceRequest req) {
        NotificationPreference pref = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> defaultPreferences(userId));

        pref.setEmailOnAssign(req.emailOnAssign());
        pref.setInAppOnAssign(req.inAppOnAssign());
        pref.setEmailOnBoardAdd(req.emailOnBoardAdd());
        pref.setInAppOnBoardAdd(req.inAppOnBoardAdd());
        pref.setEmailOnMention(req.emailOnMention());
        pref.setInAppOnMention(req.inAppOnMention());
        pref.setEmailOnDueSoon(req.emailOnDueSoon());
        pref.setInAppOnDueSoon(req.inAppOnDueSoon());

        return toResponse(preferenceRepository.save(pref));
    }

    @Transactional
    @Override
    public NotificationPreference getOrCreatePreferenceEntity(Long userId) {
        return preferenceRepository.findByUserId(userId)
                .orElseGet(() -> preferenceRepository.save(defaultPreferences(userId)));
    }

    private NotificationPreference defaultPreferences(Long userId) {
        User user = userRepository.getReferenceById(userId);
        return NotificationPreference.builder().user(user).build(); // Lombok @Builder.Default gives all-true
    }

    // ── Notifications ────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public Notification create(User recipient, Notification.Type type, String message,
                               String referenceType, Long referenceId) {
        Notification n = Notification.builder()
                .recipient(recipient)
                .type(type)
                .message(message)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();
        return notificationRepository.save(n);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    @Override
    public void markRead(Long notificationId, Long requestingUserId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        if (!n.getRecipient().getId().equals(requestingUserId)) {
            throw new AccessDeniedException("Cannot mark another user's notification as read");
        }
        n.setRead(true);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .message(n.getMessage())
                .referenceType(n.getReferenceType())
                .referenceId(n.getReferenceId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference p) {
        return NotificationPreferenceResponse.builder()
                .emailOnAssign(p.isEmailOnAssign()).inAppOnAssign(p.isInAppOnAssign())
                .emailOnBoardAdd(p.isEmailOnBoardAdd()).inAppOnBoardAdd(p.isInAppOnBoardAdd())
                .emailOnMention(p.isEmailOnMention()).inAppOnMention(p.isInAppOnMention())
                .emailOnDueSoon(p.isEmailOnDueSoon()).inAppOnDueSoon(p.isInAppOnDueSoon())
                .weeklyDigest(p.isWeeklyDigest())
                .build();
    }

    @Transactional(readOnly = true)
    @Override
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    @Override
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadForUser(userId);
    }
}
