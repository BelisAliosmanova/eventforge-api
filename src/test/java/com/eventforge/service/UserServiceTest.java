package com.eventforge.service;

import com.eventforge.dto.request.ChangePasswordRequest;
import com.eventforge.exception.InvalidEmailConfirmationLinkException;
import com.eventforge.exception.InvalidPasswordException;
import com.eventforge.model.User;
import com.eventforge.model.VerificationToken;
import com.eventforge.repository.UserRepository;
import com.eventforge.security.jwt.JWTService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @InjectMocks
    private UserService userService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationTokenService emailVerificationTokenService;
    @Mock
    private JWTService jwtService;
    @Mock
    private Utils utils;

    @Test
    void saveUserInDb() {
        User user = new User();
        userService.saveUserInDb(user);
        verify(userRepository, times(1)).save(user);
    }




    @Test
    void testGetUserByEmail_ExistingEmail() {
        String email = "test@example.com";
        User user = User.builder().username(email).build();
        when(userRepository.findByEmail(email)).thenReturn(user);

        User result = userService.getUserByEmail(email);

        assertEquals(user, result);
        verify(userRepository).findByEmail(email);
    }

    @Test
    void testGetUserByEmail_NonExistingEmail() {
        String email = "nonexisting@example.com";
        when(userRepository.findByEmail(email)).thenReturn(null);

        User result = userService.getUserByEmail(email);

        assertNull(result);
        verify(userRepository).findByEmail(email);
    }

    @Test
    void testGetLoggedUserByToken_ValidToken() {
        String token = "valid-token";
        String username = "test";
        User user = User.builder().username(username).build();

        String extractedTokenFromHeader = "extracted-token";
        when(jwtService.extractTokenValueFromHeader(token)).thenReturn(extractedTokenFromHeader);
        when(jwtService.extractUsernameFromToken(extractedTokenFromHeader)).thenReturn(username);

        when(userRepository.findByEmail(username)).thenReturn(user);

        User result = userService.getLoggedUserByToken(token);

        assertEquals(user, result);
        verify(jwtService).extractTokenValueFromHeader(token);
        verify(jwtService).extractUsernameFromToken(extractedTokenFromHeader);
        verify(userRepository).findByEmail(username);

        verifyNoMoreInteractions(jwtService, userRepository);
    }


    @Test
    void updateUserIsEnabledFieldAfterConfirmedEmail() {
        String token = "sampleToken";
        User user = new User();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(user);
        when(emailVerificationTokenService.getVerificationTokenByToken(token)).thenReturn(verificationToken);

        String result = userService.updateUserIsEnabledFieldAfterConfirmedEmail(token);

        assertTrue(user.getIsEnabled());
        verify(userRepository, times(1)).save(user);
        verify(emailVerificationTokenService, times(1)).deleteVerificationToken(verificationToken);
        assertNotNull(result);
        assertEquals("Успешно потвърдихте профилът си , вече можете да се впишете.", result);
    }


    @Test
    void saveUserVerificationToken_withoutExistingToken() {
        String token = "sampleToken";
        String type = "email";
        User user = new User();

        doAnswer((Answer<Void>) invocation -> {
            VerificationToken savedToken = invocation.getArgument(0);
            user.setVerificationToken(savedToken);
            return null;
        }).when(emailVerificationTokenService).saveVerificationToken(any());

        userService.saveUserVerificationToken(user, token, type);

        assertNotNull(user.getVerificationToken());
        assertEquals(token, user.getVerificationToken().getToken());
        assertEquals(type, user.getVerificationToken().getType());
        verify(emailVerificationTokenService, times(1)).saveVerificationToken(any());
    }

    @Test
    void saveUserVerificationToken_withExistingToken() {
        String token = "sampleToken";
        String type = "email";
        User user = new User();
        VerificationToken existingToken = new VerificationToken(token, user, type);
        user.setVerificationToken(existingToken);

        userService.saveUserVerificationToken(user, token, type);

        assertNotNull(user.getVerificationToken());
        assertEquals(token, user.getVerificationToken().getToken());
        assertEquals(type, user.getVerificationToken().getType());
    }


    @Test
    void testChangeAccountPassword_ShouldBeSuccessfullyChanged() {
        String token = "validToken";
        String oldPassword = "oldPassword";
        String newPassword = "newPassword";
        String confirmNewPassword = "newPassword";

        User user = User.builder().username("test").password("encodedPassword").build();
        when(userService.getLoggedUserByToken(token)).thenReturn(user);
        when(utils.isPasswordValid(oldPassword, user.getPassword())).thenReturn(true);
        when(utils.encodePassword(newPassword)).thenReturn("encodedNewPassword");

        String result = userService.changeAccountPassword(token, new ChangePasswordRequest(oldPassword, newPassword, confirmNewPassword));

        verify(utils).encodePassword(newPassword);
        assertThat(result).isEqualTo("Успешно променихте паролата си.");
    }

    @Test
    void testChangeAccountPassword_InvalidToken() {
        String token = "invalidToken";
        String oldPassword = "oldPassword";
        String newPassword = "newPassword";
        String confirmNewPassword = "newPassword";

        when(userService.getLoggedUserByToken(token)).thenReturn(null);
        String result = userService.changeAccountPassword(token, new ChangePasswordRequest(oldPassword, newPassword, confirmNewPassword));

        assertThat(result).isNull();
    }


    @Test
    public void generateNewRandomPasswordForUserViaVerificationToken() {
        // Arrange
        VerificationToken token = new VerificationToken();
        User user = new User();
        String newGeneratedPassword = utils.generateRandomPassword();

        when(utils.encodePassword(newGeneratedPassword)).thenReturn(newGeneratedPassword); // Stub the encodePassword method

        // Act
        String result = userService.generateNewRandomPasswordForUserViaVerificationToken(token , user);

        // Assert


        verify(utils).encodePassword(newGeneratedPassword); // Verify that encodePassword was called with any string argument
        verify(userRepository).save(user);
        verify(emailVerificationTokenService).deleteVerificationToken(token);

    }
    @Test
    void testSetApproveByAdminToTrue_UserPresent() {
        Long userId = 1L;
        User user = User.builder().id(userId).username("test@example.com").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.setApproveByAdminToTrue(userId);

        verify(userRepository).findById(userId);
    }

    @Test
    void testLockAccountById_UserPresent() {
        Long userId = 1L;
        User user = User.builder().id(userId).username("test@example.com").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.lockAccountById(userId);

        verify(userRepository).findById(userId);

    }

    @Test
    void testUnlockAccountById_UserPresent() {
        Long userId = 1L;
        User user = User.builder().id(userId).username("test@example.com").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.unlockAccountById(userId);

        verify(userRepository).findById(userId);
    }

    @Test
    void testChangeAccountPassword_invalidOldPassword() {
        String token = "token";
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .oldPassword("oldPassword")
                .build();
        User user = User.builder()
                .password("hashedPassword")
                .build();

        when(userService.getLoggedUserByToken(token)).thenReturn(user);
        when(utils.isPasswordValid(request.getOldPassword(), user.getPassword())).thenReturn(false);

        InvalidPasswordException exception = assertThrows(InvalidPasswordException.class,
                () -> userService.changeAccountPassword(token, request));

        assertEquals("Паролата не съответства на запазената в базата данни.", exception.getMessage());
    }

    @Test
    void testChangeAccountPassword_passwordsDoNotMatch() {
        String token = "token";
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .oldPassword("oldPassword")
                .newPassword("newPassword")
                .confirmNewPassword("differentPassword")
                .build();
        User user = User.builder()
                .password("hashedPassword")
                .build();

        when(userService.getLoggedUserByToken(token)).thenReturn(user);
        when(utils.isPasswordValid(request.getOldPassword(), user.getPassword())).thenReturn(true);

        InvalidPasswordException exception = assertThrows(InvalidPasswordException.class,
                () -> userService.changeAccountPassword(token, request));

        assertEquals("Новите пароли не съвпадат. Новата парола трябва да съответства на потвърдената парола.",
                exception.getMessage());
    }
}