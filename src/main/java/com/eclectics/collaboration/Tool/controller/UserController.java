package com.eclectics.collaboration.Tool.controller;

import com.eclectics.collaboration.Tool.dto.*;
import com.eclectics.collaboration.Tool.exception.CollaborationExceptions;
import com.eclectics.collaboration.Tool.response.ResponseHandler;
import com.eclectics.collaboration.Tool.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
@Slf4j
@Tag(name = "User Management", description = "Operations for user registration, authentication, and profile management")
public class UserController {

    private final UserService userService;
    private final HttpServletRequest request;

    @Operation(summary = "Register a new user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Registration successful, verification email sent"),
            @ApiResponse(responseCode = "400", description = "Invalid registration data")
    })
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> registerUser(
            @RequestBody UserRegistrationRequestDTO requestDTO
    ) throws java.io.IOException {
        log.info("Register request received → email={}", requestDTO.getEmail());
        UserRegistrationResponseDTO response = userService.createUser(requestDTO, null);
        return ResponseHandler.generateResponse(
                "Registration successful, open email and confirm your account",
                HttpStatus.CREATED, response, request.getRequestURI()
        );
    }

    @Operation(summary = "Authenticate user and log in")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<Object> loginUser(
            @RequestBody UserLoginRequestDTO loginRequest
    ) {
        UserLoginResponseDTO response = userService.userLogin(loginRequest);
        return ResponseHandler.generateResponse("Login successful", HttpStatus.OK, response, request.getRequestURI());
    }

    @Operation(summary = "Confirm user account via token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account confirmed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or broken confirmation token"),
            @ApiResponse(responseCode = "409", description = "Account is already confirmed")
    })
    @GetMapping("/confirm")
    public ResponseEntity<Object> confirmAccount(
            @RequestParam("token") String token
    ) {
        try {
            userService.userConfirmAccount(token);
            return ResponseHandler.generateResponse(
                    "Account confirmed successfully, please login",
                    HttpStatus.OK, null, request.getRequestURI()
            );
        } catch (CollaborationExceptions.ResourceAlreadyExistsException e) {
            return ResponseHandler.generateResponse(
                    "Account already confirmed, please login",
                    HttpStatus.CONFLICT, null, request.getRequestURI()
            );
        } catch (Exception e) {
            return ResponseHandler.generateResponse(
                    "Account confirmation failed, please try again",
                    HttpStatus.BAD_REQUEST, null, request.getRequestURI()
            );
        }
    }

    @Operation(summary = "Resend account confirmation email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Confirmation email resent"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "409", description = "Account already confirmed")
    })
    @PostMapping("/resend-confirmation")
    public ResponseEntity<Object> resendConfirmation(
            @RequestBody UserEmailDTO userEmailDTO
    ) {
        try {
            userService.resendConfirmationEmail(userEmailDTO.getEmail());
            return ResponseHandler.generateResponse(
                    "Confirmation email resent, please check your inbox",
                    HttpStatus.CREATED, null, request.getRequestURI()
            );
        } catch (CollaborationExceptions.ResourceAlreadyExistsException e) {
            return ResponseHandler.generateResponse(
                    "Account already confirmed, please login",
                    HttpStatus.CONFLICT, null, request.getRequestURI()
            );
        } catch (CollaborationExceptions.ResourceNotFoundException e) {
            return ResponseHandler.generateResponse(
                    "No account found with that email",
                    HttpStatus.NOT_FOUND, null, request.getRequestURI()
            );
        }
    }

    @Operation(summary = "Request a password reset email link")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Password reset request processed successfully"),
            @ApiResponse(responseCode = "404", description = "User email not found")
    })
    @PostMapping("/reset-password-request")
    public ResponseEntity<Object> sendResetPasswordEmail(
            @RequestBody UserEmailDTO userEmailDTO
    ) {
        UserEmailDTO response = userService.userSendResetPassword(userEmailDTO);
        return ResponseHandler.generateResponse("Reset password send", HttpStatus.CREATED, response, request.getRequestURI());
    }

    @Operation(summary = "Reset password using token link confirmation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Password updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired reset token")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Object> resetPassword(
            @RequestParam("token") String token,
            @RequestParam("newPassword") String newPassword
    ) {
        userService.userUpdatePassword(token, newPassword);
        return ResponseHandler.generateResponse("Password updated successfully.", HttpStatus.CREATED, null, request.getRequestURI());
    }

    @Operation(summary = "Mark the onboarding tour as completed for the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Onboarding marked complete"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PatchMapping("/onboarding-complete")
    public ResponseEntity<Object> completeOnboarding(
            @RequestHeader("Authorization") String tokenHeader
    ) {
        String token = tokenHeader.replace("Bearer ", "");
        userService.markOnboardingComplete(token);
        return ResponseHandler.generateResponse("Onboarding marked complete", HttpStatus.OK, null, request.getRequestURI());
    }

    @Operation(summary = "Update account profile details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User profile updated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or missing token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping("/update")
    public ResponseEntity<Object> updateProfile(
            @RequestHeader("Authorization") String tokenHeader,
            @RequestBody UserRegistrationRequestDTO userDTO
    ) {
        String token = tokenHeader.replace("Bearer ", "");
        UserRegistrationRequestDTO response = userService.updateUser(token, userDTO);
        return ResponseHandler.generateResponse("User updated successfully", HttpStatus.CREATED, response, request.getRequestURI());
    }

    @Operation(summary = "Log out user and invalidate current session token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully logged out"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PutMapping("/logout")
    public ResponseEntity<Object> userLogOut(
            @RequestHeader("Authorization") String tokenHeader
    ) {
        String token = tokenHeader.replace("Bearer ", "");
        userService.logOutUser(token);
        return ResponseHandler.generateResponse("Logged Out", HttpStatus.OK, null, request.getRequestURI());
    }

    @Operation(summary = "Permanently delete user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/delete")
    public ResponseEntity<Object> deleteAccount(
            @RequestHeader("Authorization") String tokenHeader
    ) {
        String token = tokenHeader.replace("Bearer ", "");
        userService.userDeleteAccount(token);
        return ResponseHandler.generateResponse("Account deleted successfully", HttpStatus.OK, null, request.getRequestURI());
    }

    @Operation(summary = "Exchange a valid refresh token for a new access token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Refresh token invalid or expired")
    })
    @PostMapping("/refresh")
    public ResponseEntity<Object> refreshToken(
            @RequestBody TokenRefreshRequestDTO requestDTO
    ) {
        TokenRefreshResponseDTO response = userService.refreshToken(requestDTO.getRefreshToken());
        return ResponseHandler.generateResponse("Token refreshed successfully", HttpStatus.OK, response, request.getRequestURI());
    }

    @Operation(summary = "Get the current user's personal scratchpad notes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Scratchpad fetched successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/scratchpad")
    public ResponseEntity<Object> getScratchpad(
            @RequestHeader("Authorization") String tokenHeader
    ) {
        String token = tokenHeader.replace("Bearer ", "");
        ScratchpadDTO response = userService.getScratchpad(token);
        return ResponseHandler.generateResponse("Scratchpad fetched", HttpStatus.OK, response, request.getRequestURI());
    }

    @Operation(summary = "Save the current user's personal scratchpad notes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Scratchpad saved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PutMapping("/scratchpad")
    public ResponseEntity<Object> updateScratchpad(
            @RequestHeader("Authorization") String tokenHeader,
            @RequestBody ScratchpadDTO requestDTO
    ) {
        String token = tokenHeader.replace("Bearer ", "");
        ScratchpadDTO response = userService.updateScratchpad(token, requestDTO.getContent());
        return ResponseHandler.generateResponse("Scratchpad saved", HttpStatus.OK, response, request.getRequestURI());
    }

    @Operation(summary = "Upload or replace the current user's profile picture")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avatar uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file (too large, wrong type, or missing)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or missing token"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> uploadAvatar(
            @RequestHeader("Authorization") String tokenHeader,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) throws java.io.IOException {
        String token = tokenHeader.replace("Bearer ", "");
        AvatarUploadResponseDTO response = userService.uploadAvatar(token, file);
        return ResponseHandler.generateResponse("Avatar uploaded successfully", HttpStatus.OK, response, request.getRequestURI());
    }
}