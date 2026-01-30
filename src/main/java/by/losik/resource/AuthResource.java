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

import java.util.Map;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class AuthResource {

    private final UserRepository userRepository;

    @Inject
    public AuthResource(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response login(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @Context HttpServletRequest request) {

        if (username == null || username.isBlank() ||
                password == null || password.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Username and password are required"))
                    .build();
        }

        boolean isValid = userRepository.validateCredentials(username, password);

        if (!isValid) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Invalid credentials"))
                    .build();
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("username", username);
        session.setMaxInactiveInterval(30 * 60);

        return Response.ok(Map.of(
                "username", username,
                "sessionId", session.getId()
        )).build();
    }

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
            userRepository.createUser(username, password, email);

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
        return Response.ok(Map.of("username", username)).build();
    }

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