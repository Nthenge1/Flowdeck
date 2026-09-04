package com.eclectics.collaboration.Tool.service.Impl;

import com.eclectics.collaboration.Tool.dto.InvitationResponseDTO;
import com.eclectics.collaboration.Tool.dto.MyInvitationResponseDTO;
import com.eclectics.collaboration.Tool.enums.WorkspaceRole;
import com.eclectics.collaboration.Tool.exception.CollaborationExceptions;
import com.eclectics.collaboration.Tool.model.Invitation;
import com.eclectics.collaboration.Tool.model.User;
import com.eclectics.collaboration.Tool.model.WorkSpace;
import com.eclectics.collaboration.Tool.model.WorkSpaceMember;
import com.eclectics.collaboration.Tool.repository.InvitationRepository;
import com.eclectics.collaboration.Tool.repository.UserRespository;
import com.eclectics.collaboration.Tool.repository.WorkSpaceMemberRepository;
import com.eclectics.collaboration.Tool.repository.WorkSpaceReposiroty;
import com.eclectics.collaboration.Tool.service.EmailService;
import com.eclectics.collaboration.Tool.service.InvitationService;
import com.eclectics.collaboration.Tool.service.WorkSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class InvitationServiceImpl implements InvitationService {

    private final InvitationRepository invitationRepository;
    private final WorkSpaceReposiroty workSpaceRepository;
    private final EmailService emailService;
    private final UserRespository userRepository;
    private final WorkSpaceMemberRepository workSpaceMemberRepository;
    private final WorkSpaceService workSpaceService;

    @Transactional
    @Override
    public void acceptInvite(String token, User invitee) {
        Invitation invite = invitationRepository.findByInviteToken(token)
                .orElseThrow(() -> new CollaborationExceptions.BadRequestException("Invalid or non-existent invitation token."));

        if (invite.getExpiryDate().isBefore(LocalDateTime.now())) {
            invitationRepository.delete(invite);
            throw new CollaborationExceptions.BadRequestException("This invitation has expired.");
        }

        if (!invite.getEmail().equalsIgnoreCase(invitee.getEmail())) {
            throw new CollaborationExceptions.BadRequestException("This invite was sent to a different email address.");
        }

        User managedInvitee = userRepository.findById(invitee.getId())
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("User not found."));

        WorkSpace ws = workSpaceRepository.findById(invite.getWorkspace().getId())
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Workspace not found."));

        boolean alreadyMember = workSpaceMemberRepository.existsByWorkspace_IdAndUser_Id(ws.getId(), managedInvitee.getId());
        if (!alreadyMember) {
            WorkSpaceMember member = new WorkSpaceMember();
            member.setWorkspace(ws);
            member.setUser(managedInvitee);
            member.setRole(invite.getRole() != null ? invite.getRole() : WorkspaceRole.MEMBER);
            workSpaceMemberRepository.save(member);
        }

        invitationRepository.delete(invite);

        workSpaceService.evictWorkspaceCachesFor(ws.getWorkSpaceOwnerId().getEmail(), ws.getWorkSpaceOwnerId().getId(), ws.getId());
        workSpaceMemberRepository.findByWorkspace_Id(ws.getId()).forEach(m ->
                workSpaceService.evictWorkspaceCachesFor(m.getUser().getEmail(), m.getUser().getId(), ws.getId())
        );
    }

    @Transactional
    @Override
    public void rejectInvite(String token, User invitee) {
        Invitation invite = invitationRepository.findByInviteToken(token)
                .orElseThrow(() -> new CollaborationExceptions.BadRequestException("Invalid or non-existent invitation token."));

        if (invite.getExpiryDate().isBefore(LocalDateTime.now())) {
            invitationRepository.delete(invite);
            throw new CollaborationExceptions.BadRequestException("This invitation has expired.");
        }

        if (!invite.getEmail().equalsIgnoreCase(invitee.getEmail())) {
            throw new CollaborationExceptions.BadRequestException("This invite was sent to a different email address.");
        }

        WorkSpace workspace = workSpaceRepository.findById(invite.getWorkspace().getId())
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Workspace not found."));

        String ownerEmail = workspace.getWorkSpaceOwnerId().getEmail();
        String workspaceName = workspace.getWorkSpaceName();
        String inviteeEmail = invitee.getEmail();

        invitationRepository.delete(invite);

        emailService.sendInviteRejectedEmail(ownerEmail, inviteeEmail, workspaceName);
    }


    // ─── DELETE ───────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public void deleteInvitation(Long invitationId, User requester) {
        Invitation invite = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Invitation not found."));

        WorkSpace workspace = invite.getWorkspace();

        // Only the workspace owner can manually delete a invite
        if (!workspace.getWorkSpaceOwnerId().getId().equals(requester.getId())) {
            throw new CollaborationExceptions.UnauthorizedException("Only the workspace owner can delete invitations.");
        }

        invitationRepository.delete(invite);
    }

    // ─── GET ALL FOR WORKSPACE ────────────────────────────────────────────────

    @Override
    public List<InvitationResponseDTO> getWorkspaceInvitations(Long workspaceId, User requester) {
        WorkSpace workspace = workSpaceRepository.findById(workspaceId)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Workspace not found."));

        // Only the workspace owner can view all pending invites
        if (!workspace.getWorkSpaceOwnerId().getId().equals(requester.getId())) {
            throw new CollaborationExceptions.UnauthorizedException("Only the workspace owner can view invitations.");
        }

        return invitationRepository.findAllByWorkspaceId(workspaceId)
                .stream()
                .map(invite -> InvitationResponseDTO.builder()
                        .id(invite.getId())
                        .email(invite.getEmail())
                        .workspaceName(workspace.getWorkSpaceName())
                        .expiryDate(invite.getExpiryDate())
                        .expired(invite.getExpiryDate().isBefore(LocalDateTime.now()))
                        .build())
                .toList();
    }

    // ─── GET ALL FOR CURRENT USER (INBOX) ──────────────────────────────────────

    @Override
    public List<MyInvitationResponseDTO> getMyInvitations(User user) {
        return invitationRepository.findAllByEmailIgnoreCase(user.getEmail())
                .stream()
                .filter(invite -> !invite.getExpiryDate().isBefore(LocalDateTime.now()))
                .map(invite -> MyInvitationResponseDTO.builder()
                        .token(invite.getInviteToken())
                        .workspaceName(invite.getWorkspace().getWorkSpaceName())
                        .expiryDate(invite.getExpiryDate())
                        .build())
                .toList();
    }
}
