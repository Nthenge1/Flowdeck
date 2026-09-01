package com.eclectics.collaboration.Tool.service.Impl;

import com.eclectics.collaboration.Tool.dto.InviteRequestDTO;
import com.eclectics.collaboration.Tool.dto.InviteResponseDTO;
import com.eclectics.collaboration.Tool.enums.WorkspaceRole;
import com.eclectics.collaboration.Tool.exception.CollaborationExceptions;
import com.eclectics.collaboration.Tool.enums.ConfigKey;
import com.eclectics.collaboration.Tool.model.Invitation;
import com.eclectics.collaboration.Tool.model.User;
import com.eclectics.collaboration.Tool.model.WorkSpace;
import com.eclectics.collaboration.Tool.repository.InvitationRepository;
import com.eclectics.collaboration.Tool.repository.UserRespository;
import com.eclectics.collaboration.Tool.repository.WorkSpaceMemberRepository;
import com.eclectics.collaboration.Tool.repository.WorkSpaceReposiroty;
import com.eclectics.collaboration.Tool.service.EmailService;
import com.eclectics.collaboration.Tool.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RequiredArgsConstructor
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final WorkSpaceReposiroty workspaceRepository;
    private final InvitationRepository invitationRepository;
    private final SystemConfigService systemConfigService;
    private final WorkSpaceMemberRepository workSpaceMemberRepository;
    private final UserRespository userRespository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${brevo.api-key}")
    private String brevoApiKey;

    @Value("${brevo.from-email}")
    private String fromEmaill;

    @Value("${brevo.from-name}")
    private String fromName;

    @Override
    public void sendAccountConfirmationEmail(String to, String confirmLink) {
        String subject = "FLOWDECK ACCOUNT CONFIRMATION";
        String text = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px;">
                <h2 style="color: #4F46E5;">Welcome to FLOWDECK!</h2>
                <p>Hello,</p>
                <p>Thank you for registering! Please confirm your account by clicking the button below:</p>
                <a href="%s"
                   style="display:inline-block; padding:12px 24px; background-color:#4F46E5;
                          color:white; text-decoration:none; border-radius:6px; margin: 16px 0;">
                   Confirm My Account
                </a>
                <br/><br/>
                <img src="https://img.freepik.com/free-vector/team-collaboration-concept_23-2148908789.jpg"
                     alt="Team Collaboration"
                     style="width:100%%; max-width:500px; border-radius:8px; margin: 16px 0;" />
                <br/>
                <p>Best regards,<br/><strong>FLOWDECK</strong></p>
            </div>
            """.formatted(confirmLink);
        sendEmail(to, subject, text);
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        String subject = "FLOWDECK RESET PASSWORD";
        String text = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px;">
                <h2 style="color: #4F46E5;">Password Reset Request</h2>
                <p>Hello,</p>
                <p>We received a request to reset your password. Click the button below to proceed:</p>
                <a href="%s"
                   style="display:inline-block; padding:12px 24px; background-color:#4F46E5;
                          color:white; text-decoration:none; border-radius:6px; margin: 16px 0;">
                   Reset My Password
                </a>
                <p style="color: #888; font-size: 13px;">If you didn't request this, you can safely ignore this email. Your password will not be changed.</p>
                <br/>
                <img src="https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=600"
                     alt="Team Collaboration"
                     style="width:100%%; max-width:500px; border-radius:8px; margin: 16px 0;" />
                <br/>
                <p>Best regards,<br/><strong>FLOWDECK</strong></p>
            </div>
            """.formatted(resetLink);
        sendEmail(to, subject, text);
    }

    @Override
    public void sendMentionNotification(String to, String mentionerName, String cardTitle, String commentSnippet) {
        String subject = "FLOWDECK. You were mentioned in a comment";
        String text = """
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px;">
            <h2 style="color: #4F46E5;">You were mentioned</h2>
            <p>Hello,</p>
            <p><strong>%s</strong> mentioned you in a comment on the card <strong>%s</strong>:</p>
            <div style="background:#f3f4f6; border-left: 3px solid #4F46E5; padding: 12px 16px; margin: 16px 0; color:#334155;">
                %s
            </div>
            <p style="color: #888; font-size: 13px;">Log in to Flowdeck to view and reply.</p>
            <br/>
            <p>Best regards,<br/><strong>FLOWDECK</strong></p>
        </div>
        """.formatted(mentionerName, cardTitle, commentSnippet);
        sendEmail(to, subject, text);
    }

    @Transactional
    @Override
    public InviteResponseDTO inviteUsers(User owner, InviteRequestDTO inviteDto, Long workspaceId) {
        WorkSpace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Workspace not found"));

        boolean isOwner = workspace.getWorkSpaceOwnerId().getId().equals(owner.getId());
        boolean isAdminMember = workSpaceMemberRepository
                .findByWorkspace_IdAndUser_Id(workspaceId, owner.getId())
                .map(m -> m.getRole() == WorkspaceRole.ADMIN)
                .orElse(false);

        if (!isOwner && !isAdminMember) {
            throw new CollaborationExceptions.UnauthorizedException("Only the workspace owner or an admin can invite others");
        }

        List<InviteResponseDTO.InviteResultDTO> results = new ArrayList<>();

        for (InviteRequestDTO.InviteeDTO invitee : inviteDto.getInvitations()) {
            String email = invitee.getEmail();

            Optional<User> existingUser = userRespository.findByEmail(email);
            if (existingUser.isPresent()) {
                boolean alreadyMember = workSpaceMemberRepository
                        .findByWorkspace_IdAndUser_Id(workspaceId, existingUser.get().getId())
                        .isPresent();

                if (alreadyMember) {
                    results.add(new InviteResponseDTO.InviteResultDTO(
                            email, false, "This user is already a member of the workspace"
                    ));
                    continue;
                }
            }

            String token = UUID.randomUUID().toString();

            Invitation invite = new Invitation();
            invite.setEmail(email);
            invite.setWorkspace(workspace);
            invite.setInviteToken(token);
            invite.setExpiryDate(LocalDateTime.now().plusDays(7));
            invite.setRole(invitee.getRole() != null ? invitee.getRole() : WorkspaceRole.MEMBER);

            invitationRepository.save(invite);
            sendInvitationEmail(email, token, workspace.getWorkSpaceName());

            results.add(new InviteResponseDTO.InviteResultDTO(
                    email, true, "Invitation sent"
            ));
        }

        return new InviteResponseDTO(results);
    }
    @Override
    public void sendInvitationEmail(String to, String token, String workspaceName) {
        String subject = "FLOWDECK WORKSPACE INVITE";
        String text = """
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px;">
            <h2 style="color: #4F46E5;">You're Invited to Join a Workspace!</h2>
            <p>Hello,</p>
            <p>You've been invited to join the workspace: <strong>%s</strong></p>
            <p>Log in to your FLOWDECK account to accept or decline this invitation.</p>
            <a href="https://flowdeckk.netlify.app/"
               style="display:inline-block; padding:12px 24px; background-color:#4F46E5;
                      color:white; text-decoration:none; border-radius:6px; margin: 8px 4px;">
               Login here
            </a>
            <p style="color: #888; font-size: 13px;">This invite expires in 7 days.</p>
            <br/>
            <img src="https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=600"
                 alt="Team Collaboration"
                 style="width:100%%; max-width:500px; border-radius:8px; margin: 16px 0;" />
            <br/>
            <p>Best regards,<br/><strong>FLOWDECK</strong></p>
        </div>
        """.formatted(workspaceName);
        sendEmail(to, subject, text);
    }

    @Override
    public void sendInviteRejectedEmail(String ownerEmail, String inviteeEmail, String workspaceName) {
        String subject = "FLOWDECK INVITE DECLINE";
        String text = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px;">
                <h2 style="color: #4F46E5;">Workspace Invite Declined</h2>
                <p>Hello,</p>
                <p><strong>%s</strong> has declined your invitation to join the workspace: <strong>%s</strong>.</p>
                <p>You may invite someone else if needed.</p>
                <br/>
                <img src="https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=600"
                     alt="Team Collaboration"
                     style="width:100%%; max-width:500px; border-radius:8px; margin: 16px 0;" />
                <br/>
                <p>Best regards,<br/><strong>FLOWDECK</strong></p>
            </div>
            """.formatted(inviteeEmail, workspaceName);
        sendEmail(ownerEmail, subject, text);
    }

    @Override
    public void sendNewSupportNotification(String submitterName, String submitterEmail, String issueName, String message) {
        String adminEmail = systemConfigService.getValueByKey(ConfigKey.SUPPORT_EMAIL);
        String subject = "FLOWDECK. New Support Request Received";
        String text = """
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px;">
            <h2 style="color: #4F46E5;">New Support Request</h2>
            <p>Hello Admin,</p>
            <p>A new support message has been submitted. Here are the details:</p>

            <table style="width: 100%%; border-collapse: collapse; margin: 16px 0;">
                <tr style="background-color: #f3f4f6;">
                    <td style="padding: 10px 14px; font-weight: bold; width: 140px;">Full Name</td>
                    <td style="padding: 10px 14px;">%s</td>
                </tr>
                <tr>
                    <td style="padding: 10px 14px; font-weight: bold;">Email</td>
                    <td style="padding: 10px 14px;">
                        <a href="mailto:%s" style="color: #4F46E5;">%s</a>
                    </td>
                </tr>
                <tr style="background-color: #f3f4f6;">
                    <td style="padding: 10px 14px; font-weight: bold;">Issue Category</td>
                    <td style="padding: 10px 14px;">%s</td>
                </tr>
                <tr>
                    <td style="padding: 10px 14px; font-weight: bold; vertical-align: top;">Message</td>
                    <td style="padding: 10px 14px;">%s</td>
                </tr>
            </table>

            <p style="color: #888; font-size: 13px;">
                Log in to the admin dashboard to review and update the status of this request.
            </p>
            <br/>
            <p>Best regards,<br/><strong>FLOWDECK SYSTEM</strong></p>
        </div>
        """.formatted(submitterName, submitterEmail, submitterEmail, issueName, message);

        sendEmail(adminEmail, subject, text);
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        log.info("Attempting to send email to: {}", to);

        Map<String, Object> sender = Map.of(
                "name", fromName,
                "email", fromEmaill
        );

        Map<String, Object> recipient = Map.of("email", to);

        Map<String, Object> body = new HashMap<>();
        body.put("sender", sender);
        body.put("to", List.of(recipient));
        body.put("subject", subject);
        body.put("htmlContent", htmlContent);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonBody = objectMapper.writeValueAsString(body);

            WebClient client = WebClient.create();
            client.post()
                    .uri("https://api.brevo.com/v3/smtp/email")
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(r -> log.info("Email sent successfully to: {}", to))
                    .doOnError(e -> log.error("Email sending failed to: {} — reason: {}", to, e.getMessage()))
                    .block();
        } catch (Exception e) {
            log.error("Email sending failed to: {} — reason: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email to: " + to, e);
        }
    }

    @Override
    public void sendCardAssignedNotification(String to, String assignerName, String cardTitle) {
        String subject = "FLOWDECK — You were assigned a card";
        String text = """
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px;">
            <h2 style="color: #4F46E5;">You were assigned a card</h2>
            <p>Hello,</p>
            <p><strong>%s</strong> assigned you to the card <strong>%s</strong>.</p>
            <p style="color: #888; font-size: 13px;">Log in to Flowdeck to view the card.</p>
            <br/>
            <p>Best regards,<br/><strong>FLOWDECK</strong></p>
        </div>
        """.formatted(assignerName, cardTitle);
        sendEmail(to, subject, text);
    }

    @Override
    public void sendBoardAddedNotification(String to, String adderName, String boardName) {
        String subject = "Flowdeck — You were added to a board";
        String text = """
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px;">
            <h2 style="color: #4F46E5;">You were added to a board</h2>
            <p>Hello,</p>
            <p><strong>%s</strong> added you to the board <strong>%s</strong>.</p>
            <p style="color: #888; font-size: 13px;">Log in to Flowdeck to view the board.</p>
            <br/>
            <p>Best regards,<br/><strong>FLOWDECK</strong></p>
        </div>
        """.formatted(adderName, boardName);
        sendEmail(to, subject, text);
    }

    @Override
    public void sendDueSoonNotification(String to, String cardTitle, LocalDateTime dueDate) {
        String subject = "Flowdeck. Card due soon";
        String formattedDueDate = dueDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a"));
        String text = """
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px;">
            <h2 style="color: #4F46E5;">Card due soon</h2>
            <p>Hello,</p>
            <p>Your card <strong>%s</strong> is due on <strong>%s</strong>.</p>
            <p style="color: #888; font-size: 13px;">Log in to Flowdeck to view the card.</p>
            <br/>
            <p>Best regards,<br/><strong>FLOWDECK</strong></p>
        </div>
        """.formatted(cardTitle, formattedDueDate);
        sendEmail(to, subject, text);
    }

    @Override
    public void sendWeeklyDigest(String to, String userName, List<String> assignedCardTitles, List<String> mentionSummaries) {
        String subject = "Flowdeck. Your weekly digest";

        StringBuilder cardsHtml = new StringBuilder();
        if (!assignedCardTitles.isEmpty()) {
            cardsHtml.append("<h3 style=\"color:#334155;\">Cards assigned to you</h3><ul>");
            for (String title : assignedCardTitles) {
                cardsHtml.append("<li>").append(title).append("</li>");
            }
            cardsHtml.append("</ul>");
        }

        StringBuilder mentionsHtml = new StringBuilder();
        if (!mentionSummaries.isEmpty()) {
            mentionsHtml.append("<h3 style=\"color:#334155;\">Mentions this week</h3><ul>");
            for (String summary : mentionSummaries) {
                mentionsHtml.append("<li>").append(summary).append("</li>");
            }
            mentionsHtml.append("</ul>");
        }

        String text = """
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px;">
            <h2 style="color: #4F46E5;">Your weekly digest</h2>
            <p>Hello %s,</p>
            <p>Here's what happened on Flowdeck this week:</p>
            %s
            %s
            <p style="color: #888; font-size: 13px;">Log in to Flowdeck to view details.</p>
            <br/>
            <p>Best regards,<br/><strong>FLOWDECK</strong></p>
        </div>
        """.formatted(userName, cardsHtml, mentionsHtml);

        sendEmail(to, subject, text);
    }
}
