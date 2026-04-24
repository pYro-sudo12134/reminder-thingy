package by.losik.resource;

import by.losik.service.TelegramBindingService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/telegram/bot")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class TelegramResource {

    private final TelegramBindingService bindingService;

    @Inject
    public TelegramResource(TelegramBindingService bindingService) {
        this.bindingService = bindingService;
    }

    @POST
    @Path("/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response validateCode(Map<String, Object> payload) {
        String code = (String) payload.get("code");
        Long chatId = ((Number) payload.get("chatId")).longValue();

        Map<String, Object> result = bindingService.validateAndBindByChatId(code, chatId);
        return result.containsKey("error")
                ? Response.status(Response.Status.BAD_REQUEST).entity(result).build()
                : Response.ok(result).build();
    }

    @GET
    @Path("/status")
    public Response getStatus(@QueryParam("chatId") Long chatId) {
        Map<String, Object> result = bindingService.getBindingStatusByChatId(chatId);
        return Response.ok(result).build();
    }

    @POST
    @Path("/unbind")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response unbind(Map<String, Object> payload) {
        Long chatId = ((Number) payload.get("chatId")).longValue();
        Map<String, Object> result = bindingService.unbindTelegramByChatId(chatId);
        return result.containsKey("error")
                ? Response.status(Response.Status.NOT_FOUND).entity(result).build()
                : Response.ok(result).build();
    }
}