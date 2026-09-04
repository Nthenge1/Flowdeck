package com.eclectics.collaboration.Tool.service;

import com.eclectics.collaboration.Tool.dto.LabelResponseDTO;

import java.util.List;

public interface CardLabelService {
    List<LabelResponseDTO> getLabelsForCard(Long cardId);

    void addLabelToCard(Long cardId, Long labelId, Long requesterId);

    void removeLabelFromCard(Long cardId, Long labelId, Long requesterId);
}
