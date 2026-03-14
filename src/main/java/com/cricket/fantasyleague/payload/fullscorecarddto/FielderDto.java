package com.cricket.fantasyleague.payload.fullscorecarddto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FielderDto 
{
    private String name ;
    private Integer catches;
    private Integer runouts;
    private Integer runouts_direct;
    private Integer stumpings;
    private Integer lbwbowled ;
}
