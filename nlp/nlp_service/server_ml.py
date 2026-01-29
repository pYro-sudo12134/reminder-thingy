import grpc
from concurrent import futures
import logging
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import Optional

try:
    from . import reminder_parser_pb2 as pb
    from . import reminder_parser_pb2_grpc as pb_grpc
    from .ml_integration import MLEnhancedReminderParser
    from .secret_loader import SecretLoader
except ImportError as e:
    try:
        import reminder_parser_pb2 as pb
        import reminder_parser_pb2_grpc as pb_grpc
        from ml_integration import MLEnhancedReminderParser
        from secret_loader import SecretLoader
    except ImportError:
        logging.error(f"Import error: {e}")
        sys.exit(1)

class MLReminderParserService(pb_grpc.ReminderParserServiceServicer):

    def __init__(self, model_dir: str = None, api_key: Optional[str] = None):
        self.request_count = 0
        self.error_count = 0

        self.secrets = SecretLoader.load_secrets()

        self.api_key = api_key
        if not self.api_key:
            self.api_key = self.secrets.get("NLP_GRPC_API_KEY") or self.secrets.get("GRPC_API_KEY")

        if not self.api_key:
            self.api_key = os.getenv('GRPC_API_KEY') or os.getenv('NLP_GRPC_API_KEY')

        base_dir = Path(__file__).parent.parent

        self.parser = MLEnhancedReminderParser(str(base_dir / 'models') if model_dir is None else model_dir)

        intent_model_path = base_dir / 'models' / 'intent_classifier'
        ner_model_path = base_dir / 'models' / 'ner_model'

        print(f"Intent model path: {intent_model_path}")
        print(f"NER model path: {ner_model_path}")
        print(f"Intent model exists: {intent_model_path.exists()}")
        print(f"NER model exists: {ner_model_path.exists()}")

        if intent_model_path.exists():
            try:
                ner_path = str(ner_model_path) if ner_model_path.exists() else None
                self.parser.load_ml_models(
                    str(intent_model_path),
                    ner_path
                )
                self.ml_enabled = True
                logging.info("ML models loaded successfully")
            except Exception as e:
                self.ml_enabled = False
                logging.warning(f"Error loading ML models: {e}, using rule-based parsing")
        else:
            self.ml_enabled = False
            logging.warning("ML models not found, using rule-based parsing")

        logging.info(f"Authentication enabled: {bool(self.api_key)}")
        logging.info(f"Total secrets loaded: {len(self.secrets)}")

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
            print(f"New authenticated request from user: {request.user_id}")
            print(f"Text: '{request.text}'")
            print(f"Language: {request.language_code}")

            language = None
            if request.language_code:
                language_code = request.language_code.lower()
                if '-' in language_code:
                    language = language_code.split('-')[0]
                elif len(language_code) >= 2:
                    language = language_code[:2]
                else:
                    language = language_code
                print(f"Transformed language: {language}")

            parsed = self.parser.parse(
                text=request.text,
                language=language
            )

            print(f"Parsing result:")
            print(f"Action: {parsed.action}")
            print(f"Intent: {parsed.intent}")
            print(f"Confidence: {parsed.confidence}")
            print(f"Time: {parsed.time_expression.natural_language}")

            response = self._to_protobuf(parsed, request.user_id)

            return response

        except Exception as e:
            self.error_count += 1
            logging.error(f"Error parsing reminder: {e}", exc_info=True)
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            return pb.ParseResponse()

    def HealthCheck(self, request, context):
        return pb.HealthResponse(
            healthy=True,
            model_version="2.0.0-ml",
            supported_languages=['ru', 'en'],
            auth_required=bool(self.api_key),
            total_requests=self.request_count,
            error_count=self.error_count
        )

    def _to_protobuf(self, parsed, user_id: str) -> pb.ParseResponse:
        print(f"Converting to protobuf")
        print(f"Action: {parsed.action}")
        print(f"Intent: {parsed.intent}")
        print(f"Confidence: {parsed.confidence}")
        print(f"Time expression: {parsed.time_expression.type}, {parsed.time_expression.natural_language}")

        entities_list = []
        if hasattr(parsed, 'entities') and parsed.entities:
            for entity in parsed.entities:
                entity_proto = pb.Entity(
                    text=entity.get('text', ''),
                    type=entity.get('label', ''),
                    start=entity.get('start', 0),
                    end=entity.get('end', 0),
                    confidence=entity.get('confidence', 0.0)
                )
                entities_list.append(entity_proto)

        print(f"Found {len(entities_list)} entities")

        parsed_reminder = pb.ParsedReminder(
            action=parsed.action,
            normalized_text=parsed.normalized_text,
            intent=parsed.intent
        )

        parsed_reminder.entities.extend(entities_list)

        temporal_expr = pb.TemporalExpression()

        if parsed.time_expression.type.name == "ABSOLUTE" and parsed.time_expression.datetime:
            temporal_expr.absolute.iso_datetime = parsed.time_expression.datetime.isoformat()
            temporal_expr.absolute.natural_language = parsed.time_expression.natural_language
            print(f"Set absolute time: {parsed.time_expression.datetime.isoformat()}")

        elif parsed.time_expression.type.name == "RELATIVE" and parsed.time_expression.relative_seconds:
            temporal_expr.relative.seconds_from_now = parsed.time_expression.relative_seconds
            temporal_expr.relative.unit = "seconds"
            temporal_expr.relative.amount = float(parsed.time_expression.relative_seconds)
            print(f"Set relative time: {parsed.time_expression.relative_seconds} seconds")

        elif parsed.time_expression.type.name == "RECURRING" and parsed.time_expression.cron_expression:
            temporal_expr.recurring.cron_expression = parsed.time_expression.cron_expression
            temporal_expr.recurring.natural_language = parsed.time_expression.natural_language

        parsed_reminder.time_expression.CopyFrom(temporal_expr)

        response = pb.ParseResponse(
            reminder_id=f"{user_id}_{int(datetime.now().timestamp())}",
            parsed=parsed_reminder,
            confidence=parsed.confidence,
            language_detected=parsed.language,
            raw_text=parsed.raw_text
        )

        print(f"Conversion completed")
        return response

def serve():
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )

    secrets = SecretLoader.load_secrets()

    api_key = secrets.get("NLP_GRPC_API_KEY") or secrets.get("GRPC_API_KEY")

    if not api_key:
        api_key = os.getenv('GRPC_API_KEY')

    if not api_key:
        logging.warning("No API key found. Server will run without authentication")
        api_key = None

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))

    service = MLReminderParserService(api_key=api_key)
    pb_grpc.add_ReminderParserServiceServicer_to_server(service, server)

    port = 50051
    server.add_insecure_port(f'[::]:{port}')
    server.start()

    logging.info(f"ML Enhanced NLP gRPC Server started on port {port}")
    logging.info(f"ML models enabled: {service.ml_enabled}")
    logging.info(f"Authentication enabled: {bool(api_key)}")
    logging.info(f"Secrets loaded: {len(secrets)}")

    server.wait_for_termination()

if __name__ == '__main__':
    serve()