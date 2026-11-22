package com.techpulse.taskmanagement;

import com.techpulse.taskmanagement.entity.User;
import com.techpulse.taskmanagement.repository.UserRepository;
import com.techpulse.taskmanagement.security.AuthenticatedUser;
import com.techpulse.taskmanagement.security.JwtService;
import com.techpulse.taskmanagement.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-signing-key-with-at-least-32-bytes")
@AutoConfigureMockMvc
class TaskManagementApiIntegrationTests {
    private static final String PASSWORD = "SecurePass123!";
    private static final AtomicInteger EMAIL_SUFFIX = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${local.server.port}")
    private int port;

    @Test
    void h2ConsoleIsPublicForLocalDatabaseInspection() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/h2-console/"))
                .GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void registrationAndLoginReturnTenantBoundJwtWithoutPasswordData() throws Exception {
        String email = email("admin");
        MvcResult registration = registerTenant("First tenant", email);
        String response = registration.getResponse().getContentAsString();
        long tenantId = numberAt(response, "tenantId");
        long adminId = numberAt(response, "adminUserId");
        mockMvc.perform(post("/tenant/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantName\":\"Second name\",\"admin\":{\"email\":\""
                                + email + "\",\"password\":\"" + PASSWORD + "\"}}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/tenant/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantName\":\"\",\"admin\":{\"email\":\""
                                + email("invalid") + "\",\"password\":\"" + PASSWORD + "\"}}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());

        String token = login(email);
        AuthenticatedUser principal = jwtService.parseToken(token);
        assertThat(principal.userId()).isEqualTo(adminId);
        assertThat(principal.tenantId()).isEqualTo(tenantId);
        assertThat(principal.role().name()).isEqualTo("ADMIN");

        User stored = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(stored.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(stored.getPassword()).startsWith("$2");
    }

    @Test
    void tenantRolesAndOwnershipAreEnforcedAcrossTaskOperations() throws Exception {
        String adminOneEmail = email("admin-one");
        String adminOne = tokenFromRegistration("Tenant one", adminOneEmail);
        String managerOneEmail = email("manager-one");
        String userAliceEmail = email("alice");
        String userBobEmail = email("bob");
        createUser(adminOne, managerOneEmail, "MANAGER");
        long aliceId = createUser(adminOne, userAliceEmail, "USER");
        long bobId = createUser(adminOne, userBobEmail, "USER");
        String managerOne = login(managerOneEmail);
        String alice = login(userAliceEmail);
        String bob = login(userBobEmail);

        long taskAlice = createTask(managerOne, "Alice task");
        long taskBob = createTask(managerOne, "Bob task");
        assignTask(managerOne, taskAlice, aliceId).andExpect(status().isOk());
        assignTask(managerOne, taskBob, bobId).andExpect(status().isOk());

        String adminTwoEmail = email("admin-two");
        String adminTwo = tokenFromRegistration("Tenant two", adminTwoEmail);
        String managerTwoEmail = email("manager-two");
        String userTwoEmail = email("tenant-two-user");
        createUser(adminTwo, managerTwoEmail, "MANAGER");
        long userTwoId = createUser(adminTwo, userTwoEmail, "USER");
        String managerTwo = login(managerTwoEmail);
        String userTwo = login(userTwoEmail);
        long taskTwo = createTask(managerTwo, "Tenant two task");
        assignTask(managerTwo, taskTwo, userTwoId).andExpect(status().isOk());

        mockMvc.perform(get("/tasks").header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Alice task"))
                .andExpect(jsonPath("$[0].assignedToId").value(aliceId));
        mockMvc.perform(get("/tasks").header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
        mockMvc.perform(get("/tasks").header("Authorization", bearer(adminOne)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
        mockMvc.perform(get("/tasks").header("Authorization", bearer(managerOne)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
        mockMvc.perform(get("/tasks").header("Authorization", bearer(managerTwo)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(put("/tasks/{id}/status", taskAlice)
                        .header("Authorization", bearer(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(put("/tasks/{id}/status", taskBob)
                        .header("Authorization", bearer(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/tasks/{id}/status", taskAlice)
                        .header("Authorization", bearer(managerOne)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/tasks").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"No access\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/tasks").header("Authorization", bearer(managerOne))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Forged tenant\",\"tenant_id\":999}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/users").header("Authorization", bearer(managerOne))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email("forbidden")
                                + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"USER\"}"))
                .andExpect(status().isForbidden());

        assignTask(managerOne, taskAlice, userTwoId).andExpect(status().isNotFound());
        assignTask(managerTwo, taskAlice, userTwoId).andExpect(status().isNotFound());
        mockMvc.perform(put("/tasks/{id}/status", taskAlice)
                        .header("Authorization", bearer(userTwo)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/tasks").header("Authorization", "Bearer malformed.token.value"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/tasks")).andExpect(status().isUnauthorized());
        assertThatThrownBy(TenantContext::requireTenantId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void databaseForeignKeyRejectsCrossTenantTaskAssignee() throws Exception {
        String adminOneEmail = email("db-admin-one");
        String adminOne = tokenFromRegistration("DB tenant one", adminOneEmail);
        String userOneEmail = email("db-user-one");
        createUser(adminOne, userOneEmail, "USER");

        String adminTwoEmail = email("db-admin-two");
        String adminTwo = tokenFromRegistration("DB tenant two", adminTwoEmail);
        String userTwoEmail = email("db-user-two");
        long userTwoId = createUser(adminTwo, userTwoEmail, "USER");

        long tenantOneId = userRepository.findByEmailIgnoreCase(userOneEmail).orElseThrow()
                .getTenant().getId();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into tasks (title, description, status, assigned_to, tenant_id) "
                        + "values (?, ?, ?, ?, ?)",
                "forbidden cross-tenant assignment", "", "TODO", userTwoId, tenantOneId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void adminCanManageUsersAndManagerCanOnlyViewNonManagerUsers() throws Exception {
        String adminEmail = email("management-admin");
        String adminToken = tokenFromRegistration("Management tenant", adminEmail);
        long adminId = userRepository.findByEmailIgnoreCase(adminEmail).orElseThrow().getId();
        String managerEmail = email("management-manager");
        String memberEmail = email("management-member");
        long managerId = createUser(adminToken, managerEmail, "MANAGER");
        long memberId = createUser(adminToken, memberEmail, "USER");
        String managerToken = login(managerEmail);

        mockMvc.perform(get("/users").header("Authorization", bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[?(@.role == 'MANAGER')]", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(get("/users/{id}", managerId).header("Authorization", bearer(managerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/users/{id}", memberId).header("Authorization", bearer(managerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
        mockMvc.perform(patch("/users/{id}", memberId).header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"blocked@example.test\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/users/{id}", memberId).header("Authorization", bearer(managerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/users").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)));
        mockMvc.perform(get("/users/{id}", memberId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        String updatedEmail = email("updated-member");
        mockMvc.perform(patch("/users/{id}", memberId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + updatedEmail + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(updatedEmail));
        mockMvc.perform(patch("/users/{id}", memberId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"ChangedPass123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + updatedEmail + "\",\"password\":\"ChangedPass123!\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/users/{id}", adminId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/{id}", adminId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/users/{id}", memberId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/users/{id}", memberId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        String otherAdminEmail = email("other-tenant-admin");
        String otherAdminToken = tokenFromRegistration("Other management tenant", otherAdminEmail);
        long otherUserId = createUser(otherAdminToken, email("other-member"), "USER");
        mockMvc.perform(get("/users/{id}", otherUserId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/users/{id}", otherUserId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/users/{id}", otherUserId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void expiredJwtIsRejected() throws Exception {
        String email = email("expired");
        registerTenant("Expiry tenant", email);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        String token = new JwtService("test-only-signing-key-with-at-least-32-bytes", Duration.ofMillis(1))
                .createToken(user);
        Thread.sleep(30);
        mockMvc.perform(get("/tasks").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    private MvcResult registerTenant(String name, String email) throws Exception {
        return mockMvc.perform(post("/tenant/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantName\":\"" + name + "\",\"admin\":{\"email\":\""
                                + email + "\",\"password\":\"" + PASSWORD + "\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adminEmail").value(email))
                .andReturn();
    }

    private String tokenFromRegistration(String tenant, String adminEmail) throws Exception {
        registerTenant(tenant, adminEmail);
        return login(adminEmail);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andExpect(status().isOk()).andReturn();
        return stringAt(result.getResponse().getContentAsString(), "accessToken");
    }

    private long createUser(String adminToken, String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/users").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                                + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist()).andReturn();
        return numberAt(result.getResponse().getContentAsString(), "id");
    }

    private long createTask(String managerToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/tasks").header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":\"test\"}"))
                .andExpect(status().isCreated()).andReturn();
        return numberAt(result.getResponse().getContentAsString(), "id");
    }

    private org.springframework.test.web.servlet.ResultActions assignTask(String managerToken, long taskId,
                                                                           long userId) throws Exception {
        return mockMvc.perform(put("/tasks/{id}/assign", taskId)
                .header("Authorization", bearer(managerToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + userId + "}"));
    }

    private String email(String prefix) {
        return prefix + EMAIL_SUFFIX.incrementAndGet() + "@example.test";
    }

    private String loginBody(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long numberAt(String json, String field) throws Exception {
        return Long.parseLong(stringAt(json, field));
    }

    private String stringAt(String json, String field) throws Exception {
        tools.jackson.databind.JsonNode node = new tools.jackson.databind.ObjectMapper().readTree(json);
        return node.get(field).asString();
    }
}
