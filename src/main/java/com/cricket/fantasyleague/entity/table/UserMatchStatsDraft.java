package com.cricket.fantasyleague.entity.table;

import java.util.List;
import java.util.Random;

import com.cricket.fantasyleague.entity.enums.Booster;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "draft_user_match")
public class UserMatchStatsDraft 
{
    @Id
    private Integer id ;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User userid ;

    @ManyToOne
    @JoinColumn(name = "match_id", referencedColumnName = "id")
    private Match matchid ;

    private Booster boosterused ;
    private Integer transferused ;
    private Double matchpoints ;

    @ManyToOne
    @JoinColumn(name = "captain_id", referencedColumnName = "id")
    private Player captainid ;

    @ManyToOne
    @JoinColumn(name = "vicecaptain_id", referencedColumnName = "id")
    private Player vicecaptainid ;
    
    @ManyToOne
    @JoinColumn(name = "triple_booster_id", referencedColumnName = "id")
    private Player tripleboosterplayerid ;

    @ManyToMany(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    @JoinTable(
        name = "draft_user_playing11", // Set your custom table name here
        joinColumns = @JoinColumn(name = "user_matchstats_id"),
        inverseJoinColumns = @JoinColumn(name = "player_id")
    )
    private List<Player> playing11 ;

    public UserMatchStatsDraft(User userid, Match matchid, Booster boosterused, Integer transferused,
            Double matchpoints, Player captainid, Player vicecaptainid, Player tripleboosterplayerid,
            List<Player> playing11) 
    {
        this.id = generateId();
        this.userid = userid;
        this.matchid = matchid;
        this.boosterused = boosterused;
        this.transferused = transferused;
        this.matchpoints = matchpoints;
        this.captainid = captainid;
        this.vicecaptainid = vicecaptainid;
        this.tripleboosterplayerid = tripleboosterplayerid;
        this.playing11 = playing11;
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
