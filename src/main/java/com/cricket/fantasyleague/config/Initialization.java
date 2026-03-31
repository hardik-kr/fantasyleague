package com.cricket.fantasyleague.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.service.user.UserPersistServiceImpl;

@Component
public class Initialization implements CommandLineRunner {

    private final UserPersistServiceImpl userPersistService;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.firstname}")
    private String adminFirstname;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    public Initialization(UserPersistServiceImpl userPersistService) {
        this.userPersistService = userPersistService;
    }

    @Override
    public void run(String... args) throws Exception {
        User adminuser = userPersistService.findByRole(UserRole.ADMIN);
        if (adminuser == null) {
            adminuser = new User();
            adminuser.setUsername(adminUsername);
            adminuser.setFirstname(adminFirstname);
            adminuser.setEmail(adminEmail);
            adminuser.setPassword(new BCryptPasswordEncoder().encode(adminPassword));
            adminuser.setRole(UserRole.ADMIN);
            userPersistService.saveUser(adminuser);
        }
    }
}
