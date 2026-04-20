package com.cricket.fantasyleague.entity.table;

import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.util.SnowflakeIdGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
    private Long id ;

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
        this.isActive = true ;
    }

    public User(String username, String firstname, String lastname, String email, String password,
                String phonenumber, String favteam, UserRole role) 
    {
        this.id = SnowflakeIdGenerator.generate();
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

    @PrePersist
    private void ensureId() {
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.generate();
        }
    }
}
