package com.cricket.fantasyleague.service.workflow;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.payload.dto.UserTransferDto;
import com.cricket.fantasyleague.service.MatchService;
import com.cricket.fantasyleague.service.UserTransferService;

@Service
public class TransferWorkflowService {

    private final UserTransferService userTransferService;
    private final MatchService matchService;

    public TransferWorkflowService(UserTransferService userTransferService, MatchService matchService) {
        this.userTransferService = userTransferService;
        this.matchService = matchService;
    }

    public void makeTransferForCurrentWindow(UserTransferDto userTransferDto, String userEmail) {
        LocalDate currdate = LocalDate.now();
        LocalTime currtime = LocalTime.now();

        Match nextMatch = matchService.findUpcomingMatch(currdate, currtime);
        userTransferService.makeTransfer(nextMatch, userTransferDto, userEmail);
    }
}
