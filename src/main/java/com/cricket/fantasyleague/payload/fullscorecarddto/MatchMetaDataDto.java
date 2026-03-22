package com.cricket.fantasyleague.payload.fullscorecarddto;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatchMetaDataDto 
{
    private Integer id ;
    private String match ;
    private String teamA ;
    private String teamB ;
    private LocalDate startDate ;
    private LocalTime startTime ;
    private String toss ;
    private String venue ;
    private String result ;
    private Boolean isMatchComplete ;
    private Integer playerOfTheMatch ;
    private PlayingSquadDto playingSquad ;
}
