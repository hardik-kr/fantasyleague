package com.cricket.fantasyleague.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.entity.table.User;

public interface UserRepository extends JpaRepository<User,Integer> 
{
    User findByEmail(String email) ;
    User findByRole(UserRole role) ;
    
}
