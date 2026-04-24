package by.losik.resource;

import by.losik.repository.UserRepository;
import by.losik.service.TelegramBindingService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class UserResource {

    private static final Logger log = LoggerFactory.getLogger(UserResource.class);

    private final UserRepository userRepository;
    private final TelegramBindingService telegramBindingService;

    @Inject
    public UserResource(UserRepository userRepository, TelegramBindingService telegramBindingService) {
        this.userRepository = userRepository;
        this.telegramBindingService = telegramBindingService;
    }

    @GET
    @Path("/email/{email}")
    public Response getUserByEmail(@PathParam("email") String email) {
        return Optional.ofNullable(email)
                .map(userRepository::findByEmail)
                .flatMap(opt -> opt)
                .map(user -> Response.ok(Map.of(
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "telegramChatId", Optional.ofNullable(user.getTelegramChatId())
                                .map(String::valueOf)
                                .orElse("")
                )).build())
                .orElseGet(() -> {
                    log.warn("User not found by email: {}", email);
                    return Response.status(404).build();
                });
    }

    @POST
    @Path("/{id}/telegram/generate-code")
    public Response generateTelegramCode(@PathParam("id") Long id) {
        return Optional.ofNullable(id)
                .map(telegramBindingService::generateBindingCode)
                .map(result -> Boolean.TRUE.equals(result.get("success"))
                        ? Response.ok(result).build()
                        : Response.status(Response.Status.BAD_REQUEST).entity(result).build())
                .orElse(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("success", false, "error", "Invalid user ID"))
                        .build());
    }

    @POST
    @Path("/{id}/telegram/bind")
    public Response bindTelegram(@PathParam("id") Long id, Map<String, Object> request) {
        return Optional.ofNullable(id)
                .flatMap(uid -> Optional.ofNullable(request)
                        .map(req -> {
                            String code = (String) req.get("code");
                            Long chatId = Optional.ofNullable(req.get("chatId"))
                                    .map(n -> ((Number) n).longValue())
                                    .orElse(null);
                            return Map.entry(uid, new Object[]{code, chatId});
                        }))
                .filter(entry -> entry.getValue()[0] != null && entry.getValue()[1] != null)
                .map(entry -> telegramBindingService.validateAndBind(
                        entry.getKey(),
                        (String) entry.getValue()[0],
                        (Long) entry.getValue()[1]))
                .map(result -> Boolean.TRUE.equals(result.get("success"))
                        ? Response.ok(result).build()
                        : Response.status(Response.Status.BAD_REQUEST).entity(result).build())
                .orElse(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("success", false, "error", "Missing code or chatId"))
                        .build());
    }

    @GET
    @Path("/{id}/telegram/status")
    public Response getTelegramStatus(@PathParam("id") Long id) {
        return Optional.ofNullable(id)
                .map(telegramBindingService::getBindingStatus)
                .map(Response::ok)
                .map(Response.ResponseBuilder::build)
                .orElse(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("success", false, "error", "Invalid user ID"))
                        .build());
    }

    @DELETE
    @Path("/{id}/telegram")
    public Response unbindTelegram(@PathParam("id") Long id) {
        return Optional.ofNullable(id)
                .map(telegramBindingService::unbindTelegram)
                .map(result -> Boolean.TRUE.equals(result.get("success"))
                        ? Response.ok(result).build()
                        : Response.status(Response.Status.BAD_REQUEST).entity(result).build())
                .orElse(Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("success", false, "error", "Invalid user ID"))
                        .build());
    }
}