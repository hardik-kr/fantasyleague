package com.cricket.fantasyleague.service.api;

import java.util.List;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.payload.response.DraftResponse;
import com.cricket.fantasyleague.payload.response.MatchHistoryResponse;
import com.cricket.fantasyleague.payload.response.UserTeamResponse;

public interface UserTeamService {

    DraftResponse getDraftForNextMatch(User user);

    List<MatchHistoryResponse> getMatchHistory(User user);

    UserTeamResponse getUserTeamForMatch(Integer userId, Integer matchId);
}
