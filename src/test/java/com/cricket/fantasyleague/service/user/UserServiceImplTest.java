package com.cricket.fantasyleague.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.season.UserOverallStats;
import com.cricket.fantasyleague.payload.dto.UserDto;
import com.cricket.fantasyleague.util.AppConstants;
import com.cricket.fantasyleague.util.SnowflakeIdGenerator;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @BeforeAll
    static void initSnowflakeGenerator() {
        new SnowflakeIdGenerator(1);
    }

    @Mock
    private UserPersistServiceImpl persistService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void createUserInitializesTransfersFromActiveLeague() {
        UserServiceImpl service = new UserServiceImpl(persistService, passwordEncoder, 12);
        UserDto dto = new UserDto();
        dto.setEmail("test@example.com");
        dto.setUsername("testuser");
        dto.setPassword("password");

        when(persistService.findByEmail(dto.getEmail())).thenReturn(null);
        when(persistService.findByUsername(dto.getUsername())).thenReturn(null);
        when(persistService.saveUser(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createUser(dto);

        ArgumentCaptor<UserOverallStats> statsCaptor = ArgumentCaptor.forClass(UserOverallStats.class);
        verify(persistService).saveOverallStats(statsCaptor.capture());

        assertThat(statsCaptor.getValue().getTransferleft()).isEqualTo(60);
        assertThat(statsCaptor.getValue().getBoosterleft()).isEqualTo(AppConstants.FantasyPoints.TOTAL_BOOSTER);
    }

    @Test
    void updatePasswordHashesAndPersistsExistingUser() {
        UserServiceImpl service = new UserServiceImpl(persistService, passwordEncoder, 12);
        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("old-password");
        when(persistService.findByEmail("test@example.com")).thenReturn(user);

        service.updatePassword("test@example.com", "newpass1");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(persistService).saveUser(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isNotEqualTo("newpass1");
        assertThat(passwordEncoder.matches("newpass1", userCaptor.getValue().getPassword())).isTrue();
    }

    @Test
    void totalTransfersAreConfiguredByLeague() {
        assertThat(AppConstants.FantasyPoints.totalTransferForLeague(2)).isEqualTo(120);
        assertThat(AppConstants.FantasyPoints.totalTransferForLeague(12)).isEqualTo(60);
        assertThat(AppConstants.FantasyPoints.totalTransferForLeague(3)).isEqualTo(40);
        assertThat(AppConstants.FantasyPoints.totalTransferForLeague(999)).isEqualTo(120);
    }
}
