package com.cricket.fantasyleague.payload.fullscorecarddto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InningDto 
{
    private String score ;
    private String overs ;
    private List<BatterDto> batter ;
    private List<BowlerDto> bowler ; 
    private List<FielderDto> fielder ;   
}
