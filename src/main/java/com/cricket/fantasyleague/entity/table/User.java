package com.cricket.fantasyleague.entity.table;

import java.util.Random;

import com.cricket.fantasyleague.entity.enums.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;

@Entity
@Data
@AllArgsConstructor
@Table(name = "user")
public class User 
{
    @Id
    private Integer id ;

    @Column(length = 30, unique = true, nullable = false)
    private String username ;

    @Column(length = 30)
    private String firstname ;

    @Column(length = 30)
    private String lastname ;
    
    @Column(length = 30)
    private String email ;

    private String password ;

    @Column(length = 10)
    private String phonenumber ;

    @Column(length = 30)
    private String favteam ;

    private UserRole role ;

    @Column(nullable = false)
    private Boolean isActive = true ;

    public User() 
    {
        this.id = generateId() ;
        this.isActive = true ;
    }

    public User(String username, String firstname, String lastname, String email, String password,
                String phonenumber, String favteam, UserRole role) 
    {
        this.id = generateId() ;
        this.username = username;
        this.firstname = firstname;
        this.lastname = lastname;
        this.email = email;
        this.password = password;
        this.phonenumber = phonenumber;
        this.favteam = favteam;
        this.role = role;
        this.isActive = true;
    }

    private Integer generateId() 
    {
        Random random = new Random();
        StringBuilder id = new StringBuilder();

        id.append(random.nextInt(9)+1) ;
        
        for (int i = 0; i < 5; i++) {
            int digit = random.nextInt(9); // Generates a random digit between 0 and 9
            id.append(digit);
        }

        return Integer.parseInt(id.toString()) ;
    }
}
