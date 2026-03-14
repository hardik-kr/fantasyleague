package com.cricket.fantasyleague.payload.dto;

import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.table.Team;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerJsonStructDto 
{
    private String playerName ;
    private Team teamId ;
    private Double credit ;
    private PlayerType playerType ;
    private Boolean overseas ;
    private Boolean uncapped ;
}
