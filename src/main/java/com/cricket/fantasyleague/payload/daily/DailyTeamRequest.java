package com.cricket.fantasyleague.payload.daily;

import java.util.List;

import lombok.Data;

@Data
public class DailyTeamRequest {
    private List<Integer> playing11;
    private Integer captainId;
    private Integer viceCaptainId;
}
