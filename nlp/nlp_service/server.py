import grpc
from concurrent import futures
import logging
from datetime import datetime

from . import reminder_parser_pb2 as pb
from . import reminder_parser_pb2_grpc as pb_grpc
from .model import ReminderNLPModel

class ReminderParserService(pb_grpc.ReminderParserServiceServicer):

    def __init__(self):
        self.model = ReminderNLPModel()
        self.start_time = datetime.now()

    def ParseReminder(self, request, context):
        try:
            parsed = self.model.parse(
                text=request.text,
                language=request.language_code or None
            )

            # Конвертируем в protobuf
            response = pb.ParseResponse(
                reminder_id=f"{request.user_id}_{int(datetime.now().timestamp())}",
                parsed=pb.ParsedReminder(
                    action=parsed.action,
                    time_expression=self._time_to_proto(parsed.time_expression),
                    normalized_text=parsed.normalized_text,
                    intent=parsed.intent
                ),
                confidence=parsed.confidence,
                language_detected=parsed.language,
                raw_text=parsed.raw_text
            )

            return response

        except Exception as e:
            logging.error(f"Error: {e}")
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            return pb.ParseResponse()

    def HealthCheck(self, request, context):
        return pb.HealthResponse(
            healthy=True,
            model_version="1.0.0",
            supported_languages=list(self.model.models.keys())
        )

    def _time_to_proto(self, time_expr):
        from .model import ReminderType

        if time_expr.type == ReminderType.ABSOLUTE and time_expr.datetime:
            return pb.TemporalExpression(
                absolute=pb.AbsoluteTime(
                    iso_datetime=time_expr.datetime.isoformat(),
                    natural_language=time_expr.natural_language
                )
            )
        else:
            # Fallback
            return pb.TemporalExpression(
                relative=pb.RelativeTime(
                    seconds_from_now=3600,
                    unit="hours",
                    amount=1
                )
            )

def serve():
    logging.basicConfig(level=logging.INFO)

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    pb_grpc.add_ReminderParserServiceServicer_to_server(
        ReminderParserService(), server
    )
    server.add_insecure_port('[::]:50051')
    server.start()
    print("gRPC сервер запущен на порту 50051")
    server.wait_for_termination()

if __name__ == '__main__':
    serve()