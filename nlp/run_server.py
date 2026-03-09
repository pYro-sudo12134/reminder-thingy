import sys
import os
from pathlib import Path
import logging
import requests

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

current_dir = Path(__file__).parent
sys.path.insert(0, str(current_dir))

def check_ollama():
    """Проверяет доступность Ollama"""
    host = os.getenv('OLLAMA_HOST', 'localhost')
    port = os.getenv('OLLAMA_PORT', '11434')

    try:
        response = requests.get(f"http://{host}:{port}/api/tags", timeout=5)
        if response.status_code == 200:
            models = response.json().get('models', [])
            model_names = [m['name'] for m in models]
            logger.info(f" Ollama is available at {host}:{port}")
            logger.info(f"   Available models: {', '.join(model_names) if model_names else 'none'}")

            required_model = os.getenv('OLLAMA_MODEL', 'llama3.2:latest')
            if required_model in model_names:
                logger.info(f" Required model '{required_model}' is available")
            else:
                logger.info(f"Model '{required_model}' will be pulled on first request")

            return True
        else:
            logger.warning(f"️ Ollama returned status {response.status_code}")
            return False
    except requests.exceptions.ConnectionError:
        logger.error(f" Cannot connect to Ollama at {host}:{port}")
        logger.error("   Make sure Ollama container is running:")
        logger.error("   docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama ollama/ollama")
        return False
    except Exception as e:
        logger.error(f" Error checking Ollama: {e}")
        return False

def check_opensearch():
    """Проверяет доступность OpenSearch"""
    try:
        from opensearchpy import OpenSearch

        host = os.getenv('OPENSEARCH_HOST', 'localstack')
        port = int(os.getenv('OPENSEARCH_PORT', '4510'))
        use_ssl = os.getenv('OPENSEARCH_USE_SSL', 'false').lower() == 'true'
        user = os.getenv('OPENSEARCH_USER', 'admin')
        password = os.getenv('OPENSEARCH_ADMIN_PASSWORD', '')

        auth = (user, password) if password else None

        client = OpenSearch(
            hosts=[{'host': host, 'port': port}],
            http_compress=True,
            use_ssl=use_ssl,
            verify_certs=False,
            http_auth=auth,
            timeout=5
        )

        if client.ping():
            logger.info(" OpenSearch is available")
            return True
    except Exception as e:
        logger.debug(f"OpenSearch connection error: {e}")

    logger.warning("️ OpenSearch is not available")
    return False

def generate_protobuf():
    """Генерирует protobuf код если нужно"""
    try:
        from nlp_service import reminder_parser_pb2
        from nlp_service import reminder_parser_pb2_grpc
        logger.info(" Protobuf modules loaded")
        return True
    except ImportError:
        logger.info("Generating protobuf code...")
        try:
            import subprocess
            import shutil

            proto_file = current_dir / "proto" / "reminder_parser.proto"
            if proto_file.exists():
                cmd = [
                    sys.executable, "-m", "grpc_tools.protoc",
                    f"-I{current_dir / 'proto'}",
                    f"--python_out={current_dir}",
                    f"--grpc_python_out={current_dir}",
                    str(proto_file)
                ]
                subprocess.run(cmd, check=True)

                # Создаем nlp_service директорию если её нет
                nlp_service_dir = current_dir / "nlp_service"
                nlp_service_dir.mkdir(exist_ok=True)

                # Перемещаем сгенерированные файлы
                for file in ["reminder_parser_pb2.py", "reminder_parser_pb2_grpc.py"]:
                    src = current_dir / file
                    if src.exists():
                        dst = nlp_service_dir / file
                        # Читаем и исправляем импорты
                        with open(src, 'r', encoding='utf-8') as f:
                            content = f.read()

                        # Исправляем импорт для корректной работы в пакете
                        content = content.replace(
                            'import reminder_parser_pb2 as reminder__parser__pb2',
                            'from . import reminder_parser_pb2 as reminder__parser__pb2'
                        )

                        with open(dst, 'w', encoding='utf-8') as f:
                            f.write(content)

                        src.unlink()  # Удаляем исходный файл

                logger.info(" Protobuf code generated")
                return True
        except Exception as e:
            logger.error(f"Failed to generate protobuf: {e}")
            return False

def main():
    print("=" * 60)
    print(" NLP Service Launcher")
    print("=" * 60)

    # Генерируем protobuf если нужно
    generate_protobuf()

    # Определяем режим запуска из переменной окружения
    mode = os.getenv('NLP_MODE', 'ollama').lower()

    print(f"\n Mode: {mode}")

    if mode in ['ollama', 'ollama-rag']:
        # Проверяем доступность Ollama
        if not check_ollama():
            logger.error(f" Ollama not available for {mode} mode")
            logger.info("Falling back to rule-based mode...")
            mode = 'rule'
        else:
            if mode == 'ollama-rag':
                check_opensearch()  # Просто логируем статус, не критично

    # Запускаем соответствующий сервер
    try:
        if mode in ['ollama', 'ollama-rag']:
            print(f"\n Запуск Ollama сервера...")
            from llm_agent.ollama_server import serve
            serve()
        elif mode in ['llm-rag', 'llm']:
            print(f"\n Запуск YandexGPT сервера...")
            from llm_agent.server import serve
            serve()
        elif mode == 'ml':
            print("\n Запуск ML enhanced сервера...")
            from nlp_service.server_ml import serve
            serve()
        else:
            print("\n Запуск rule-based сервера...")
            from nlp_service.server import serve
            serve()
    except ImportError as e:
        logger.error(f"Failed to start {mode} server: {e}")
        logger.info("Falling back to rule-based server...")
        try:
            from nlp_service.server import serve
            serve()
        except ImportError as e2:
            logger.error(f"Failed to start fallback server: {e2}")
            sys.exit(1)

if __name__ == "__main__":
    main()