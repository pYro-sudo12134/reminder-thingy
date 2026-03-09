# nlp/llm_agent/test_ollama_client.py
#!/usr/bin/env python3
import grpc
import sys
import os

sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'nlp_service'))

from nlp_service import reminder_parser_pb2 as pb
from nlp_service import reminder_parser_pb2_grpc as pb_grpc

def test_ollama_parser():
    channel = grpc.insecure_channel('localhost:50051')
    stub = pb_grpc.ReminderParserServiceStub(channel)

    # Тестовые примеры
    test_cases = [
        # Русские
        ("Напомни купить молоко завтра в 15:00", "ru-RU"),
        ("Нужно позвонить маме через 2 часа", "ru-RU"),
        ("Встреча с командой каждый понедельник в 10 утра", "ru-RU"),
        ("Дедлайн по проекту в пятницу", "ru-RU"),
        ("Напомни про день рождения 15 марта", "ru-RU"),

        # Английские
        ("Remind me to call John tomorrow at 3 PM", "en-US"),
        ("Meeting with team next Monday at 10 AM", "en-US"),
        ("Submit report in 3 hours", "en-US"),
    ]

    for text, lang in test_cases:
        try:
            request = pb.ParseRequest(
                text=text,
                language_code=lang,
                user_id="test_user"
            )

            response = stub.ParseReminder(request)

            print(f"\n{'='*60}")
            print(f"📝 Текст: {text}")
            print(f"🌐 Язык: {response.language_detected}")
            print(f"🎯 Действие: {response.parsed.action}")
            print(f"💡 Intent: {response.parsed.intent}")
            print(f"📊 Уверенность: {response.confidence:.2%}")

            # Выводим время
            if response.parsed.HasField('time_expression'):
                te = response.parsed.time_expression
                if te.HasField('absolute'):
                    print(f"⏰ Абсолютное время: {te.absolute.iso_datetime}")
                elif te.HasField('relative'):
                    print(f"⏰ Относительное время: через {te.relative.amount} {te.relative.unit}")
                elif te.HasField('recurring'):
                    print(f"⏰ Повторяющееся: {te.recurring.cron_expression}")

        except grpc.RpcError as e:
            print(f"❌ Ошибка: {e.code()} - {e.details()}")

if __name__ == '__main__':
    print("🚀 Тестирование Ollama агента для парсинга напоминаний")
    print("="*60)
    test_ollama_parser()