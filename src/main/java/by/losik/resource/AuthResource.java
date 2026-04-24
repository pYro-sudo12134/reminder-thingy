package by.losik.resource;

import by.losik.repository.UserRepository;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST ресурс для аутентификации и управления сессиями.
 * <p>
 * Предоставляет endpoints для:
 * <ul>
 *     <li>Входа в систему (login)</li>
 *     <li>Регистрации нового пользователя</li>
 *     <li>Выхода из системы (logout)</li>
 *     <li>Проверки статуса аутентификации</li>
 *     <li>Получения информации о текущем пользователе</li>
 * </ul>
 */
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class AuthResource {

    private static final Logger log = LoggerFactory.getLogger(AuthResource.class);

    /** Таймаут сессии по умолчанию (30 минут) */
    private static final int DEFAULT_SESSION_TIMEOUT_MIN = 30;

    private final UserRepository userRepository;

    /**
     * Создаёт ресурс аутентификации с внедрённым репозиторием.
     *
     * @param userRepository репозиторий пользователей
     */
    @Inject
    public AuthResource(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Аутентифицирует пользователя и создаёт сессию.
     *
     * @param username имя пользователя
     * @param password пароль
     * @param request HTTP запрос для получения сессии
     * @return Response с sessionId или ошибкой
     */
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response login(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @Context HttpServletRequest request) {

        if (username == null || username.isBlank()) {
            log.error("Username is null or blank");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Username and password are required"))
                    .build();
        }

        if (password == null || password.isBlank()) {
            log.error("Password is null or blank");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Username and password are required"))
                    .build();
        }

        try {
            boolean isValid = userRepository.validateCredentials(username, password);
            log.info("validateCredentials returned: {}", isValid);

            if (!isValid) {
                log.warn("Invalid credentials for user: {}", username);
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of("error", "Invalid username or password"))
                        .build();
            }

            HttpSession session = request.getSession(true);
            log.debug("Session created: {}", session.getId());
            session.setAttribute("username", username);
            session.setMaxInactiveInterval(DEFAULT_SESSION_TIMEOUT_MIN * 60);

            return Response.ok(Map.of(
                    "username", username,
                    "sessionId", session.getId()
            )).build();

        } catch (Exception e) {
            log.error("Exception in login: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Login failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Регистрирует нового пользователя.
     *
     * @param username имя пользователя
     * @param password пароль
     * @param email email
     * @param request HTTP запрос для получения сессии
     * @return Response с sessionId или ошибкой
     */
    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response register(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @FormParam("email") String email,
            @Context HttpServletRequest request) {

        if (username == null || username.isBlank() ||
                password == null || password.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Username and password are required"))
                    .build();
        }

        if (userRepository.userExists(username)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Username already exists"))
                    .build();
        }

        try {
            userRepository.createUser(username, email, password);

            HttpSession session = request.getSession(true);
            session.setAttribute("username", username);
            session.setMaxInactiveInterval(30 * 60);

            return Response.ok(Map.of(
                    "username", username,
                    "sessionId", session.getId(),
                    "message", "Registration successful"
            )).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Registration failed"))
                    .build();
        }
    }

    /**
     * Получает информацию о текущем пользователе.
     *
     * @param request HTTP запрос для получения сессии
     * @return Response с username и userId или ошибкой
     */
    @GET
    @Path("/me")
    public Response getCurrentUser(@Context HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Not authenticated"))
                    .build();
        }

        String username = (String) session.getAttribute("username");
        
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "User not found"))
                    .build();
        }
        
        var user = userOpt.get();
        return Response.ok(Map.of(
                "username", username,
                "userId", String.valueOf(user.getId())
        )).build();
    }

    /**
     * Проверяет статус аутентификации.
     *
     * @param request HTTP запрос для получения сессии
     * @return Response с active=true/false
     */
    @GET
    @Path("/is-auth")
    public Response getStatus(@Context HttpServletRequest request) {
        return Response.ok(Map.of("active", request.getSession(false) != null)).build();
    }

    /**
     * Завершает сессию пользователя (logout).
     *
     * @param request HTTP запрос для получения сессии
     * @return Response с подтверждением
     */
    @POST
    @Path("/logout")
    public Response logout(@Context HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Response.ok(Map.of("message", "Logged out")).build();
    }
}