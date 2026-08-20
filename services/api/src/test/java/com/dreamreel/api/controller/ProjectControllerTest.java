package com.dreamreel.api.controller;

import com.dreamreel.api.domain.User;
import com.dreamreel.api.domain.UserRole;
import com.dreamreel.api.domain.UserStatus;
import com.dreamreel.api.dto.CreateProjectRequest;
import com.dreamreel.api.repository.UserRepository;
import com.dreamreel.api.security.UserPrincipal;
import com.dreamreel.api.dto.UpdateCanvasRequest;
import com.dreamreel.api.repository.ProjectRepository;
import com.dreamreel.api.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUpAuth() {
        userRepository.deleteAll();
        projectRepository.deleteAll();
        var user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setDisplayName("测试用户");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        var principal = new UserPrincipal(user);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void projectCrudFlow() throws Exception {
        var createRequest = new CreateProjectRequest("测试短剧", com.dreamreel.api.domain.ProjectType.SHORT_DRAMA, "描述");
        var createResult = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("测试短剧"))
                .andExpect(jsonPath("$.data.canvasData").exists())
                .andReturn();

        var id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/projects/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));

        var canvas = "{\"nodes\":[],\"edges\":[]}";
        mockMvc.perform(patch("/api/v1/projects/" + id + "/canvas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCanvasRequest(canvas))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canvasData").value(canvas));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(delete("/api/v1/projects/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMissingProjectReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
