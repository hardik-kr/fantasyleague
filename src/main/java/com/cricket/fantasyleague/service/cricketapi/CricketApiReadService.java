package com.cricket.fantasyleague.service.cricketapi;

import java.util.List;

import com.cricket.fantasyleague.service.cricketapi.model.CricketApiMatchRow;
import com.cricket.fantasyleague.service.cricketapi.model.CricketApiPlayerRow;
import com.cricket.fantasyleague.service.cricketapi.model.CricketApiTeamRow;

public interface CricketApiReadService {

    List<CricketApiTeamRow> fetchTeams();

    List<CricketApiPlayerRow> fetchPlayers();

    List<CricketApiMatchRow> fetchMatches();
}
