package com.cricket.fantasyleague.service;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.payload.dto.UserDto;

public interface UserService 
{
    void createUser(UserDto userdto) ;
    UserDetails getUserByUserName(String username) ;
    List<User> getAllUser() ;
}
