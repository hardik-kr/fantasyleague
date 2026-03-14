package com.cricket.fantasyleague.service;

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
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.exception.ResourceAlreadyExist;
import com.cricket.fantasyleague.payload.dto.UserDto;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.util.AppConstants;

@Service
public class UserServiceImpl implements UserService, UserDetailsService
{
    private UserRepository userRepository ;
    private UserOverallStatsRepository userOverallStatsRepository ;

    public UserServiceImpl(UserRepository userRepository, UserOverallStatsRepository userOverallStatsRepository) {
        this.userRepository = userRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
    }

    @Override
    public void createUser(UserDto inpuser) 
    {
        User isexist = userRepository.findByEmail(inpuser.getEmail());
        if(isexist != null)
        {
            throw new ResourceAlreadyExist(AppConstants.user.ALREADY_EXIST,"email",inpuser.getEmail()) ;
        }
        User user = getUserFromUserDtoObj(inpuser) ;
        userRepository.save(user) ;
        createUserOverallPointsObj(user);
    }

    public void createUserOverallPointsObj(User user) 
    {
        Integer transfer = AppConstants.FantasyPoints.TOTAL_TRANSFER ;
        Integer booster = AppConstants.FantasyPoints.TOTAL_BOOSTER ;
        Double points = 0.0 ;
        Double prevpoints = 0.0 ;
        UserOverallStats userOverallStatsObj = new UserOverallStats(user, points, prevpoints, booster, transfer) ;
        saveUserOverallPoints(userOverallStatsObj) ;
    }

    private void saveUserOverallPoints(UserOverallStats userOverallStatsObj) 
    {
        try {
            userOverallStatsRepository.save(userOverallStatsObj) ;
        }
        catch(Exception e) {
            Throwable cause =  e.getCause().getCause() ;
            throw new CommonException(String.format(AppConstants.error.DATABASE_ERROR,AppConstants.entity.USEROVERALLPOINTS,cause.getMessage())) ;
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException 
    {
        User userobj = userRepository.findByEmail(username) ;
        if(userobj == null)
            throw new BadCredentialsException(String.format("Invalid Username not found : %s",username)) ;

        UserDto userdto = getUserDtoFromUserObj(userobj) ;
        return userdto ;
    }

    private User getUserFromUserDtoObj(UserDto inpuser) 
    {
        User userobj = new User() ;
        userobj.setEmail(inpuser.getEmail());
        userobj.setFavteam(inpuser.getFavteam());
        userobj.setName(inpuser.getName());
        userobj.setPassword(new BCryptPasswordEncoder().encode(inpuser.getPassword()));
        userobj.setPhonenumber(inpuser.getPhonenumber());
        userobj.setRole(UserRole.USER);
        return userobj ;
    }

    private UserDto getUserDtoFromUserObj(User userobj) 
    {
        UserDto userdtoobj = new UserDto() ;
        userdtoobj.setEmail(userobj.getEmail());
        userdtoobj.setFavteam(userobj.getFavteam());
        userdtoobj.setName(userobj.getName());
        userdtoobj.setPassword(userobj.getPassword());
        userdtoobj.setPhonenumber(userobj.getPhonenumber());
        userdtoobj.setRole(userobj.getRole());
        return userdtoobj ;
    }

    @Override
    public UserDetails getUserByUserName(String username) 
    {
        return loadUserByUsername(username) ;
    }

    @Override
    public List<User> getAllUser() 
    {
        return userRepository.findAll() ;
    }   
    
    
}
