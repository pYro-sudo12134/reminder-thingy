package by.losik.resource;

import by.losik.config.ExternalConfig;
import by.losik.repository.UserRepository;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/external")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class ExternalResource {
    private final UserRepository userRepository;

    private final String apiKey;

    @Inject
    public ExternalResource(UserRepository userRepository,
                            ExternalConfig externalConfig) {
        this.userRepository = userRepository;
        this.apiKey = externalConfig.getAPI_KEY();
    }

    @GET
    @Path("/user/email/{email}")
    public Response getUserByEmail(@PathParam("email") String email,
                                   @HeaderParam("X-API-Key") String apiKey) {
        if (!this.apiKey.equals(apiKey)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        var user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(Map.of(
                "telegramChatId", user.get().getTelegramChatId(),
                "email", user.get().getEmail()
        )).build();
    }
}
