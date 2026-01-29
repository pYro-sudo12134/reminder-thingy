import os
import json
from pathlib import Path
from typing import Dict, Optional

class SecretLoader:
    @staticmethod
    def load_secrets() -> Dict[str, str]:
        secrets = {}

        docker_secrets = SecretLoader._load_docker_secrets()
        secrets.update(docker_secrets)

        if not docker_secrets:
            local_secrets = SecretLoader._load_local_files()
            secrets.update(local_secrets)

        env_secrets = SecretLoader._load_from_env()
        secrets.update({k: v for k, v in env_secrets.items() if k not in secrets})

        print(f"Loaded {len(secrets)} secrets")
        return secrets

    @staticmethod
    def _load_docker_secrets() -> Dict[str, str]:
        secrets = {}
        secrets_path = Path("/run/secrets")

        if secrets_path.exists() and secrets_path.is_dir():
            for file in secrets_path.iterdir():
                if file.is_file():
                    try:
                        key = file.stem.upper()
                        value = file.read_text().strip()
                        secrets[key] = value
                        print(f"Loaded Docker secret: {key}")
                    except Exception as e:
                        print(f"Error reading Docker secret {file}: {e}")

        return secrets

    @staticmethod
    def _load_local_files() -> Dict[str, str]:
        """Загрузка секретов из локальных файлов"""
        secrets = {}

        json_paths = [
            Path("./secrets/secrets.json"),
            Path("/app/secrets/secrets.json"),
            Path("../secrets/secrets.json"),
        ]

        for json_path in json_paths:
            if json_path.exists():
                try:
                    with open(json_path, 'r') as f:
                        data = json.load(f)
                        secrets.update(data)
                    print(f"Loaded secrets from {json_path}")
                    break
                except Exception as e:
                    print(f"Error reading JSON secrets {json_path}: {e}")

        return secrets

    @staticmethod
    def _load_from_env() -> Dict[str, str]:
        secrets = {}
        env_keys = [
            "NLP_GRPC_API_KEY",
            "JWT_SECRET",
            "SERVICE_TOKEN",
            "NLP_SERVICE_HOST",
            "NLP_SERVICE_PORT",
            "GRPC_USE_TLS",
            "WS_PORT",
        ]

        for key in env_keys:
            value = os.getenv(key)
            if value:
                secrets[key] = value

        return secrets

    @staticmethod
    def get_api_key() -> str:
        secrets = SecretLoader.load_secrets()

        api_key = (
                secrets.get("NLP_GRPC_API_KEY") or
                os.getenv("NLP_GRPC_API_KEY") or
                "default-insecure-key-for-development"
        )

        if not api_key or api_key == "default-insecure-key-for-development":
            print("WARNING: Using default insecure API key!")

        return api_key