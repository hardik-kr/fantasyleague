package com.cricket.fantasyleague.entity.table;

import java.time.LocalDateTime;

import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.util.SnowflakeIdGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean seasonOnboardingSeen = false ;

    @Column(nullable = false, updatable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt ;

    @Column(nullable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt ;

    public User() 
    {
        this.isActive = true ;
        this.seasonOnboardingSeen = false ;
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
        this.seasonOnboardingSeen = false;
    }

    @PrePersist
    private void ensureId() {
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.generate();
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.seasonOnboardingSeen == null) {
            this.seasonOnboardingSeen = false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    private void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
