package com.cricket.fantasyleague.payload.daily;

import java.util.List;

import com.cricket.fantasyleague.payload.response.PlayerResponse;
import com.cricket.fantasyleague.payload.response.TeamBrief;

public record DailyMatchPlayerPoolResponse(
        Integer matchId,
        TeamBrief teamA,
        TeamBrief teamB,
        List<PlayerResponse> players
) {
}
