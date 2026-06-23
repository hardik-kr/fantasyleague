package com.cricket.fantasyleague.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.cricket.fantasyleague.entity.enums.Booster;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class AppConfig {

    private volatile Snapshot snapshot;

    public void load(Snapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("App config snapshot cannot be null");
        }
        this.snapshot = snapshot;
    }

    public Integer getActiveLeagueId() {
        return requireSnapshot().activeLeagueId();
    }

    public String getName() {
        return requireSnapshot().name();
    }

    public Integer getYear() {
        return requireSnapshot().year();
    }

    public String getStatus() {
        return requireSnapshot().status();
    }

    public Integer getTotalTransfer() {
        return requireSnapshot().totalTransfer();
    }

    public JsonNode getBooster() {
        return requireSnapshot().booster().deepCopy();
    }

    public Integer getTotalBooster() {
        int total = 0;
        JsonNode booster = requireSnapshot().booster();
        for (JsonNode item : booster) {
            var fields = item.fields();
            while (fields.hasNext()) {
                JsonNode detail = fields.next().getValue();
                boolean active = !detail.has("active") || detail.path("active").asBoolean(false);
                if (active) {
                    total += detail.path("count").asInt(0);
                }
            }
        }
        return total;
    }

    public List<BoosterConfig> getActiveBoosters() {
        List<BoosterConfig> activeBoosters = new ArrayList<>();
        JsonNode booster = requireSnapshot().booster();
        for (JsonNode item : booster) {
            var fields = item.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                JsonNode detail = field.getValue();
                boolean active = !detail.has("active") || detail.path("active").asBoolean(false);
                int count = detail.path("count").asInt(0);
                if (active && count > 0) {
                    activeBoosters.add(new BoosterConfig(field.getKey(), count));
                }
            }
        }
        return List.copyOf(activeBoosters);
    }

    public Map<String, Integer> getInitialBoosterLeftDetail() {
        Map<String, Integer> detail = new LinkedHashMap<>();
        for (BoosterConfig booster : getActiveBoosters()) {
            detail.put(booster.id(), booster.count());
        }
        return detail;
    }

    public Integer getConfiguredBoosterCount(Booster targetBooster) {
        if (targetBooster == null) {
            return 0;
        }
        for (BoosterConfig booster : getActiveBoosters()) {
            if (booster.id().equals(targetBooster.name())) {
                return booster.count();
            }
        }
        return 0;
    }

    public boolean isBoosterActive(Booster targetBooster) {
        if (targetBooster == null) {
            return false;
        }
        JsonNode booster = requireSnapshot().booster();
        for (JsonNode item : booster) {
            JsonNode detail = item.path(targetBooster.name());
            if (!detail.isMissingNode()) {
                boolean active = !detail.has("active") || detail.path("active").asBoolean(false);
                int count = detail.path("count").asInt(0);
                return active && count > 0;
            }
        }
        return false;
    }

    public Set<Integer> getFreeTransferMatchIds() {
        return requireSnapshot().freeTransferMatchIds();
    }

    public boolean isFreeTransferMatch(Integer matchId) {
        return matchId != null && requireSnapshot().freeTransferMatchIds().contains(matchId);
    }

    public Snapshot snapshot() {
        Snapshot current = requireSnapshot();
        return new Snapshot(
                current.activeLeagueId(),
                current.name(),
                current.year(),
                current.status(),
                current.totalTransfer(),
                current.booster().deepCopy(),
                current.freeTransferMatchIds());
    }

    private Snapshot requireSnapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            throw new IllegalStateException("App config has not been loaded");
        }
        return current;
    }

    public record Snapshot(
            Integer activeLeagueId,
            String name,
            Integer year,
            String status,
            Integer totalTransfer,
            JsonNode booster,
            Set<Integer> freeTransferMatchIds) {

        public Snapshot {
            if (booster == null) {
                throw new IllegalArgumentException("Booster config cannot be null");
            }
            booster = booster.deepCopy();
            freeTransferMatchIds = Set.copyOf(freeTransferMatchIds == null ? Set.of() : freeTransferMatchIds);
        }
    }

    public record BoosterConfig(String id, Integer count) {
    }
}
