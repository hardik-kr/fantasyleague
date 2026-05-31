package com.cricket.fantasyleague.service.season;

import java.util.List;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.payload.season.DraftResponse;
import com.cricket.fantasyleague.payload.season.MatchHistoryResponse;
import com.cricket.fantasyleague.payload.season.MyTeamResponse;
import com.cricket.fantasyleague.payload.season.UserTeamResponse;

public interface UserTeamService {

    DraftResponse getDraftForNextMatch(User user);

    /** Lean read-only payload for {@code GET /api/seasons/me/my-team}. */
    MyTeamResponse getMyTeamForPreview(User user);

    List<MatchHistoryResponse> getMatchHistory(User user);

    UserTeamResponse getUserTeamForMatch(Long userId, Integer matchId);
}
