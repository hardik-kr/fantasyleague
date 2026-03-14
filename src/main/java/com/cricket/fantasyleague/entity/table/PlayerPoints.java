package com.cricket.fantasyleague.entity.table;

import java.util.Random;

import jakarta.persistence.Entity;
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
@Table(name = "player_points")
public class PlayerPoints 
{
    @Id
    private Integer id ;

    @ManyToOne
    @JoinColumn(name = "match_id", referencedColumnName = "id")
    private Match matchid ;

    @ManyToOne
    @JoinColumn(name = "player_id", referencedColumnName = "id")
    private Player playerid ;

    private Double playerpoints ;

    public PlayerPoints(Match matchid, Player playerid, Double playerpoints) 
    {
        this.id = generateId() ;
        this.matchid = matchid;
        this.playerid = playerid;
        this.playerpoints = playerpoints;
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
