package com.unipi.dynamicplugins.plugincore.controller;

import com.unipi.dynamicplugins.plugincore.model.PluginEntity;
import com.unipi.dynamicplugins.plugincore.service.PluginService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PluginController.class)
@Import(com.unipi.dynamicplugins.plugincore.config.SecurityConfig.class)
class PluginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PluginService service;

    // Plugin list (no auth required)
    @Test
    void list_shouldReturnOk() throws Exception {
        PluginEntity entity = new PluginEntity("Demo", "desc", true, "path");
        entity.setId(1L); // <---- set an id!
        when(service.listPlugins()).thenReturn(List.of(entity));
        mockMvc.perform(get("/plugins"))
                .andExpect(status().isOk());
    }

    // Auth required endpoint
    @Test
    void activate_shouldReturnOk() throws Exception {
        PluginEntity entity = new PluginEntity("Demo", "desc", false, "path");
        when(service.activatePlugin(1L)).thenReturn(entity);
        mockMvc.perform(post("/plugins/1/activate")
                        .with(httpBasic("admin", "admin123"))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // Unauthorized
    @Test
    void activate_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/plugins/1/activate").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
