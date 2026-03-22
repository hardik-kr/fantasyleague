package com.cricket.fantasyleague.entity.table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "teams", catalog = "cricketapi")
public class Team 
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id ;
    @Column(length = 50)
    private String name ;
    @Column(length = 5, name = "short_name")
    private String shortName ;
    @Column(name = "league_id")
    private Integer leagueId ;
}
