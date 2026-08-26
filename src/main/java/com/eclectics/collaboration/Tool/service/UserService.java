package com.eclectics.collaboration.Tool.service;

import com.eclectics.collaboration.Tool.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface UserService {
    UserRegistrationResponseDTO createUser(UserRegistrationRequestDTO requestDTO, MultipartFile avatarUrl) throws IOException;
    UserLoginResponseDTO userLogin(UserLoginRequestDTO user);

    void markOnboardingComplete(String token);

    UserEmailDTO userSendResetPassword(UserEmailDTO user);
    void userUpdatePassword(String token, String newPassword);
    UserRegistrationRequestDTO updateUser(String token, UserRegistrationRequestDTO userDTO);
    void userConfirmAccount(String token);
    void userDeleteAccount(String token);

    TokenRefreshResponseDTO refreshToken(String requestToken);

    void logOutUser(String token);

    ScratchpadDTO getScratchpad(String token);

    ScratchpadDTO updateScratchpad(String token, String content);

    AvatarUploadResponseDTO uploadAvatar(String token, MultipartFile avatar) throws IOException;
}
