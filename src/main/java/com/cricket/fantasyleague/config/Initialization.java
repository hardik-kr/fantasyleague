package com.cricket.fantasyleague.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.repository.UserRepository;

@Component
public class Initialization implements CommandLineRunner
{
    @Autowired
    private UserRepository userRepository ;
    
    @Override
    public void run(String... args) throws Exception 
    {
        User adminuser = userRepository.findByRole(UserRole.ADMIN) ;
        if(adminuser == null)
        {
            adminuser = new User() ;
            adminuser.setEmail("admin@gmail.com");
            adminuser.setPassword(new BCryptPasswordEncoder().encode("password"));
            adminuser.setRole(UserRole.ADMIN);
            userRepository.save(adminuser) ;
        }
    }
}
