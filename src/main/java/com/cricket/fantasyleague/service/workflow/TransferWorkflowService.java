package com.cricket.fantasyleague.service.workflow;

import static com.cricket.fantasyleague.util.MatchTimeUtils.nowDate;
import static com.cricket.fantasyleague.util.MatchTimeUtils.nowTime;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.payload.dto.UserTransferDto;
import com.cricket.fantasyleague.service.match.MatchService;
import com.cricket.fantasyleague.service.usertransfer.UserTransferService;

@Service
public class TransferWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(TransferWorkflowService.class);

    private final UserTransferService userTransferService;
    private final MatchService matchService;
    private final long lockWindowMinutes;

    public TransferWorkflowService(UserTransferService userTransferService,
                                   MatchService matchService,
                                   @Value("${fantasy.lock.window-minutes:10}") long lockWindowMinutes) {
        this.userTransferService = userTransferService;
        this.matchService = matchService;
        this.lockWindowMinutes = lockWindowMinutes;
    }

    public void makeTransferForCurrentWindow(UserTransferDto userTransferDto, String userEmail) {
        LocalDate today = nowDate();
        LocalTime now = nowTime();

        Match recentlyStarted = matchService.findLockedMatch(today, now);
        if (recentlyStarted != null && recentlyStarted.getTime() != null) {
            Duration sinceStart = Duration.between(recentlyStarted.getTime(), now);
            if (sinceStart.toMinutes() < lockWindowMinutes) {
                logger.info("Transfer rejected for user={}: matchId={} started {}min ago, within {}min lock window",
                        userEmail, recentlyStarted.getId(), sinceStart.toMinutes(), lockWindowMinutes);
                throw new CommonException("Team editing is locked. Match has started and teams are being finalized.");
            }
        }

        Match nextMatch = matchService.findUpcomingMatch(today, now);
        if (nextMatch == null) {
            throw new CommonException("No upcoming matches available. Tournament may have ended.");
        }
        userTransferService.makeTransfer(nextMatch, userTransferDto, userEmail);
    }
}
