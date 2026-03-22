package com.cricket.fantasyleague.service.user;

import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.exception.ResourceAlreadyExist;
import com.cricket.fantasyleague.payload.dto.UserDto;
import com.cricket.fantasyleague.util.AppConstants;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {

    private final UserPersistServiceImpl persistService;

    public UserServiceImpl(UserPersistServiceImpl persistService) {
        this.persistService = persistService;
    }

    @Override
    public void createUser(UserDto inpuser) {
        User existByEmail = persistService.findByEmail(inpuser.getEmail());
        if (existByEmail != null) {
            throw new ResourceAlreadyExist(AppConstants.user.ALREADY_EXIST, "email", inpuser.getEmail());
        }
        User existByUsername = persistService.findByUsername(inpuser.getUsername());
        if (existByUsername != null) {
            throw new ResourceAlreadyExist(AppConstants.user.ALREADY_EXIST, "username", inpuser.getUsername());
        }
        User user = buildUserFromDto(inpuser);
        persistService.saveUser(user);
        initializeUserOverallStats(user);
    }

    private void initializeUserOverallStats(User user) {
        Integer transfer = AppConstants.FantasyPoints.TOTAL_TRANSFER;
        Integer booster = AppConstants.FantasyPoints.TOTAL_BOOSTER;
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
            throw new BadCredentialsException(String.format(AppConstants.user.USER_NOT_FOUND, username));
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

    private User buildUserFromDto(UserDto inpuser) {
        User userobj = new User();
        userobj.setUsername(inpuser.getUsername());
        userobj.setFirstname(inpuser.getFirstname());
        userobj.setLastname(inpuser.getLastname());
        userobj.setEmail(inpuser.getEmail());
        userobj.setFavteam(inpuser.getFavteam());
        userobj.setPassword(new BCryptPasswordEncoder().encode(inpuser.getPassword()));
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
