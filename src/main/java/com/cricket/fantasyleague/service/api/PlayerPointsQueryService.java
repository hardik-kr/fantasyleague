package com.cricket.fantasyleague.service.api;

import java.util.List;

import com.cricket.fantasyleague.payload.response.MatchPlayerPointsResponse;

public interface PlayerPointsQueryService {

    List<MatchPlayerPointsResponse> getMatchPlayerPoints(Integer matchId);
}
