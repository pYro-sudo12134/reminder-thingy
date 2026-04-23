package by.losik.resource;

import by.losik.service.TelegramBindingService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Path("/user/{userId}/telegram")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class TelegramController {
    private static final Logger log = LoggerFactory.getLogger(TelegramController.class);

    private final TelegramBindingService telegramBindingService;

    @Inject
    public TelegramController(TelegramBindingService telegramBindingService) {
        this.telegramBindingService = telegramBindingService;
    }

    @POST
    @Path("/code")
    public Response generateCode(@PathParam("userId") Long userId) {
        log.info("Generating Telegram binding code for user: {}", userId);
        Map<String, Object> result = telegramBindingService.generateBindingCode(userId);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Response.ok(result).build();
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(result)
                .build();
    }

    @GET
    @Path("/status")
    public Response getStatus(@PathParam("userId") Long userId) {
        log.info("Getting Telegram binding status for user: {}", userId);
        Map<String, Object> result = telegramBindingService.getBindingStatus(userId);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Response.ok(result).build();
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(result)
                .build();
    }

    @DELETE
    @Path("/unbind")
    public Response unbind(@PathParam("userId") Long userId) {
        log.info("Unbinding Telegram for user: {}", userId);
        Map<String, Object> result = telegramBindingService.unbindTelegram(userId);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Response.ok(result).build();
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(result)
                .build();
    }

    @POST
    @Path("/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response validateCode(Map<String, Object> payload) {
        String code = (String) payload.get("code");
        Long chatId = payload.get("chatId") != null ?
                ((Number) payload.get("chatId")).longValue() : null;

        if (code == null || chatId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("success", false, "error", "Missing code or chatId"))
                    .build();
        }

        log.info("Validating code {} for chatId: {}", code, chatId);
        Map<String, Object> result = telegramBindingService.validateAndBind(null, code, chatId);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Response.ok(result).build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(result)
                .build();
    }

    @POST
    @Path("/unbind-by-chatid")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response unbindByChatId(Map<String, Object> payload) {
        Long chatId = payload.get("chatId") != null ?
                ((Number) payload.get("chatId")).longValue() : null;

        if (chatId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("success", false, "error", "Missing chatId"))
                    .build();
        }

        log.info("Unbinding by chatId: {}", chatId);
        Map<String, Object> result = telegramBindingService.unbindTelegramByChatId(chatId);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Response.ok(result).build();
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(result)
                .build();
    }

    @POST
    @Path("/bot/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response botValidateCode(Map<String, Object> payload) {
        String code = (String) payload.get("code");
        Long chatId = payload.get("chatId") != null ?
                ((Number) payload.get("chatId")).longValue() : null;

        if (code == null || chatId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("success", false, "error", "Missing code or chatId"))
                    .build();
        }

        log.info("Bot validating code {} for chatId: {}", code, chatId);
        Map<String, Object> result = telegramBindingService.validateAndBind(null, code, chatId);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Response.ok(result).build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(result)
                .build();
    }

    @GET
    @Path("/bot/status")
    public Response botGetStatus(@QueryParam("chatId") Long chatId) {
        if (chatId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("success", false, "error", "Missing chatId"))
                    .build();
        }

        Map<String, Object> result = telegramBindingService.getBindingStatusByChatId(chatId);
        return Response.ok(result).build();
    }

    @POST
    @Path("/bot/unbind")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response botUnbind(Map<String, Object> payload) {
        Long chatId = payload.get("chatId") != null ?
                ((Number) payload.get("chatId")).longValue() : null;

        if (chatId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("success", false, "error", "Missing chatId"))
                    .build();
        }

        Map<String, Object> result = telegramBindingService.unbindTelegramByChatId(chatId);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Response.ok(result).build();
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(result)
                .build();
    }
}