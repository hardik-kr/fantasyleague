package com.cricket.fantasyleague.payload.fullscorecarddto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FielderDto 
{
    private Integer PlayerId ;
    private Integer catches;
    private Integer runouts;
    private Integer runouts_direct;
    private Integer stumpings;
    private Integer lbwbowled ;
}
