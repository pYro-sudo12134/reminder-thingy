package by.losik.resource;

import by.losik.entity.User;
import by.losik.repository.UserRepository;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class UserResource {

    private static final Logger log = LoggerFactory.getLogger(UserResource.class);

    private final UserRepository userRepository;

    @Inject
    public UserResource(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GET
    @Path("/email/{email}")
    public Response getUserByEmail(@PathParam("email") String email) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                return Response.ok(Map.of(
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "telegramChatId", user.getTelegramChatId() != null ? user.getTelegramChatId() : ""
                )).build();
            }
            return Response.status(404).build();
        } catch (Exception e) {
            log.error("Error getting user by email: {}", email, e);
            return Response.status(500).build();
        }
    }
}