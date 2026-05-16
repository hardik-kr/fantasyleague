package com.cricket.fantasyleague.controller.admin;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.cricket.fantasyleague.service.masterdata.MasterDataReadService;

@ExtendWith(MockitoExtension.class)
class MasterCacheAdminControllerTest {

    @Mock
    private MasterDataReadService masterDataReadService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MasterCacheAdminController(masterDataReadService)).build();
    }

    @Test
    void postReloadInvokesService() throws Exception {
        when(masterDataReadService.isEnabled()).thenReturn(true);
        mockMvc.perform(post("/api/admin/cache/master/reload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reloaded"))
                .andExpect(jsonPath("$.masterCacheEnabled").value(true));
        verify(masterDataReadService).reloadMatchesAndCachedPlayerLeagues();
    }

    @Test
    void postEvictInvokesService() throws Exception {
        when(masterDataReadService.isEnabled()).thenReturn(false);
        mockMvc.perform(post("/api/admin/cache/master/evict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("evicted"))
                .andExpect(jsonPath("$.masterCacheEnabled").value(false));
        verify(masterDataReadService).evictAll();
    }
}
