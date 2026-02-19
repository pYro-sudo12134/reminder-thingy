package by.losik.resource;

import by.losik.dto.PasswordResetRequest;
import by.losik.dto.PasswordResetResponse;
import by.losik.entity.User;
import by.losik.repository.UserRepository;
import by.losik.service.EmailPasswordResetService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Path("/auth/password")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
public class PasswordResetResource {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetResource.class);

    private final EmailPasswordResetService emailService;
    private final UserRepository userRepository;

    @Inject
    public PasswordResetResource(EmailPasswordResetService emailService,
                                 UserRepository userRepository) {
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    @POST
    @Path("/forgot")
    public void forgotPassword(@Suspended AsyncResponse asyncResponse,
                               PasswordResetRequest request) {
        String email = request.email();

        if (email == null || email.isBlank()) {
            asyncResponse.resume(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(new PasswordResetResponse(false, "Email is required", null))
                            .build()
            );
            return;
        }

        log.info("Password reset requested for email: {}", email);

        CompletableFuture.supplyAsync(() -> userRepository.findByEmail(email))
                .thenCompose(optionalUser -> {
                    if (optionalUser.isEmpty()) {
                        log.info("Password reset requested for non-existent email: {}", email);
                        return CompletableFuture.completedFuture(
                                new PasswordResetResponse(true,
                                        "If the email exists, reset instructions will be sent", null)
                        );
                    }

                    User user = optionalUser.get();
                    String username = user.getUsername();
                    String userEmail = user.getEmail(); // получаем email из БД

                    return emailService.sendPasswordResetEmail(userEmail, username)
                            .thenApply(messageId -> {
                                log.info("Password reset email sent to: {}, messageId: {}", userEmail, messageId);
                                return new PasswordResetResponse(true,
                                        "If the email exists, reset instructions will be sent", null);
                            })
                            .exceptionally(throwable -> {
                                log.error("Failed to send password reset email to: {}", userEmail, throwable);
                                return new PasswordResetResponse(true,
                                        "If the email exists, reset instructions will be sent", null);
                            });
                })
                .thenAccept(response ->
                        asyncResponse.resume(Response.ok(response).build())
                )
                .exceptionally(throwable -> {
                    log.error("Error processing password reset request", throwable);
                    asyncResponse.resume(
                            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                    .entity(new PasswordResetResponse(false,
                                            "An error occurred processing your request", null))
                                    .build()
                    );
                    return null;
                });
    }

    @GET
    @Path("/validate")
    public void validateToken(@Suspended AsyncResponse asyncResponse,
                              @QueryParam("token") String token) {
        if (token == null || token.isBlank()) {
            asyncResponse.resume(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(new PasswordResetResponse(false, "Token is required", null))
                            .build()
            );
            return;
        }

        CompletableFuture.supplyAsync(() -> emailService.validateResetToken(token))
                .thenAccept(username -> {
                    if (username == null) {
                        asyncResponse.resume(
                                Response.status(Response.Status.BAD_REQUEST)
                                        .entity(new PasswordResetResponse(false,
                                                "Invalid or expired token", null))
                                        .build()
                        );
                    } else {
                        asyncResponse.resume(
                                Response.ok(new PasswordResetResponse(true,
                                                "Token is valid", token))
                                        .build()
                        );
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Error validating token", throwable);
                    asyncResponse.resume(
                            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                    .entity(new PasswordResetResponse(false,
                                            "Error validating token", null))
                                    .build()
                    );
                    return null;
                });
    }

    @POST
    @Path("/reset")
    public void resetPassword(@Suspended AsyncResponse asyncResponse,
                              PasswordResetRequest request) {
        String token = request.token();
        String newPassword = request.newPassword();

        if (token == null || token.isBlank()) {
            asyncResponse.resume(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(new PasswordResetResponse(false, "Token is required", null))
                            .build()
            );
            return;
        }

        if (newPassword == null || newPassword.isBlank()) {
            asyncResponse.resume(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(new PasswordResetResponse(false, "New password is required", null))
                            .build()
            );
            return;
        }

        if (newPassword.length() < 6) {
            asyncResponse.resume(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(new PasswordResetResponse(false,
                                    "Password must be at least 6 characters", null))
                            .build()
            );
            return;
        }

        log.info("Processing password reset with token");

        CompletableFuture.supplyAsync(() -> emailService.validateResetToken(token))
                .thenCompose(username -> {
                    if (username == null) {
                        log.warn("Invalid or expired token used for password reset");
                        return CompletableFuture.completedFuture(
                                new PasswordResetResponse(false, "Invalid or expired token", null)
                        );
                    }

                    Optional<User> userOpt = userRepository.findByUsername(username);

                    if (userOpt.isEmpty()) {
                        log.error("User not found after token validation: {}", username);
                        return CompletableFuture.completedFuture(
                                new PasswordResetResponse(false, "User not found", null)
                        );
                    }

                    User user = userOpt.get();
                    boolean updated = userRepository.updatePassword(username, newPassword);

                    if (!updated) {
                        log.error("Failed to update password for user: {}", username);
                        return CompletableFuture.completedFuture(
                                new PasswordResetResponse(false, "Failed to update password", null)
                        );
                    }

                    log.info("Password successfully reset for user: {}", username);

                    emailService.sendPasswordChangedConfirmation(user.getEmail(), username)
                            .whenComplete((messageId, throwable) -> {
                                if (throwable != null) {
                                    log.error("Failed to send confirmation email to {}", user.getEmail(), throwable);
                                }
                            });

                    return CompletableFuture.completedFuture(
                            new PasswordResetResponse(true,
                                    "Password successfully reset. You can now login with your new password.",
                                    null)
                    );
                })
                .thenAccept(response -> {
                    if (response.success()) {
                        asyncResponse.resume(Response.ok(response).build());
                    } else {
                        asyncResponse.resume(
                                Response.status(Response.Status.BAD_REQUEST)
                                        .entity(response)
                                        .build()
                        );
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Error resetting password", throwable);
                    asyncResponse.resume(
                            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                    .entity(new PasswordResetResponse(false,
                                            "An error occurred resetting your password", null))
                                    .build()
                    );
                    return null;
                });
    }

    @POST
    @Path("/reset-form")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void resetPasswordForm(@Suspended AsyncResponse asyncResponse,
                                  @FormParam("token") String token,
                                  @FormParam("newPassword") String newPassword) {

        PasswordResetRequest request = new PasswordResetRequest(null, newPassword, token);
        resetPassword(asyncResponse, request);
    }
}