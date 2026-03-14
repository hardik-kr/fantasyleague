package com.cricket.fantasyleague.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.cricket.fantasyleague.entity.table.Team;

public interface TeamRepository extends JpaRepository<Team,Integer> 
{
  
}

