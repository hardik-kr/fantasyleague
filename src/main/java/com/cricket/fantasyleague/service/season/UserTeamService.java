package com.cricket.fantasyleague.service.season;

import java.util.List;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.payload.season.DraftResponse;
import com.cricket.fantasyleague.payload.season.MatchHistoryResponse;
import com.cricket.fantasyleague.payload.season.UserTeamResponse;

public interface UserTeamService {

    DraftResponse getDraftForNextMatch(User user);

    /**
     * Preview payload for the my-team UI: locked XI for the live match when one exists,
     * otherwise the same shape as {@link #getDraftForNextMatch(User)} for the next match.
     */
    DraftResponse getMyTeamForPreview(User user);

    List<MatchHistoryResponse> getMatchHistory(User user);

    UserTeamResponse getUserTeamForMatch(Long userId, Integer matchId);
}
