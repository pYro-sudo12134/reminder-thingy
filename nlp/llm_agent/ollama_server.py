import grpc
from concurrent import futures
import logging
import os
import sys
from datetime import datetime
from pathlib import Path

current_dir = Path(__file__).parent.parent
sys.path.insert(0, str(current_dir))

try:
    from nlp_service import reminder_parser_pb2 as pb
    from nlp_service import reminder_parser_pb2_grpc as pb_grpc
    from nlp_service.secret_loader import SecretLoader
except ImportError as e:
    print(f"Import error: {e}")
    sys.exit(1)

from llm_agent.ollama_agent import OllamaAgent, ReminderParseResult
from llm_agent.opensearch_rag import OpenSearchReminderRAG

logger = logging.getLogger(__name__)

class OllamaReminderParserService(pb_grpc.ReminderParserServiceServicer):

    def __init__(self):
        self.request_count = 0
        self.error_count = 0
        self.start_time = datetime.now()

        # Загружаем секреты
        self.secrets = SecretLoader.load_secrets()
        self.api_key = self.secrets.get("NLP_GRPC_API_KEY") or os.getenv("NLP_GRPC_API_KEY")

        logger.info("Initializing Ollama agent...")

        self.use_rag = os.getenv('NLP_MODE', 'ollama').lower() == 'ollama-rag'
        self.rag = None
        if self.use_rag:
            try:
                self.rag = OpenSearchReminderRAG()
                logger.info("OpenSearch RAG initialized")
            except Exception as e:
                logger.warning(f"Failed to initialize OpenSearch RAG: {e}")

        self.agent = OllamaAgent(rag_client=self.rag)

        logger.info("Ollama Agent initialized")
        logger.info(f"Authentication enabled: {bool(self.api_key)}")
        logger.info(f"RAG enabled: {self.use_rag}")

    def _validate_auth(self, context) -> bool:
        if not self.api_key:
            return True

        metadata = dict(context.invocation_metadata())
        auth_header = metadata.get('authorization', '')

        if not auth_header.startswith('Bearer '):
            context.set_code(grpc.StatusCode.UNAUTHENTICATED)
            context.set_details('Missing or invalid authorization header. Use: Bearer <api_key>')
            return False

        token = auth_header[7:]
        if token != self.api_key:
            context.set_code(grpc.StatusCode.UNAUTHENTICATED)
            context.set_details('Invalid API key')
            return False

        return True

    def ParseReminder(self, request: pb.ParseRequest, context):
        if not self._validate_auth(context):
            self.error_count += 1
            return pb.ParseResponse()

        self.request_count += 1

        try:
            logger.info(f"Processing request from user: {request.user_id}")
            logger.info(f"   Text: '{request.text}'")

            language = None
            if request.language_code:
                language = request.language_code[:2].lower()

            result = self.agent.parse_reminder(
                text=request.text,
                language=language or 'ru'
            )

            logger.info(f"Parsed: action='{result.action}', intent={result.intent}, confidence={result.confidence:.2f}")

            response = self._to_protobuf(result, request)

            return response

        except Exception as e:
            self.error_count += 1
            logger.error(f" Error parsing reminder: {e}", exc_info=True)
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            return pb.ParseResponse()

    def HealthCheck(self, request, context):
        return pb.HealthResponse(
            healthy=True,
            model_version=f"3.0.0-ollama",
            supported_languages=['ru', 'en', 'auto'],
            auth_required=bool(self.api_key),
            total_requests=self.request_count,
            error_count=self.error_count
        )

    def _to_protobuf(self, result: ReminderParseResult, request: pb.ParseRequest) -> pb.ParseResponse:
        """Конвертирует результат агента в protobuf"""
        temporal_expr = pb.TemporalExpression()

        if result.time_type == "absolute" and result.datetime:
            temporal_expr.absolute.iso_datetime = result.datetime
            temporal_expr.absolute.natural_language = result.natural_language_time
        elif result.time_type == "relative" and result.relative_seconds:
            temporal_expr.relative.seconds_from_now = result.relative_seconds
            seconds = result.relative_seconds
            if seconds % 3600 == 0:
                temporal_expr.relative.unit = "hours"
                temporal_expr.relative.amount = seconds / 3600
            elif seconds % 60 == 0:
                temporal_expr.relative.unit = "minutes"
                temporal_expr.relative.amount = seconds / 60
            else:
                temporal_expr.relative.unit = "seconds"
                temporal_expr.relative.amount = float(seconds)
        elif result.time_type == "recurring" and result.cron_expression:
            temporal_expr.recurring.cron_expression = result.cron_expression
            temporal_expr.recurring.natural_language = result.natural_language_time

        entities = []
        for entity in (result.entities or []):
            entities.append(pb.Entity(
                text=entity.get('text', ''),
                type=entity.get('type', ''),
                start=entity.get('start', 0),
                end=entity.get('end', 0),
                confidence=entity.get('confidence', 0.0)
            ))

        parsed_reminder = pb.ParsedReminder(
            action=result.action,
            time_expression=temporal_expr,
            normalized_text=result.normalized_text or result.action,
            intent=result.intent,
            entities=entities
        )

        response = pb.ParseResponse(
            reminder_id=f"{request.user_id}_{int(datetime.now().timestamp())}",
            parsed=parsed_reminder,
            confidence=result.confidence,
            language_detected=result.language,
            raw_text=request.text
        )

        return response

