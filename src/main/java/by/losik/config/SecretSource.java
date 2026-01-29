package by.losik.config;

public enum SecretSource {
    DOCKER_SECRETS,
    LOCAL_FILES,
    AWS_SECRETS_MANAGER,
    ENVIRONMENT
}