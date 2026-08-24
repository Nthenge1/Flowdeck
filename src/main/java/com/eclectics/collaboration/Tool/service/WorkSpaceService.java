package com.eclectics.collaboration.Tool.service;

import com.eclectics.collaboration.Tool.dto.WorkSpaceRequestDTO;
import com.eclectics.collaboration.Tool.dto.WorkSpaceResponseDTO;
import com.eclectics.collaboration.Tool.model.User;
import com.eclectics.collaboration.Tool.model.WorkSpace;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface WorkSpaceService {
    WorkSpaceResponseDTO createWorkspace(User user, WorkSpaceRequestDTO request);

    List<WorkSpaceResponseDTO> myWorkspaces();

    @Transactional
    void deleteWorkspace(Long workspaceId, User user);

    @Transactional
    boolean toggleStarWorkspace(Long workspaceId, Long userId);

    List<WorkSpaceResponseDTO> getStarredWorkspaces(Long userId);

    @Transactional
    void leaveWorkspace(Long workspaceId, User user);

    @Transactional
    WorkSpaceResponseDTO updateWorkspace(Long workspaceId, User user, WorkSpaceRequestDTO request);

    WorkSpaceResponseDTO getWorkspaceById(Long id, User user);

    void evictWorkspaceCachesFor(String email, Long userId, Long workspaceId);
}
