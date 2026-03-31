package com.cricket.fantasyleague.service.user;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.payload.dto.UserDto;

public interface UserService 
{
    void validateNewUser(UserDto userdto) ;
    void createUser(UserDto userdto) ;
    UserDetails getUserByUserName(String username) ;
    UserDetails loadUserByUsername(String username) ;
    List<User> getAllUser() ;
}
