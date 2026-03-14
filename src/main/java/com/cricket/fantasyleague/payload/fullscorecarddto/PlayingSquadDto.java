package com.cricket.fantasyleague.payload.fullscorecarddto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayingSquadDto 
{
    private List<PlayerDto> playerListA ;
    private List<PlayerDto> playerListB ;    
}
