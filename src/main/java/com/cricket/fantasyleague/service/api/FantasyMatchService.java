package com.cricket.fantasyleague.service.api;

import java.util.List;
import java.util.Optional;

import com.cricket.fantasyleague.payload.response.MatchResponse;

public interface FantasyMatchService {

    List<MatchResponse> getAllMatchesWithTeams();

    Optional<MatchResponse> getMatchById(Integer matchId);
}
