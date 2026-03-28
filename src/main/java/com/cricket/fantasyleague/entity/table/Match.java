package com.cricket.fantasyleague.entity.table;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.cricket.fantasyleague.entity.enums.MatchState;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "matches", catalog = "cricketapi")
public class Match 
{
    @Id
    private Integer id ;
    private LocalDate date ;

    @Column(name = "is_match_complete")
    private Boolean isMatchComplete;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_state", length = 20)
    private MatchState matchState;

    @Column(name = "match_desc", length = 100)
    private String matchDesc;

    @Column(length = 100)
    private String result ;

    private LocalTime time ;

    @Column(length = 40)
    private String timezone ;

    @Column(length = 70)
    private String toss ;

    @Column(length = 100)
    private String venue ;

    @Column(name = "league_id")
    private Integer leagueId ;

    @Column(name = "mom_player_id")
    private Integer momPlayerId;

    @ManyToOne
    @JoinColumn(name = "teama_id", referencedColumnName = "id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Team teamA ;
    
    @ManyToOne
    @JoinColumn(name = "teamb_id", referencedColumnName = "id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Team teamB ;
}
