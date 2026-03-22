package com.cricket.fantasyleague.entity.table;

import com.cricket.fantasyleague.entity.enums.PlayerType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "players", catalog = "cricketapi")
public class Player 
{
    @Id
    private Integer id;
    
    @Column(length = 50)
    private String name;

    @Enumerated(EnumType.ORDINAL)
    private PlayerType role;
}
