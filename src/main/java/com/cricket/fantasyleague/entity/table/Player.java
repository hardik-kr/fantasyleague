package com.cricket.fantasyleague.entity.table;

import com.cricket.fantasyleague.entity.enums.PlayerType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "player")
public class Player 
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id ;
    
    @Column(length = 30)
    private String name ;

    @ManyToOne
    @JoinColumn(name = "teamid", referencedColumnName = "id")
    private Team teamid ;

    private Double credit ;
    private PlayerType type ;
    private Boolean overseas ;
    private Boolean uncapped ;

    public Player(String name, Team teamid, Double credit, PlayerType type, Boolean overseas, Boolean uncapped) 
    {
        this.name = name;
        this.teamid = teamid;
        this.credit = credit;
        this.type = type;
        this.overseas = overseas;
        this.uncapped = uncapped;
    }
}
