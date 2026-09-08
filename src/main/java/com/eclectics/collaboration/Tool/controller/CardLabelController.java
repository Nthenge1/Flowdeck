package com.eclectics.collaboration.Tool.controller;

import com.eclectics.collaboration.Tool.dto.LabelResponseDTO;
import com.eclectics.collaboration.Tool.response.ResponseHandler;
import com.eclectics.collaboration.Tool.security.CustomUserDetails;
import com.eclectics.collaboration.Tool.service.CardLabelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cards/{cardId}/labels")
@RequiredArgsConstructor
@Tag(name = "Card Labels", description = "Attach and remove labels on a card")
public class CardLabelController {

    private final CardLabelService cardLabelService;
    private final HttpServletRequest request;

    @Operation(summary = "Get all labels applied to a card")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Labels fetched successfully"),
            @ApiResponse(responseCode = "404", description = "Card not found")
    })
    @GetMapping
    public ResponseEntity<Object> getLabelsForCard(
            @PathVariable Long cardId) {

        List<LabelResponseDTO> labels = cardLabelService.getLabelsForCard(cardId);

        return ResponseHandler.generateResponse(
                "Labels fetched successfully",
                HttpStatus.OK,
                labels,
                request.getRequestURI()
        );
    }

//    @Operation(summary = "Add a label to a card")
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "Label added successfully"),
//            @ApiResponse(responseCode = "400", description = "Label already applied or from a different board"),
//            @ApiResponse(responseCode = "404", description = "Card or label not found")
//    })
//    @PostMapping("/{labelId}")
//    public ResponseEntity<Object> addLabelToCard(
//            @PathVariable Long cardId,
//            @PathVariable Long labelId,
//            @AuthenticationPrincipal CustomUserDetails userDetails) {
//
//        cardLabelService.addLabelToCard(cardId, labelId, userDetails.getId());
//
//        return ResponseHandler.generateResponse(
//                "Label added successfully",
//                HttpStatus.OK,
//                null,
//                request.getRequestURI()
//        );
//    }

    @Operation(summary = "Remove a label from a card")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Label removed successfully"),
            @ApiResponse(responseCode = "404", description = "Card, label, or card-label link not found")
    })
    @DeleteMapping("/{labelId}")
    public ResponseEntity<Object> removeLabelFromCard(
            @PathVariable Long cardId,
            @PathVariable Long labelId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        cardLabelService.removeLabelFromCard(cardId, labelId, userDetails.getId());

        return ResponseHandler.generateResponse(
                "Label removed successfully",
                HttpStatus.OK,
                null,
                request.getRequestURI()
        );
    }
}