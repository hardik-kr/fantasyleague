package com.cricket.fantasyleague.entity.table.season;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cricket.fantasyleague.entity.enums.Booster;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.util.SnowflakeIdGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
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
    private Long id ;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User userid ;

    private Double totalpoints ;

    private Double prevpoints ;

    private Integer boosterleft ;

    private Integer transferleft ;

    @Column(name = "used_boosters", length = 255)
    private String usedBoosters;

    public UserOverallStats(User userid, Double totalpoints, Double prevpoints, Integer boosterleft, Integer transferleft) 
    {
        this.id = SnowflakeIdGenerator.generate();
        this.userid = userid;
        this.prevpoints = prevpoints ;
        this.totalpoints = totalpoints;
        this.boosterleft = boosterleft;
        this.transferleft = transferleft;
    }

    public Set<Booster> getUsedBoosterSet() {
        return getUsedBoosterCountMap().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public void addUsedBooster(Booster booster) {
        incrementUsedBooster(booster);
    }

    public Map<Booster, Integer> getUsedBoosterCountMap() {
        Map<Booster, Integer> counts = new LinkedHashMap<>();
        for (Booster booster : Booster.values()) {
            counts.put(booster, 0);
        }

        if (usedBoosters == null || usedBoosters.isBlank()) {
            return counts;
        }

        String value = usedBoosters.trim();
        if (value.matches("[0-9a-zA-Z]+") && value.length() <= Booster.values().length) {
            Booster[] boosters = Booster.values();
            for (int i = 0; i < value.length() && i < boosters.length; i++) {
                counts.put(boosters[i], Math.max(Character.digit(value.charAt(i), 36), 0));
            }
            return counts;
        }

        for (String part : value.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            String[] pieces = token.split(":", 2);
            try {
                Booster booster = Booster.valueOf(pieces[0].trim());
                int count = pieces.length > 1 ? Integer.parseInt(pieces[1].trim()) : 1;
                counts.put(booster, Math.max(count, 0));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy values instead of blocking login/draft load.
            }
        }
        return counts;
    }

    public int getUsedBoosterCount(Booster booster) {
        if (booster == null) {
            return 0;
        }
        return Math.max(getUsedBoosterCountMap().getOrDefault(booster, 0), 0);
    }

    public void incrementUsedBooster(Booster booster) {
        if (booster == null) {
            return;
        }
        Map<Booster, Integer> counts = getUsedBoosterCountMap();
        counts.put(booster, Math.max(counts.getOrDefault(booster, 0), 0) + 1);
        this.usedBoosters = toCompactUsedBoosters(counts);
    }

    private String toCompactUsedBoosters(Map<Booster, Integer> counts) {
        StringBuilder value = new StringBuilder(Booster.values().length);
        for (Booster booster : Booster.values()) {
            int count = Math.max(counts.getOrDefault(booster, 0), 0);
            value.append(Character.forDigit(Math.min(count, 35), 36));
        }
        return value.toString();
    }

    @PrePersist
    private void ensureId() {
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.generate();
        }
    }
}
