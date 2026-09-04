package com.eclectics.collaboration.Tool.controller;

import com.eclectics.collaboration.Tool.dto.LabelRequestDTO;
import com.eclectics.collaboration.Tool.dto.LabelResponseDTO;
import com.eclectics.collaboration.Tool.security.CustomUserDetails;
import com.eclectics.collaboration.Tool.service.LabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;

    @GetMapping("/boards/{boardId}/labels/{labelId}/exists")
    public ResponseEntity<Boolean> labelExists(
            @PathVariable Long boardId,
            @PathVariable Long labelId) {
        return ResponseEntity.ok(labelService.labelExistsOnBoard(boardId, labelId));
    }

    @PostMapping("/boards/{boardId}/labels")
    public ResponseEntity<LabelResponseDTO> createLabel(
            @PathVariable Long boardId,
            @RequestBody LabelRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(labelService.createLabel(boardId, dto, userDetails.getId()));
    }

    @GetMapping("/boards/{boardId}/labels")
    public ResponseEntity<List<LabelResponseDTO>> getLabels(@PathVariable Long boardId) {
        return ResponseEntity.ok(labelService.getLabelsByBoard(boardId));
    }

    @DeleteMapping("/labels/{labelId}")
    public ResponseEntity<Void> deleteLabel(
            @PathVariable Long labelId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        labelService.deleteLabel(labelId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}