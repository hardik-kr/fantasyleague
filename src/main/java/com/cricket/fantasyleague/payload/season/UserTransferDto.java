package com.cricket.fantasyleague.payload.season;

import java.util.List;

import com.cricket.fantasyleague.entity.enums.Booster;

import lombok.Data;

@Data
public class UserTransferDto 
{
    private List<Integer> userplaying11 ;
    private Integer captainid ;
    private Integer vicecaptainid ;
    private Integer tripleboostpid ;
    private Booster boosterid ;
}
