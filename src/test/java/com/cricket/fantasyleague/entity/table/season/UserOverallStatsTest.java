package com.cricket.fantasyleague.entity.table.season;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.cricket.fantasyleague.entity.enums.Booster;

class UserOverallStatsTest {

    @Test
    void incrementUsedBoosterStoresCompactFixedPositionCounts() {
        UserOverallStats stats = new UserOverallStats();

        stats.addUsedBooster(Booster.DOUBLE_UP);
        stats.addUsedBooster(Booster.POWER_ALLROUNDER);
        stats.addUsedBooster(Booster.POWER_ALLROUNDER);

        assertThat(stats.getUsedBoosters()).isEqualTo("0010020");
        assertThat(stats.getUsedBoosterCount(Booster.DOUBLE_UP)).isEqualTo(1);
        assertThat(stats.getUsedBoosterCount(Booster.POWER_ALLROUNDER)).isEqualTo(2);
        assertThat(stats.getUsedBoosterCount(Booster.POWER_BATTER)).isZero();
    }

    @Test
    void parsesLegacyCommaSeparatedBoostersAsSingleUse() {
        UserOverallStats stats = new UserOverallStats();
        stats.setUsedBoosters("DOUBLE_UP,POWER_BATTER");

        assertThat(stats.getUsedBoosterCount(Booster.DOUBLE_UP)).isEqualTo(1);
        assertThat(stats.getUsedBoosterCount(Booster.POWER_BATTER)).isEqualTo(1);
        assertThat(stats.getUsedBoosterCount(Booster.POWER_ALLROUNDER)).isZero();
    }

    @Test
    void parsesLegacyCountPairs() {
        UserOverallStats stats = new UserOverallStats();
        stats.setUsedBoosters("DOUBLE_UP:1,POWER_ALLROUNDER:5");

        assertThat(stats.getUsedBoosterCount(Booster.DOUBLE_UP)).isEqualTo(1);
        assertThat(stats.getUsedBoosterCount(Booster.POWER_ALLROUNDER)).isEqualTo(5);
    }

    @Test
    void parsesBase36CompactCounts() {
        UserOverallStats stats = new UserOverallStats();
        stats.setUsedBoosters("00100a0");

        assertThat(stats.getUsedBoosterCount(Booster.DOUBLE_UP)).isEqualTo(1);
        assertThat(stats.getUsedBoosterCount(Booster.POWER_ALLROUNDER)).isEqualTo(10);
    }
}
