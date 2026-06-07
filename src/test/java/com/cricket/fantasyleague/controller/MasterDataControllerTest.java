package com.cricket.fantasyleague.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.service.masterdata.MasterDataConfigService;
import com.cricket.fantasyleague.service.masterdata.MasterDataConfigService.FantasyPlayerConfigInitSummary;

@ExtendWith(MockitoExtension.class)
class MasterDataControllerTest {

    @Mock
    private MasterDataConfigService masterDataConfigService;

    @Test
    void initializeFantasyPlayerConfigPassesLeagueIdAndReturnsSummary() {
        MasterDataController controller = new MasterDataController(masterDataConfigService);
        when(masterDataConfigService.initializeFantasyPlayerConfigs(1))
                .thenReturn(new FantasyPlayerConfigInitSummary(1, 2, 3, 5));

        ResponseEntity<ApiResponse> response = controller.initializeFantasyPlayerConfig(1);

        verify(masterDataConfigService).initializeFantasyPlayerConfigs(1);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .contains("created=2")
                .contains("skipped=3")
                .contains("totalPlayers=5");
    }
}
