package com.eclectics.collaboration.Tool.controller;

import com.eclectics.collaboration.Tool.dto.*;
import com.eclectics.collaboration.Tool.security.CustomUserDetails;
import com.eclectics.collaboration.Tool.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "CRUD and movement operations for task cards")
public class CardController {

    private final CardService cardService;

    @Operation(summary = "Get all cards belonging to a list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cards retrieved successfully")
    })
    @GetMapping("/lists/{listId}/cards")
    public ResponseEntity<List<CardResponseDTO>> getCardsByList(@PathVariable Long listId) {
        return ResponseEntity.ok(cardService.getCardsByList(listId));
    }

    @Operation(summary = "Get a card by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Card fetched successfully"),
            @ApiResponse(responseCode = "404", description = "Card not found")
    })
    @GetMapping("/{cardId}")
    public ResponseEntity<CardResponseDTO> getCardById(@PathVariable Long cardId) {
        return ResponseEntity.ok(cardService.getCardById(cardId));
    }

    @Operation(summary = "Create a new card")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Card created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping()
    public ResponseEntity<CardResponseDTO> createCard(
            @RequestBody CardRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cardService.createCard(dto.getListId(), dto, userDetails.getId()));
    }

    @Operation(summary = "Update an existing card's details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Card updated successfully"),
            @ApiResponse(responseCode = "404", description = "Card not found")
    })
    @PutMapping("/{cardId}")
    public ResponseEntity<CardResponseDTO> updateCard(
            @PathVariable Long cardId,
            @RequestBody CardRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(cardService.updateCard(cardId, userDetails.getId(), dto));
    }

    @Operation(summary = "Move a card to a different list or position")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Card moved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid movement data")
    })
    @PutMapping("/{cardId}/move")
    public ResponseEntity<CardResponseDTO> moveCard(
            @PathVariable Long cardId,
            @RequestBody CardMoveRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(cardService.moveCard(cardId, dto, userDetails.getId()));
    }

    @Operation(summary = "Delete a card")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Card deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Card not found")
    })
    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> deleteCard(
            @PathVariable Long cardId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        cardService.deleteCard(cardId, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reassign a card to a different board member")
    @PutMapping("/{cardId}/assignee")
    public ResponseEntity<CardResponseDTO> reassignCard(
            @PathVariable Long cardId,
            @RequestBody CardAssigneeRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(cardService.reassignCard(cardId, dto.getUserId(), userDetails.getId()));
    }

    @Operation(summary = "Attach a label to a card")
    @PostMapping("/{cardId}/labels/{labelId}")
    public ResponseEntity<Void> attachLabel(
            @PathVariable Long cardId,
            @PathVariable Long labelId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        cardService.attachLabel(cardId, labelId, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

//    @Operation(summary = "Detach a label from a card")
//    @DeleteMapping("/{cardId}/labels/{labelId}")
//    public ResponseEntity<Void> detachLabel(
//            @PathVariable Long cardId,
//            @PathVariable Long labelId,
//            @AuthenticationPrincipal CustomUserDetails userDetails) {
//        cardService.detachLabel(cardId, labelId, userDetails.getId());
//        return ResponseEntity.noContent().build();
//    }

    @Operation(summary = "Get all cards assigned to the current user across every board")
    @GetMapping("/assigned-to-me")
    public ResponseEntity<List<AssignedCardResponseDTO>> getAssignedCards(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(cardService.getAssignedCards(userDetails.getId()));
    }
}