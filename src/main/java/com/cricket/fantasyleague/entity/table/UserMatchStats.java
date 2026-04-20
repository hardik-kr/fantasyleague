package com.cricket.fantasyleague.entity.table;

import java.util.List;

import com.cricket.fantasyleague.entity.enums.Booster ;
import com.cricket.fantasyleague.util.SnowflakeIdGenerator;

import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user_match_stats")
public class UserMatchStats 
{
    @Id
    private Long id ;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User userid ;

    @ManyToOne
    @JoinColumn(name = "match_id", referencedColumnName = "id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Match matchid ;

    private Booster boosterused ;
    private Integer transferused ;
    private Double matchpoints ;

    @ManyToOne
    @JoinColumn(name = "captain_id", referencedColumnName = "id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player captainid ;

    @ManyToOne
    @JoinColumn(name = "vicecaptain_id", referencedColumnName = "id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player vicecaptainid ;
    
    @ManyToOne
    @JoinColumn(name = "triple_booster_id", referencedColumnName = "id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player tripleboosterplayerid ;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_playing11",
        joinColumns = @JoinColumn(name = "user_matchstats_id"),
        inverseJoinColumns = @JoinColumn(name = "player_id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)),
        inverseForeignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private List<Player> playing11 ;

    public UserMatchStats(User userid, Match matchid, Booster boosterused, Integer transferused,
            Double matchpoints, Player captainid, Player vicecaptainid, Player tripleboosterplayerid,
            List<Player> playing11) 
    {
        this.id = SnowflakeIdGenerator.generate();
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

    @PrePersist
    private void ensureId() {
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.generate();
        }
    }
}
