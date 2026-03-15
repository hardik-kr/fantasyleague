package com.cricket.fantasyleague.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.service.UserPersistServiceImpl;

@Component
public class Initialization implements CommandLineRunner {

    private final UserPersistServiceImpl userPersistService;

    public Initialization(UserPersistServiceImpl userPersistService) {
        this.userPersistService = userPersistService;
    }

    @Override
    public void run(String... args) throws Exception {
        User adminuser = userPersistService.findByRole(UserRole.ADMIN);
        if (adminuser == null) {
            adminuser = new User();
            adminuser.setEmail("admin@gmail.com");
            adminuser.setPassword(new BCryptPasswordEncoder().encode("password"));
            adminuser.setRole(UserRole.ADMIN);
            userPersistService.saveUser(adminuser);
        }
    }
}