def serve():
    """Запускает gRPC сервер"""
    logging.basicConfig(
        level=getattr(logging, os.getenv('LOG_LEVEL', 'INFO')),
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )

    logger.info("Starting Ollama-based NLP server...")

    try:
        test_rag = None
        if os.getenv('NLP_MODE', 'ollama').lower() == 'ollama-rag':
            try:
                test_rag = OpenSearchReminderRAG()
                logger.info("Test RAG initialized")
            except Exception as e:
                logger.warning(f"Test RAG failed: {e}")

        test_agent = OllamaAgent(rag_client=test_rag)
        logger.info("Test agent initialized successfully")
    except Exception as e:
        logger.error(f" Failed to initialize Ollama agent: {e}")
        logger.error("   Make sure Ollama is running: docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama ollama/ollama")
        sys.exit(1)

    server = grpc.server(
        futures.ThreadPoolExecutor(
            max_workers=int(os.getenv('GRPC_WORKERS', '10'))
        ),
        options=[
            ('grpc.max_send_message_length',
             int(os.getenv('GRPC_MAX_MESSAGE_LENGTH', '50')) * 1024 * 1024),
            ('grpc.max_receive_message_length',
             int(os.getenv('GRPC_MAX_MESSAGE_LENGTH', '50')) * 1024 * 1024),
        ]
    )

    service = OllamaReminderParserService()
    pb_grpc.add_ReminderParserServiceServicer_to_server(service, server)

    port = int(os.getenv('GRPC_PORT', '50051'))
    use_tls = os.getenv('GRPC_USE_TLS', 'false').lower() == 'true'

    if use_tls:
        certs_dir = Path('/app/certs')
        if certs_dir.exists():
            try:
                with open(certs_dir / 'node-key.pem', 'rb') as f:
                    private_key = f.read()
                with open(certs_dir / 'node.pem', 'rb') as f:
                    certificate_chain = f.read()

                ca_cert = None
                ca_path = certs_dir / 'ca-chain.pem'
                if ca_path.exists():
                    with open(ca_path, 'rb') as f:
                        ca_cert = f.read()

                server_credentials = grpc.ssl_server_credentials(
                    [(private_key, certificate_chain)],
                    root_certificates=ca_cert,
                    require_client_auth=False
                )
                server.add_secure_port(f'[::]:{port}', server_credentials)
                logger.info(f"TLS enabled for gRPC server on port {port}")
            except Exception as e:
                logger.error(f" Failed to load TLS certificates: {e}")
                logger.warning(f"Falling back to insecure connection")
                server.add_insecure_port(f'[::]:{port}')
        else:
            logger.warning(f"TLS requested but certificates directory not found at {certs_dir}")
            logger.warning(f"Falling back to insecure connection")
            server.add_insecure_port(f'[::]:{port}')
    else:
        server.add_insecure_port(f'[::]:{port}')
        logger.info(f"TLS disabled, using insecure connection on port {port}")

    server.start()

    logger.info(f"Ollama NLP gRPC Server started on port {port}")
    logger.info(f"   Authentication: {'enabled' if service.api_key else 'disabled'}")
    logger.info(f"   TLS: {'enabled' if use_tls else 'disabled'}")
    logger.info(f"   RAG: {'enabled' if service.use_rag else 'disabled'}")

    server.wait_for_termination()

if __name__ == '__main__':
    serve()