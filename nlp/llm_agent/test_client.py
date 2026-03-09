#!/usr/bin/env python3
import grpc
import sys
import os

sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'nlp_service'))

from nlp_service import reminder_parser_pb2 as pb
from nlp_service import reminder_parser_pb2_grpc as pb_grpc

def test_llm_parser():
    channel = grpc.insecure_channel('localhost:50051')
    stub = pb_grpc.ReminderParserServiceStub(channel)

    # Тестовые примеры, с которыми старая NLP не справлялась
    test_cases = [
        # Сложные временные выражения на русском
        ("Напомни купить молоко послезавтра утром в 9:30", "ru-RU"),
        ("Нужно позвонить маме в следующий вторник после обеда", "ru-RU"),
        ("Встреча с командой через 3 дня в 15:00", "ru-RU"),
        ("Дедлайн по проекту в последний день месяца", "ru-RU"),
        ("Напомни про день рождения Маши 15 марта в 19:00", "ru-RU"),

        # Сложные на английском
        ("Remind me to call John the day after tomorrow at 3:30 PM", "en-US"),
        ("Meeting with team next Monday at 10 AM", "en-US"),
        ("Submit report by the end of this week", "en-US"),
        ("Doctor appointment on March 15th at 2 PM", "en-US"),

        # Разговорные выражения
        ("Напомни про zoom через полчаса", "ru-RU"),
        ("Купить продукты после работы", "ru-RU"),
        ("Call mom later this evening", "en-US"),
        ("Team sync in a couple of hours", "en-US"),
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
                    print(f"   Описание: {te.absolute.natural_language}")
                elif te.HasField('relative'):
                    print(f"⏰ Относительное время: через {te.relative.amount} {te.relative.unit}")
                    print(f"   Секунд: {te.relative.seconds_from_now}")
                elif te.HasField('recurring'):
                    print(f"⏰ Повторяющееся: {te.recurring.cron_expression}")

            # Выводим сущности
            if response.parsed.entities:
                print(f"🔍 Сущности:")
                for entity in response.parsed.entities:
                    print(f"   - {entity.text} ({entity.type}) conf:{entity.confidence:.2f}")

        except grpc.RpcError as e:
            print(f"Ошибка: {e.code()} - {e.details()}")

if __name__ == '__main__':
    print("Тестирование LLM-агента для парсинга напоминаний")
    print("="*60)
    test_llm_parser()