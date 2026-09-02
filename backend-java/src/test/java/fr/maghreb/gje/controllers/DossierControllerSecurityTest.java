package fr.maghreb.gje.controllers;

import fr.maghreb.gje.config.SecurityConfig;
import fr.maghreb.gje.exceptions.GlobalExceptionHandler;
import fr.maghreb.gje.security.JwtAuthFilter;
import fr.maghreb.gje.services.DossierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class DossierControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DossierService dossierService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter; // Mocked to bypass JWT auth check during webmvc tests

    private final String BASE_URL = "/api/v1/dossiers";
    private final UUID TEST_ID = UUID.randomUUID();

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createDossier_AsSuperAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(post(BASE_URL)
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Super Admin n'a pas accès aux dossiers internes des workspaces"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void getAllDossiers_AsSuperAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Super Admin n'a pas accès aux dossiers internes des workspaces"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void getDossierById_AsSuperAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + TEST_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Super Admin n'a pas accès aux dossiers internes des workspaces"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void updateDossier_AsSuperAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(put(BASE_URL + "/" + TEST_ID)
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Super Admin n'a pas accès aux dossiers internes des workspaces"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void transfererDossier_AsSuperAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + TEST_ID + "/transferer")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Super Admin n'a pas accès aux dossiers internes des workspaces"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void archiverDossier_AsSuperAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + TEST_ID + "/archiver"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Super Admin n'a pas accès aux dossiers internes des workspaces"));
    }
}
