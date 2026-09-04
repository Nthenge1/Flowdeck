package com.eclectics.collaboration.Tool.service.Impl;

import com.eclectics.collaboration.Tool.dto.LabelResponseDTO;
import com.eclectics.collaboration.Tool.exception.CollaborationExceptions;
import com.eclectics.collaboration.Tool.mapper.LabelMapper;
import com.eclectics.collaboration.Tool.model.Card;
import com.eclectics.collaboration.Tool.model.CardLabel;
import com.eclectics.collaboration.Tool.model.Label;
import com.eclectics.collaboration.Tool.repository.BoardMemberRepository;
import com.eclectics.collaboration.Tool.repository.CardLabelRepository;
import com.eclectics.collaboration.Tool.repository.CardRepository;
import com.eclectics.collaboration.Tool.repository.LabelRepository;
import com.eclectics.collaboration.Tool.service.CardLabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class CardLabelServiceImpl implements CardLabelService {

    private final CardRepository cardRepository;
    private final LabelRepository labelRepository;
    private final CardLabelRepository cardLabelRepository;
    private final BoardMemberRepository boardMemberRepository;
    private final LabelMapper labelMapper;

    @Override
    public List<LabelResponseDTO> getLabelsForCard(Long cardId) {
        return cardLabelRepository.findByCardId(cardId)
                .stream()
                .map(cl -> labelMapper.toDto(cl.getLabel()))
                .toList();
    }

    @Override
    public void addLabelToCard(Long cardId, Long labelId, Long requesterId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Card not found"));

        Long boardId = card.getList().getBoard().getId();

        boardMemberRepository
                .findByBoardIdAndUserId(boardId, requesterId)
                .orElseThrow(() -> new CollaborationExceptions.ForbiddenException("Not a board member"));

        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Label not found"));

        if (!label.getBoard().getId().equals(boardId)) {
            throw new CollaborationExceptions.BadRequestException("Label does not belong to this card's board");
        }

        if (cardLabelRepository.existsByCardIdAndLabelId(cardId, labelId)) {
            throw new CollaborationExceptions.BadRequestException("Label already applied to this card");
        }

        cardLabelRepository.save(new CardLabel(card, label));
    }

    @Override
    public void removeLabelFromCard(Long cardId, Long labelId, Long requesterId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Card not found"));

        Long boardId = card.getList().getBoard().getId();

        boardMemberRepository
                .findByBoardIdAndUserId(boardId, requesterId)
                .orElseThrow(() -> new CollaborationExceptions.ForbiddenException("Not a board member"));

        CardLabel cardLabel = cardLabelRepository.findByCardIdAndLabelId(cardId, labelId)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("Label not applied to this card"));

        cardLabelRepository.delete(cardLabel);
    }
}