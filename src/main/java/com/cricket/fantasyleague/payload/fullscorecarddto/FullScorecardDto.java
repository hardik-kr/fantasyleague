package com.cricket.fantasyleague.payload.fullscorecarddto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FullScorecardDto 
{
    private MatchMetaDataDto matchinfo ;  
    private InningDto inningsA ;
    private InningDto inningsB ;  
}
