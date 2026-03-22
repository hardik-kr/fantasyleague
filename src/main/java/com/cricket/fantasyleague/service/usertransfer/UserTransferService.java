package com.cricket.fantasyleague.service.usertransfer;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.payload.dto.UserTransferDto;

public interface UserTransferService 
{
    void makeTransfer(Match nextMatch, UserTransferDto userTransferDto, String string);

    void lockMatchTeam(Match currMatch);  
}
