package com.cricket.fantasyleague.entity.table;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import com.cricket.fantasyleague.entity.enums.Booster;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user_overall_stats")
public class UserOverallStats 
{
    @Id
    private Integer id ;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User userid ;

    private Double totalpoints ;

    private Double prevpoints ;

    private Integer boosterleft ;

    private Integer transferleft ;

    @Column(name = "used_boosters", length = 255)
    private String usedBoosters;

    public UserOverallStats(User userid, Double totalpoints,Double prevpoints, Integer boosterleft, Integer transferleft) 
    {
        this.id = generateId() ;
        this.userid = userid;
        this.prevpoints = prevpoints ;
        this.totalpoints = totalpoints;
        this.boosterleft = boosterleft;
        this.transferleft = transferleft;
    }

    public Set<Booster> getUsedBoosterSet() {
        if (usedBoosters == null || usedBoosters.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(usedBoosters.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Booster::valueOf)
                .collect(Collectors.toSet());
    }

    public void addUsedBooster(Booster booster) {
        if (booster == null) return;
        Set<Booster> current = new java.util.HashSet<>(getUsedBoosterSet());
        current.add(booster);
        this.usedBoosters = current.stream()
                .map(Booster::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private Integer generateId() 
    {
        Random random = new Random();
        StringBuilder id = new StringBuilder();

        id.append(random.nextInt(9)+1) ;
        
        for (int i = 0; i < 5; i++) {
            int digit = random.nextInt(9);
            id.append(digit);
        }

        return Integer.parseInt(id.toString()) ;
    }
}
