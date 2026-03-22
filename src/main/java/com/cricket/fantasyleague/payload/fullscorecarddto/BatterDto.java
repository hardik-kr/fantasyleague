package com.cricket.fantasyleague.payload.fullscorecarddto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BatterDto 
{
    private Integer PlayerId ;
    private String dismissal ;
    private Integer runs ;
    private Integer balls ;
    private Integer fours ;
    private Integer sixes ;
    private Double strikerate ;
}
