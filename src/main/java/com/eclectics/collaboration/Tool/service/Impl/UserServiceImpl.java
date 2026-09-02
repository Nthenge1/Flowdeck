package com.eclectics.collaboration.Tool.service.Impl;

import com.eclectics.collaboration.Tool.dto.*;
import com.eclectics.collaboration.Tool.exception.CollaborationExceptions;
import com.eclectics.collaboration.Tool.mapper.UserMapper;
import com.eclectics.collaboration.Tool.model.RefreshToken;
import com.eclectics.collaboration.Tool.model.User;
import com.eclectics.collaboration.Tool.repository.UserRespository;
import com.eclectics.collaboration.Tool.security.JwtUtil;
import com.eclectics.collaboration.Tool.service.RefreshTokenService;
import com.eclectics.collaboration.Tool.service.UserService;
import com.eclectics.collaboration.Tool.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final UserRespository userRepository;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final OSSService ossService;
    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenService refreshTokenService;

    private static final long MAX_AVATAR_SIZE_BYTES = 5L * 1024 * 1024; // 5MB
    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private String getFileExtension(String filename) {
        if (filename != null && filename.lastIndexOf(".") != -1) {
            return filename.substring(filename.lastIndexOf("."));
        }
        return "";
    }

    @Override
    public UserRegistrationResponseDTO createUser(UserRegistrationRequestDTO requestDTO, MultipartFile avatarUrl) throws IOException {

        if (userRepository.existsByEmail(requestDTO.getEmail())) {
            throw new CollaborationExceptions.ResourceAlreadyExistsException(
                    "An account with email " + requestDTO.getEmail() + " already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = mapper.toEntity(requestDTO);

        user.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
        user.setEnabled(false);
        user.setCreatedAt(now);
        User savedUser = userRepository.save(user);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            String ext = getFileExtension(avatarUrl.getOriginalFilename());

            String path = "SYNCBOARD/avatar/" + savedUser.getId() + "-" + UUID.randomUUID() + ext;
            String uploadedUrl = ossService.uploadFile(path, avatarUrl.getInputStream());
            savedUser.setAvatarUrl(uploadedUrl);
            userRepository.save(savedUser);
        }
        sendConfirmationEmail(savedUser);
        return new UserRegistrationResponseDTO(savedUser.getFirstName(), null);
    }

    @Override
    public UserLoginResponseDTO userLogin(UserLoginRequestDTO loginRequestDTO) {

        User user = userRepository.findByEmail(loginRequestDTO.getEmail())
                .orElseThrow(() -> new CollaborationExceptions.BadRequestException(
                        "Your details aren't in our system, please register"));

        if (!passwordEncoder.matches(loginRequestDTO.getPassword(), user.getPassword())) {
            throw new CollaborationExceptions.BadRequestException("Invalid email or password");
        }

        if (!user.isEnabled()) {
            throw new CollaborationExceptions.BadRequestException("Account not confirmed. Please check your email.");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getEmail());

        return new UserLoginResponseDTO(user.getId(), user.getEmail(), token, user.getFirstName(), user.getSirName(), user.getAvatarUrl(), refreshToken.getToken(), user.getRole(), user.isHasSeenOnboarding());
    }

    @Override
    public void markOnboardingComplete(String token) {
        String email = jwtUtil.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("User not found"));

        user.setHasSeenOnboarding(true);
        userRepository.save(user);
    }

    @Override
    public UserEmailDTO userSendResetPassword(UserEmailDTO userEmailDTO) {
        return userRepository.findByEmail(userEmailDTO.getEmail())
                .map(user -> {
                    String resetToken = jwtUtil.generateResetPasswordToken(user.getEmail());
                    String resetLink = "https://flowdeckk.netlify.app/reset-password?token=" + resetToken;

                    emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

                    userEmailDTO.setToken(resetToken);
                    return userEmailDTO;
                })
                .orElse(userEmailDTO);
    }

    @Override
    public void userUpdatePassword(String token, String newPassword) {

        String email = jwtUtil.validateAndExtractEmailFromResetToken(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    @CacheEvict(value = "user_scratchpad", key = "#root.target.extractEmail(#token)")
    public UserRegistrationRequestDTO updateUser(String token, UserRegistrationRequestDTO userDTO) {
        String email = jwtUtil.extractEmail(token);

        User existingUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("User not found"));

        if (userDTO.getFirstName() != null) existingUser.setFirstName(userDTO.getFirstName());
        if (userDTO.getSirName() != null) existingUser.setSirName(userDTO.getSirName());
        if (userDTO.getAvatarUrl() != null) existingUser.setAvatarUrl(userDTO.getAvatarUrl());

        User updatedUser = userRepository.save(existingUser);
        return mapper.toResponse(updatedUser);
    }

    @Override
    public void resendConfirmationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("User not found"));

        if (user.isEnabled()) {
            throw new CollaborationExceptions.ResourceAlreadyExistsException("Account already confirmed, please login");
        }

        sendConfirmationEmail(user);
    }

    @Override
    public void userConfirmAccount(String token) {

        String email = jwtUtil.validateAndExtractEmailFromConfirmationToken(token);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("User not found"));

        if (user.isEnabled()) {
            throw new CollaborationExceptions.ResourceAlreadyExistsException("Account already confirmed, please login");
        }

        user.setEnabled(true);
        userRepository.save(user);
        log.info("Account confirmed for email={}", email);
    }

    @Override
    @CacheEvict(value = "user_scratchpad", key = "#root.target.extractEmail(#token)")
    public void userDeleteAccount(String token) {
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        String tokenId = jwtUtil.extractId(jwt);

        Boolean isRevoked = redisTemplate.hasKey("revoked_token:" + tokenId);
        if (Boolean.TRUE.equals(isRevoked)) {
            throw new CollaborationExceptions.UnauthorizedException("Session expired. Please log in again.");
        }

        String email = jwtUtil.extractEmail(jwt);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("User not found"));

        userRepository.delete(user);

        SecurityContextHolder.clearContext();
    }

    @Override
    public TokenRefreshResponseDTO refreshToken(String requestToken) {

        RefreshToken refreshToken = refreshTokenService.findByToken(requestToken)
                .orElseThrow(() -> new CollaborationExceptions.UnauthorizedException("Refresh token not found. Please log in again."));

        refreshTokenService.verifyExpiration(refreshToken);

        String email = refreshToken.getUser().getEmail();
        String newAccessToken = jwtUtil.generateToken(email, refreshToken.getUser().getRole());

        return new TokenRefreshResponseDTO(newAccessToken, requestToken);
    }

    @Override
    public void logOutUser(String tokenHeader) {
        if (tokenHeader != null && tokenHeader.startsWith("Bearer ")) {
            String jwt = tokenHeader.substring(7);

            try {
                String tokenId = jwtUtil.extractId(jwt);
                Date expiration = jwtUtil.extractExpiration(jwt);
                long ttl = expiration.getTime() - System.currentTimeMillis();

                if (ttl > 0) {
                    redisTemplate.opsForValue().set(
                            "revoked_token:" + tokenId,
                            "true",
                            Duration.ofMillis(ttl)
                    );
                }

                String email = jwtUtil.extractEmail(jwt);
                refreshTokenService.deleteByUser(email);

            } catch (Exception e) {
                log.error("Could not blacklist token: {}", e.getMessage());
            }
        }

        SecurityContextHolder.clearContext();
    }

    @Override
    @Cacheable(value = "user_scratchpad", key = "#root.target.extractEmail(#token)")
    public ScratchpadDTO getScratchpad(String token) {
        String email = jwtUtil.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("User not found"));
        return new ScratchpadDTO(user.getScratchpadContent(), user.getScratchpadUpdatedAt());
    }

    @Override
    @CachePut(value = "user_scratchpad", key = "#root.target.extractEmail(#token)")
    public ScratchpadDTO updateScratchpad(String token, String content) {
        String email = jwtUtil.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("User not found"));

        user.setScratchpadContent(content);
        user.setScratchpadUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        return new ScratchpadDTO(user.getScratchpadContent(), user.getScratchpadUpdatedAt());
    }

    @Override
    @CacheEvict(value = "user_scratchpad", key = "#root.target.extractEmail(#token)")
    public AvatarUploadResponseDTO uploadAvatar(String token, MultipartFile avatar) throws IOException {
        if (avatar == null || avatar.isEmpty()) {
            throw new CollaborationExceptions.BadRequestException("No file was uploaded.");
        }

        if (avatar.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new CollaborationExceptions.BadRequestException("Image must be smaller than 5MB.");
        }

        String contentType = avatar.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new CollaborationExceptions.BadRequestException("Only JPEG, PNG, WEBP, or GIF images are allowed.");
        }

        String email = jwtUtil.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CollaborationExceptions.ResourceNotFoundException("User not found"));

        String oldAvatarUrl = user.getAvatarUrl();

        String ext = getFileExtension(avatar.getOriginalFilename());
        String path = "SYNCBOARD/avatar/" + user.getId() + "-" + UUID.randomUUID() + ext;
        String uploadedUrl = ossService.uploadFile(path, avatar.getInputStream());

        user.setAvatarUrl(uploadedUrl);
        userRepository.save(user);

        if (oldAvatarUrl != null && !oldAvatarUrl.isBlank()) {
            try {
                String oldObjectName = extractObjectNameFromUrl(oldAvatarUrl);
                if (oldObjectName != null) {
                    ossService.deleteFile(oldObjectName);
                }
            } catch (Exception e) {
                log.warn("Failed to delete old avatar '{}': {}", oldAvatarUrl, e.getMessage());
            }
        }

        return new AvatarUploadResponseDTO(uploadedUrl);
    }

    public String extractEmail(String token) {
        String jwt = token != null && token.startsWith("Bearer ") ? token.substring(7) : token;
        return jwtUtil.extractEmail(jwt);
    }

    private String extractObjectNameFromUrl(String url) {
        String prefix = "https://" + ossService.getBucketName() + "."
                + ossService.getEndpoint().replace("https://", "") + "/";
        if (url != null && url.startsWith(prefix)) {
            return url.substring(prefix.length());
        }
        return null;
    }

    // New private helper, extracted from createUser's tail
    private void sendConfirmationEmail(User user) {
        String token = jwtUtil.generateEmailConfirmationToken(user.getEmail());
        String confirmLink = "https://flowdeckk.netlify.app/confirm-account?token=" + token;

        try {
            emailService.sendAccountConfirmationEmail(user.getEmail(), confirmLink);
        } catch (Exception e) {
            log.error("Failed to send Email to user, but user saved", e);
        }
    }
}
