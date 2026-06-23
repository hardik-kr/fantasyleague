package com.cricket.fantasyleague.service.user;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.cricket.fantasyleague.config.AppConfig;
import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.season.UserOverallStats;
import com.cricket.fantasyleague.exception.ResourceAlreadyExist;
import com.cricket.fantasyleague.exception.ResourceNotFoundException;
import com.cricket.fantasyleague.payload.dto.UserDto;
import com.cricket.fantasyleague.util.AppConstants;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {

    private final UserPersistServiceImpl persistService;
    private final PasswordEncoder passwordEncoder;
    private final AppConfig appConfig;

    public UserServiceImpl(
            UserPersistServiceImpl persistService,
            PasswordEncoder passwordEncoder,
            AppConfig appConfig) {
        this.persistService = persistService;
        this.passwordEncoder = passwordEncoder;
        this.appConfig = appConfig;
    }

    @Override
    public void validateNewUser(UserDto inpuser) {
        User existByEmail = persistService.findByEmail(inpuser.getEmail());
        if (existByEmail != null) {
            throw new ResourceAlreadyExist(AppConstants.user.ALREADY_EXIST, "email", inpuser.getEmail());
        }
        User existByUsername = persistService.findByUsername(inpuser.getUsername());
        if (existByUsername != null) {
            throw new ResourceAlreadyExist(AppConstants.user.ALREADY_EXIST, "username", inpuser.getUsername());
        }
    }

    @Override
    public void createUser(UserDto inpuser) {
        validateNewUser(inpuser);
        User user = buildUserFromDto(inpuser);
        persistService.saveUser(user);
        initializeUserOverallStats(user);
    }

    private void initializeUserOverallStats(User user) {
        Integer transfer = appConfig.getTotalTransfer();
        Integer booster = appConfig.getTotalBooster();
        UserOverallStats stats = new UserOverallStats(user, 0.0, 0.0, booster, transfer);
        persistService.saveOverallStats(stats);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User userobj = persistService.findByEmail(username);
        if (userobj == null) {
            userobj = persistService.findByUsername(username);
        }
        if (userobj == null) {
            throw new UsernameNotFoundException(String.format(AppConstants.user.USER_NOT_FOUND, username));
        }
        return buildDtoFromUser(userobj);
    }

    @Override
    public UserDetails getUserByUserName(String username) {
        return loadUserByUsername(username);
    }

    @Override
    public List<User> getAllUser() {
        return persistService.findAllUsers();
    }

    @Override
    public void updatePassword(String email, String password) {
        User user = persistService.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + email);
        }
        user.setPassword(passwordEncoder.encode(password));
        persistService.saveUser(user);
    }

    private User buildUserFromDto(UserDto inpuser) {
        User userobj = new User();
        userobj.setUsername(inpuser.getUsername());
        userobj.setFirstname(inpuser.getFirstname());
        userobj.setLastname(inpuser.getLastname());
        userobj.setEmail(inpuser.getEmail());
        userobj.setFavteam(StringUtils.hasText(inpuser.getFavteam()) ? inpuser.getFavteam() : null);
        userobj.setPassword(passwordEncoder.encode(inpuser.getPassword()));
        userobj.setPhonenumber(inpuser.getPhonenumber());
        userobj.setRole(UserRole.USER);
        return userobj;
    }

    private UserDto buildDtoFromUser(User userobj) {
        UserDto userdtoobj = new UserDto();
        userdtoobj.setUsername(userobj.getUsername());
        userdtoobj.setFirstname(userobj.getFirstname());
        userdtoobj.setLastname(userobj.getLastname());
        userdtoobj.setEmail(userobj.getEmail());
        userdtoobj.setFavteam(userobj.getFavteam());
        userdtoobj.setPassword(userobj.getPassword());
        userdtoobj.setPhonenumber(userobj.getPhonenumber());
        userdtoobj.setRole(userobj.getRole());
        return userdtoobj;
    }
}
