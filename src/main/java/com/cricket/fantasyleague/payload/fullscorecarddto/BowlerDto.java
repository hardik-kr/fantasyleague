package com.cricket.fantasyleague.payload.fullscorecarddto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BowlerDto 
{
    private String name ;
    private Integer ballbowl ;     
    private Integer maidens ;
    private Integer runs ;
    private Integer wickets ;
    private Integer noball ;
    private Integer wides ;
    private Double economy ;
}
