#!/usr/bin/env python3
import grpc
import sys
import os

sys.path.append(os.path.join(os.path.dirname(__file__), 'nlp_service'))

from nlp_service import reminder_parser_pb2 as pb
from nlp_service import reminder_parser_pb2_grpc as pb_grpc

def test_parse():
    channel = grpc.insecure_channel('localhost:50051')
    stub = pb_grpc.ReminderParserServiceStub(channel)

    test_cases = [
        ("Напомни купить молоко завтра в 15:00", "ru-RU"),
        ("Remind me to call John tomorrow at 3 PM", "en-US"),
        ("Напомни отправить отчет через 2 часа", "ru-RU"),
        ("Meeting with team on Monday morning", "en-US"),
    ]

    for text, lang in test_cases:
        try:
            request = pb.ParseRequest(
                text=text,
                language_code=lang,
                user_id="test_user"
            )

            response = stub.ParseReminder(request)

            print(f"\nТекст: {text}")
            print(f"Язык: {response.language_detected}")
            print(f"Действие: {response.parsed.action}")
            print(f"Уверенность: {response.confidence:.2f}")

            if response.parsed.HasField('time_expression'):
                if response.parsed.time_expression.HasField('absolute'):
                    print(f"Время: {response.parsed.time_expression.absolute.iso_datetime}")
                elif response.parsed.time_expression.HasField('relative'):
                    print(f"Время: через {response.parsed.time_expression.relative.amount} {response.parsed.time_expression.relative.unit}")

        except grpc.RpcError as e:
            print(f"Ошибка: {e.code()} - {e.details()}")

def test_health():
    channel = grpc.insecure_channel('localhost:50051')
    stub = pb_grpc.ReminderParserServiceStub(channel)

    try:
        response = stub.HealthCheck(pb.HealthRequest())
        print(f"\nHealth Check:")
        print(f"  Healthy: {response.healthy}")
        print(f"  Version: {response.model_version}")
        print(f"  Languages: {', '.join(response.supported_languages)}")
    except grpc.RpcError as e:
        print(f"Health check failed: {e}")

if __name__ == '__main__':
    print("=== Тестирование NLP gRPC сервиса ===")

    try:
        test_health()
        test_parse()
    except Exception as e:
        print(f"Ошибка: {e}")
        print("\nУбедитесь, что сервер запущен")