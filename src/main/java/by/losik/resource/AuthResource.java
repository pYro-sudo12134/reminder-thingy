package by.losik.resource;

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
    private static final Map<String, String> VALID_USERS = Map.of(
            "admin", "admin123",
            "user", "user123"
    );

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response login(
            @FormParam("username") String username,
            @FormParam("password") String password,
            @Context HttpServletRequest request) {

        if (!isValidUser(username, password)) {
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

    @GET
    @Path("/me")
    public Response getCurrentUser(@Context HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("username") == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
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

    private boolean isValidUser(String username, String password) {
        String storedPassword = VALID_USERS.get(username);
        return storedPassword != null && storedPassword.equals(password);
    }
}