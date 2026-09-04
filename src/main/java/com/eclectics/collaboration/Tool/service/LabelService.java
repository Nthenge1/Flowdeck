package com.eclectics.collaboration.Tool.service;

import com.eclectics.collaboration.Tool.dto.LabelRequestDTO;
import com.eclectics.collaboration.Tool.dto.LabelResponseDTO;

import java.util.List;

public interface LabelService {
    LabelResponseDTO createLabel(Long boardId, LabelRequestDTO dto, Long userId);
    List<LabelResponseDTO> getLabelsByBoard(Long boardId);
    void deleteLabel(Long labelId, Long userId);

    boolean labelExistsOnBoard(Long boardId, Long labelId);
}
