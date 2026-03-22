package com.cricket.fantasyleague.service.workflow;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.payload.dto.UserTransferDto;
import com.cricket.fantasyleague.service.match.MatchService;
import com.cricket.fantasyleague.service.usertransfer.UserTransferService;

@Service
public class TransferWorkflowService {

    private final UserTransferService userTransferService;
    private final MatchService matchService;

    public TransferWorkflowService(UserTransferService userTransferService, MatchService matchService) {
        this.userTransferService = userTransferService;
        this.matchService = matchService;
    }

    public void makeTransferForCurrentWindow(UserTransferDto userTransferDto, String userEmail) {
        Match nextMatch = matchService.findUpcomingMatch(LocalDate.now(), LocalTime.now());
        if (nextMatch == null) {
            throw new CommonException("No upcoming matches available. Tournament may have ended.");
        }
        userTransferService.makeTransfer(nextMatch, userTransferDto, userEmail);
    }
}
