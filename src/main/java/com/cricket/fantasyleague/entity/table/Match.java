package com.cricket.fantasyleague.entity.table;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
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
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "matches")
public class Match 
{
    @Id
    private Integer id ;
    private LocalDate date ;
    private LocalTime time ;

    @Column(length = 100)
    private String venue ; 
    
    private Integer matchnum ;
    @Column(length = 100)
    private String result ;
    @Column(length = 70)
    private String toss ;

    @ManyToOne
    @JoinColumn(name = "teamA_id", referencedColumnName = "id")
    private Team teamA ;
    
    @ManyToOne
    @JoinColumn(name = "teamB_id", referencedColumnName = "id")
    private Team teamB ;
}
