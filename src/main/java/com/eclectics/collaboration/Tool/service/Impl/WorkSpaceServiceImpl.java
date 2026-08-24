package com.eclectics.collaboration.Tool.service.Impl;

import com.eclectics.collaboration.Tool.dto.WorkSpaceRequestDTO;
import com.eclectics.collaboration.Tool.dto.WorkSpaceResponseDTO;
import com.eclectics.collaboration.Tool.enums.BoardRole;
import com.eclectics.collaboration.Tool.enums.WorkspaceRole;
import com.eclectics.collaboration.Tool.exception.CollaborationExceptions;
import com.eclectics.collaboration.Tool.mapper.WorkSpaceMapper;
import com.eclectics.collaboration.Tool.model.*;
import com.eclectics.collaboration.Tool.repository.*;
import com.eclectics.collaboration.Tool.service.WorkSpaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class WorkSpaceServiceImpl implements WorkSpaceService {

    private final WorkSpaceReposiroty workSpaceReposiroty;
    private final WorkSpaceMapper workSpaceMapper;
    private final UserRespository userRespository;
    private final StarredWorkspaceRepository starredWorkspaceRepository;
    private final CardAssigneeRepository cardAssigneeRepository;
    private final BoardsRepository boardsRepository;
    private final BoardMemberRepository boardMemberRepository;
    private final StarredBoardRepository starredBoardRepository;
    private final ListEntityRepository listEntityRepository;
    private final WorkSpaceMemberRepository workSpaceMemberRepository;
    private final UserRecentBoardRepository userRecentBoardRepository;
    private final CacheManager cacheManager;

    // Cache names used across this class:
    //   "workspaces_my"      -> keyed by user's email, holds myWorkspaces() result
    //   "workspaces_starred" -> keyed by userId, holds getStarredWorkspaces() result
    //   "workspace_by_id"    -> keyed by "workspaceId:userId" (starred flag is user-specific)
    //
    // All eviction here is manual (via CacheManager) rather than @CacheEvict,
    // because a workspace mutation can invalidate caches for EVERY member of
    // that workspace, not just the acting user — something SpEL keys on a
    // single method argument can't express.

    @Override
    @Transactional
    public WorkSpaceResponseDTO createWorkspace(User user, WorkSpaceRequestDTO request) {
        WorkSpace ws = workSpaceMapper.toEntity(request, user);
        WorkSpace saved = workSpaceReposiroty.save(ws);

        WorkSpaceMember ownerMember = new WorkSpaceMember();
        ownerMember.setWorkspace(saved);
        ownerMember.setUser(user);
        ownerMember.setRole(WorkspaceRole.ADMIN);
        workSpaceMemberRepository.save(ownerMember);

        // Only the creator is affected - no other members exist yet.
        evictCachesForUser(user.getEmail(), user.getId(), saved.getId());

        log.info("Workspace created id={} by user={}", saved.getId(), user.getEmail());
        return workSpaceMapper.toDto(saved);
    }

    @Override
    @Cacheable(value = "workspaces_my", key = "#root.target.resolveCurrentUserEmail()")
    public List<WorkSpaceResponseDTO> myWorkspaces() {
        String email = resolveCurrentUserEmail();

        User user = userRespository.findByEmail(email)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("User not found"));

        List<WorkSpace> workspaces = workSpaceReposiroty.findAllByOwnerOrMember(user);

        Set<Long> starredIds = starredWorkspaceRepository.findByUser_Id(user.getId()).stream()
                .map(sw -> sw.getWorkspace().getId())
                .collect(Collectors.toSet());

        return workspaces.stream()
                .map(ws -> {
                    WorkSpaceResponseDTO dto = workSpaceMapper.toDto(ws);
                    dto.setStarred(starredIds.contains(ws.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Extracted from myWorkspaces() so the same email-resolution logic can be
     * referenced from the @Cacheable key SpEL via #root.target.
     */
    public String resolveCurrentUserEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return (principal instanceof UserDetails) ?
                ((UserDetails) principal).getUsername() : principal.toString();
    }

    @Transactional
    @Override
    public void deleteWorkspace(Long workspaceId, User user) {
        WorkSpace ws = workSpaceReposiroty.findById(workspaceId)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Workspace not found"));

        if (!ws.getWorkSpaceOwnerId().getId().equals(user.getId())) {
            throw new RuntimeException("You do not have permission to delete this workspace");
        }

        // Snapshot members BEFORE deleting - once the workspace is gone,
        // membership rows may cascade-delete along with it and we'd lose the list.
        List<WorkSpaceMember> members = workSpaceMemberRepository.findByWorkspace_Id(workspaceId);

        workSpaceReposiroty.delete(ws);

        evictCachesForMembers(members, workspaceId);
    }

    @Transactional
    @Override
    public boolean toggleStarWorkspace(Long workspaceId, Long userId) {
        WorkSpace workspace = workSpaceReposiroty.findById(workspaceId)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Workspace not found"));
        User user = userRespository.findById(userId)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("User not found"));

        Optional<StarredWorkspace> existing = starredWorkspaceRepository.findByWorkspace_IdAndUser_Id(workspaceId, userId);
        boolean result;
        if (existing.isPresent()) {
            starredWorkspaceRepository.delete(existing.get());
            result = false;
        } else {
            starredWorkspaceRepository.save(new StarredWorkspace(workspace, user));
            result = true;
        }

        // Only the acting user's starred flag changed - no other member is affected.
        evictCachesForUser(user.getEmail(), user.getId(), workspaceId);
        return result;
    }

    @Override
    @Cacheable(value = "workspaces_starred", key = "#userId")
    public List<WorkSpaceResponseDTO> getStarredWorkspaces(Long userId) {
        return starredWorkspaceRepository.findByUser_Id(userId).stream()
                .map(sw -> {
                    WorkSpaceResponseDTO dto = workSpaceMapper.toDto(sw.getWorkspace());
                    dto.setStarred(true);
                    return dto;
                })
                .toList();
    }

    @Transactional
    @Override
    public void leaveWorkspace(Long workspaceId, User user) {
        WorkSpace ws = workSpaceReposiroty.findById(workspaceId)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Workspace not found"));

        WorkSpaceMember member = workSpaceMemberRepository
                .findByWorkspace_IdAndUser_Id(workspaceId, user.getId())
                .orElseThrow(() -> new CollaborationExceptions.ForbiddenException("You are not a member of this workspace"));

        // Snapshot the full member list up front - every exit path below
        // (cascade-delete, ownership transfer, plain leave) needs to evict
        // caches for members who existed BEFORE this method starts mutating rows.
        List<WorkSpaceMember> membersBeforeLeaving = workSpaceMemberRepository.findByWorkspace_Id(workspaceId);

        boolean isAdmin = member.getRole() == WorkspaceRole.ADMIN;
        boolean isOwner = ws.getWorkSpaceOwnerId().getId().equals(user.getId());

        if (isAdmin) {
            long remainingAdminsAfterLeaving =
                    workSpaceMemberRepository.countByWorkspace_IdAndRole(workspaceId, WorkspaceRole.ADMIN) - 1;

            if (remainingAdminsAfterLeaving <= 0) {
                deleteWorkspaceCascade(ws);
                evictCachesForMembers(membersBeforeLeaving, workspaceId);
                return;
            }

            if (isOwner) {
                WorkSpaceMember newOwnerMember = workSpaceMemberRepository
                        .findFirstByWorkspace_IdAndRoleAndUser_IdNot(workspaceId, WorkspaceRole.ADMIN, user.getId())
                        .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException(
                                "No eligible admin found to transfer ownership"));

                ws.setWorkSpaceOwnerId(newOwnerMember.getUser());
                workSpaceReposiroty.save(ws);

                grantBoardAdminAccessToAllBoards(ws, newOwnerMember.getUser());

                log.info("Workspace ownership transferred id={} from user={} to user={}",
                        ws.getId(), user.getEmail(), newOwnerMember.getUser().getEmail());
            }
        }

        removeMemberFromWorkspace(ws, user);

        // Ownership transfer changes ws.getWorkSpaceOwnerId() for every member's
        // getWorkspaceById() view, and the leaving user drops out of everyone's
        // membership-derived state - evict everyone who was in the workspace.
        evictCachesForMembers(membersBeforeLeaving, workspaceId);
    }

    private void removeMemberFromWorkspace(WorkSpace ws, User user) {
        Long workspaceId = ws.getId();
        Long userId = user.getId();
        cardAssigneeRepository.deleteByUserIdAndWorkspaceId(userId, workspaceId);
        boardMemberRepository.deleteByUserIdAndWorkspaceId(userId, workspaceId);
        starredWorkspaceRepository.findByWorkspace_IdAndUser_Id(workspaceId, userId)
                .ifPresent(starredWorkspaceRepository::delete);
        starredBoardRepository.deleteByUserIdAndWorkspaceId(userId, workspaceId);
        workSpaceMemberRepository.deleteByWorkspace_IdAndUser_Id(workspaceId, userId);
    }

    private void deleteWorkspaceCascade(WorkSpace ws) {
        Long workspaceId = ws.getId();
        List<Boards> boards = boardsRepository.findAllByWorkSpaceId_Id(workspaceId);

        for (Boards board : boards) {
            Long boardId = board.getId();
            userRecentBoardRepository.deleteByBoard_Id(boardId);
            cardAssigneeRepository.deleteByBoardId(boardId);
            List<ListEntity> lists = listEntityRepository.findByBoard_IdOrderByPosition(boardId);
            listEntityRepository.deleteAll(lists);
            boardMemberRepository.deleteByBoardId(boardId);
            starredBoardRepository.deleteByBoard_Id(boardId);
            boardsRepository.delete(board);
        }
        starredWorkspaceRepository.deleteByWorkspace_Id(workspaceId);
        workSpaceReposiroty.delete(ws);
    }

    private void grantBoardAdminAccessToAllBoards(WorkSpace ws, User user) {
        List<Boards> boards = boardsRepository.findByWorkSpaceId_Id(ws.getId());

        for (Boards board : boards) {
            BoardMember existing = boardMemberRepository
                    .findByBoardIdAndUserId(board.getId(), user.getId())
                    .orElse(null);

            if (existing == null) {
                boardMemberRepository.save(new BoardMember(board, user, BoardRole.ADMIN));
            } else if (existing.getRole() != BoardRole.ADMIN) {
                existing.changeRole(BoardRole.ADMIN);
                boardMemberRepository.save(existing);
            }
        }
    }

    @Transactional
    @Override
    public WorkSpaceResponseDTO updateWorkspace(Long workspaceId, User user, WorkSpaceRequestDTO request) {
        WorkSpace ws = workSpaceReposiroty.findById(workspaceId)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Workspace not found"));

        boolean isOwner = ws.getWorkSpaceOwnerId().getId().equals(user.getId());
        boolean isAdminMember = workSpaceMemberRepository
                .findByWorkspace_IdAndUser_Id(workspaceId, user.getId())
                .map(m -> m.getRole() == WorkspaceRole.ADMIN)
                .orElse(false);

        if (!isOwner && !isAdminMember) {
            throw new CollaborationExceptions.ForbiddenException("You do not have permission to edit this workspace");
        }

        if (request.getWorkSpaceName() != null && !request.getWorkSpaceName().isBlank()) {
            ws.setWorkSpaceName(request.getWorkSpaceName());
        }
        ws.setWorkSpaceDescription(request.getWorkSpaceDescription());

        WorkSpace saved = workSpaceReposiroty.save(ws);

        // Name/description changed - every member's getWorkspaceById() and
        // myWorkspaces() view of this workspace is now stale.
        List<WorkSpaceMember> members = workSpaceMemberRepository.findByWorkspace_Id(workspaceId);
        evictCachesForMembers(members, workspaceId);

        log.info("Workspace updated id={} by user={}", saved.getId(), user.getEmail());
        return workSpaceMapper.toDto(saved);
    }

    @Override
    @Cacheable(value = "workspace_by_id", key = "#id + ':' + #user.id")
    public WorkSpaceResponseDTO getWorkspaceById(Long id, User user) {
        WorkSpace ws = workSpaceReposiroty.findById(id)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Workspace not found"));

        boolean isOwner = ws.getWorkSpaceOwnerId().getId().equals(user.getId());
        boolean isMember = workSpaceMemberRepository.existsByWorkspace_IdAndUser_Id(id, user.getId());

        if (!isOwner && !isMember) {
            throw new CollaborationExceptions.ForbiddenException("You are not a member of this workspace");
        }

        WorkSpaceResponseDTO dto = workSpaceMapper.toDto(ws);
        dto.setStarred(starredWorkspaceRepository.existsByWorkspace_IdAndUser_Id(id, user.getId()));
        return dto;
    }

    // ---- cache eviction helpers ----

    private void evictCachesForMembers(List<WorkSpaceMember> members, Long workspaceId) {
        for (WorkSpaceMember m : members) {
            User u = m.getUser();
            evictCachesForUser(u.getEmail(), u.getId(), workspaceId);
        }
    }

    private void evictCachesForUser(String email, Long userId, Long workspaceId) {
        Cache myWorkspacesCache = cacheManager.getCache("workspaces_my");
        if (myWorkspacesCache != null) {
            myWorkspacesCache.evict(email);
        }
        Cache starredCache = cacheManager.getCache("workspaces_starred");
        if (starredCache != null) {
            starredCache.evict(userId);
        }
        Cache byIdCache = cacheManager.getCache("workspace_by_id");
        if (byIdCache != null) {
            byIdCache.evict(workspaceId + ":" + userId);
        }
    }

    @Override
    public void evictWorkspaceCachesFor(String email, Long userId, Long workspaceId) {
        evictCachesForUser(email, userId, workspaceId);
    }
}