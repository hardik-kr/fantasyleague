package com.cricket.fantasyleague.service.season;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.payload.season.UserTransferDto;

public interface UserTransferService 
{
    void makeTransfer(Match nextMatch, UserTransferDto userTransferDto, String string);

    void lockMatchTeam(Match currMatch);  
}
