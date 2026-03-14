package com.cricket.fantasyleague.service;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.payload.dto.UserTransferDto;

public interface UserTransferService 
{
    void makeTransfer(Match nextMatch, UserTransferDto userTransferDto, String string);

    void lockMatchTeam(Match currMatch);  
}
