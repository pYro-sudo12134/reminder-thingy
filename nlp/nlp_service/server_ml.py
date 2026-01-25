import grpc
from concurrent import futures
import logging
import os
import sys
from datetime import datetime
from pathlib import Path

try:
    from . import reminder_parser_pb2 as pb
    from . import reminder_parser_pb2_grpc as pb_grpc
    from .ml_integration import MLEnhancedReminderParser
except ImportError as e:
    try:
        import reminder_parser_pb2 as pb
        import reminder_parser_pb2_grpc as pb_grpc
        from ml_integration import MLEnhancedReminderParser
    except ImportError:
        logging.error(f"Import error: {e}")
        sys.exit(1)

class MLReminderParserService(pb_grpc.ReminderParserServiceServicer):

    def __init__(self, model_dir: str = None):
        self.request_count = 0
        self.error_count = 0
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

    def ParseReminder(self, request: pb.ParseRequest, context):
        self.request_count += 1

        try:
            print(f"New request")
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
            supported_languages=['ru', 'en']
        )

    def _to_protobuf(self, parsed, user_id: str) -> pb.ParseResponse:
        print(f"Converting to protobuf")
        print(f"Action: {parsed.action}")
        print(f"Intent: {parsed.intent}")
        print(f"Confidence: {parsed.confidence}")
        print(f"Time expression: {parsed.time_expression.type}, {parsed.time_expression.natural_language}")

        if parsed.time_expression.type.name == "ABSOLUTE" and parsed.time_expression.datetime:
            print(f"Datetime: {parsed.time_expression.datetime}")
        elif parsed.time_expression.type.name == "RELATIVE" and parsed.time_expression.relative_seconds:
            print(f"Relative seconds: {parsed.time_expression.relative_seconds}")

        response = pb.ParseResponse(
            reminder_id=f"{user_id}_{int(datetime.now().timestamp())}",
            parsed=pb.ParsedReminder(
                action=parsed.action,
                normalized_text=parsed.normalized_text,
                intent=parsed.intent
            ),
            confidence=parsed.confidence,
            language_detected=parsed.language,
            raw_text=parsed.raw_text
        )

        time_expr = parsed.time_expression
        temporal_expr = pb.TemporalExpression()

        if time_expr.type.name == "ABSOLUTE" and time_expr.datetime:
            temporal_expr.absolute.iso_datetime = time_expr.datetime.isoformat()
            temporal_expr.absolute.natural_language = time_expr.natural_language
            print(f"Set absolute time: {time_expr.datetime.isoformat()}")

        elif time_expr.type.name == "RELATIVE" and time_expr.relative_seconds:
            temporal_expr.relative.seconds_from_now = time_expr.relative_seconds
            temporal_expr.relative.unit = "seconds"
            temporal_expr.relative.amount = time_expr.relative_seconds
            print(f"Set relative time: {time_expr.relative_seconds} seconds")

        elif time_expr.type.name == "RECURRING" and time_expr.cron_expression:
            temporal_expr.recurring.cron_expression = time_expr.cron_expression
            temporal_expr.recurring.natural_language = time_expr.natural_language

        response.parsed.time_expression.CopyFrom(temporal_expr)

        print(f"Conversion completed")
        return response

def serve():
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))

    service = MLReminderParserService()
    pb_grpc.add_ReminderParserServiceServicer_to_server(service, server)

    port = 50051
    server.add_insecure_port(f'[::]:{port}')
    server.start()

    logging.info(f"ML Enhanced NLP gRPC Server started on port {port}")
    logging.info(f"ML models enabled: {service.ml_enabled}")

    server.wait_for_termination()

if __name__ == '__main__':
    serve()