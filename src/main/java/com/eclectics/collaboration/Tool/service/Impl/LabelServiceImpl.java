package com.eclectics.collaboration.Tool.service.Impl;

import com.eclectics.collaboration.Tool.dto.LabelRequestDTO;
import com.eclectics.collaboration.Tool.dto.LabelResponseDTO;
import com.eclectics.collaboration.Tool.exception.CollaborationExceptions;
import com.eclectics.collaboration.Tool.mapper.LabelMapper;
import com.eclectics.collaboration.Tool.model.Boards;
import com.eclectics.collaboration.Tool.model.Label;
import com.eclectics.collaboration.Tool.repository.BoardMemberRepository;
import com.eclectics.collaboration.Tool.repository.BoardsRepository;
import com.eclectics.collaboration.Tool.repository.LabelRepository;
import com.eclectics.collaboration.Tool.service.LabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LabelServiceImpl implements LabelService {

    private final LabelRepository labelRepository;
    private final BoardsRepository boardRepository;
    private final BoardMemberRepository boardMemberRepository;
    private final LabelMapper labelMapper;

    @Override
    @Transactional
    public LabelResponseDTO createLabel(Long boardId, LabelRequestDTO dto, Long userId) {
        Boards board = boardRepository.findById(boardId)
                .orElseThrow(() ->
                        new CollaborationExceptions.ResourceNotFoundException("Board not found"));

        boardMemberRepository
                .findByBoardIdAndUserId(boardId, userId)
                .orElseThrow(() ->
                        new CollaborationExceptions.ForbiddenException("User is not a member of the board"));

        Label label = labelMapper.toEntity(dto, board);
        return labelMapper.toDto(labelRepository.save(label));
    }

    @Override
    public List<LabelResponseDTO> getLabelsByBoard(Long boardId) {
        return labelRepository.findByBoardId(boardId)
                .stream()
                .map(labelMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void deleteLabel(Long labelId, Long userId) {
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() ->
                        new CollaborationExceptions.ResourceNotFoundException("Label not found"));

        boardMemberRepository
                .findByBoardIdAndUserId(label.getBoard().getId(), userId)
                .orElseThrow(() ->
                        new CollaborationExceptions.ForbiddenException("User is not a member of the board"));

        labelRepository.delete(label);
    }

    @Override
    public boolean labelExistsOnBoard(Long boardId, Long labelId) {
        return labelRepository.existsByIdAndBoardId(labelId, boardId);
    }
}