import json
from typing import List, Dict
import random
from datetime import datetime, timedelta
from dataclasses import dataclass
from enum import Enum

class IntentType(Enum):
    REMINDER = "reminder"
    TASK = "task"
    MEETING = "meeting"
    BIRTHDAY = "birthday"
    DEADLINE = "deadline"
    APPOINTMENT = "appointment"

@dataclass
class TrainingExample:
    text: str
    language: str
    intent: IntentType
    entities: List[Dict]
    time_expression: str
    action: str
    metadata: Dict

class DatasetGenerator:
    """Генератор синтетического датасета для обучения"""

    def __init__(self):
        self.ru_templates = self._load_russian_templates()
        self.en_templates = self._load_english_templates()

    def _load_russian_templates(self) -> Dict:
        return {
            IntentType.REMINDER: [
                "Напомни мне {action} {time}",
                "Не забудь {action} {time}",
                "Сделай напоминание {action} {time}",
                "Напомни {action} {time}",
                "Скажи мне {action} {time}",
            ],
            IntentType.TASK: [
                "Нужно {action} {time}",
                "Требуется {action} {time}",
                "Необходимо {action} {time}",
                "Выполни {action} {time}",
                "Сделай {action} {time}",
            ],
            IntentType.MEETING: [
                "Встреча с {person} {time}",
                "Совещание по {topic} {time}",
                "Звонок с {person} {time}",
                "Встреча {time}",
                "Собрание {time}",
            ],
            IntentType.BIRTHDAY: [
                "День рождения {person} {time}",
                "{person} празднует день рождения {time}",
                "ДР {person} {time}",
                "Поздравить {person} с днем рождения {time}",
            ],
            IntentType.DEADLINE: [
                "Дедлайн {action} {time}",
                "Срок сдачи {action} {time}",
                "Нужно завершить {action} {time}",
                "Крайний срок {action} {time}",
                "До {time} нужно {action}",
            ],
            IntentType.APPOINTMENT: [
                "Запись к врачу {time}",
                "Прием у {person} {time}",
                "Визит к {person} {time}",
                "Аппоинтмент {time}",
                "Встреча с доктором {time}",
            ]
        }

    def _load_english_templates(self) -> Dict:
        return {
            IntentType.REMINDER: [
                "Remind me to {action} {time}",
                "Don't forget to {action} {time}",
                "Set a reminder for {action} {time}",
                "Tell me to {action} {time}",
                "Alert me to {action} {time}",
            ],
            IntentType.TASK: [
                "I need to {action} {time}",
                "I have to {action} {time}",
                "Do {action} {time}",
                "Complete {action} {time}",
                "Finish {action} {time}",
            ],
            IntentType.MEETING: [
                "Meeting with {person} {time}",
                "Call {person} {time}",
                "Appointment {time}",
                "Conference call {time}",
                "Meet {person} {time}",
            ],
            IntentType.BIRTHDAY: [
                "{person}'s birthday {time}",
                "Birthday party for {person} {time}",
                "Celebrate {person}'s birthday {time}",
                "Wish {person} happy birthday {time}",
            ],
            IntentType.DEADLINE: [
                "Deadline for {action} {time}",
                "Submit {action} by {time}",
                "Need to finish {action} by {time}",
                "Due date for {action} is {time}",
                "{action} deadline is {time}",
            ],
            IntentType.APPOINTMENT: [
                "Doctor's appointment {time}",
                "Appointment with {person} {time}",
                "Visit to {person} {time}",
                "See {person} {time}",
                "Medical appointment {time}",
            ]
        }

    def generate_dataset(self, size: int = 10000) -> List[TrainingExample]:
        """Генерирует датасет заданного размера"""
        examples = []

        # Доступные намерения (только те, для которых есть шаблоны)
        available_intents = [
            IntentType.REMINDER,
            IntentType.TASK,
            IntentType.MEETING,
            IntentType.BIRTHDAY,
            IntentType.DEADLINE,
            IntentType.APPOINTMENT
        ]

        for _ in range(size):
            # Выбираем случайный язык и намерение
            lang = random.choice(['ru', 'en'])
            intent = random.choice(available_intents)

            # Генерируем пример
            example = self._generate_example(lang, intent)
            examples.append(example)

        return examples

    def _generate_example(self, lang: str, intent: IntentType) -> TrainingExample:
        """Генерирует один пример"""
        templates = self.ru_templates if lang == 'ru' else self.en_templates

        # Проверяем, есть ли шаблоны для этого намерения
        if intent not in templates:
            # Если нет, используем REMINDER как fallback
            intent = IntentType.REMINDER

        template = random.choice(templates[intent])

        # Генерируем заполнители
        placeholders = self._generate_placeholders(lang, intent)

        # Заполняем шаблон
        text = template.format(**placeholders)

        # Создаем разметку сущностей
        entities = self._extract_entities(text, lang)

        return TrainingExample(
            text=text,
            language=lang,
            intent=intent,
            entities=entities,
            time_expression=placeholders['time'],
            action=placeholders.get('action', ''),
            metadata=placeholders
        )

    def _generate_placeholders(self, lang: str, intent: IntentType) -> Dict:
        """Генерирует значения для заполнителей"""
        placeholders = {}

        # Генерируем время
        placeholders['time'] = self._generate_time_expression(lang)

        # Генерируем действие для соответствующих намерений
        if intent in [IntentType.REMINDER, IntentType.TASK, IntentType.DEADLINE]:
            placeholders['action'] = self._generate_action(lang)

        # Генерируем персону для встреч, дней рождения и приемов
        if intent in [IntentType.MEETING, IntentType.BIRTHDAY, IntentType.APPOINTMENT]:
            placeholders['person'] = self._generate_person(lang)

        # Генерируем тему для встреч
        if intent == IntentType.MEETING:
            placeholders['topic'] = self._generate_topic(lang)

        return placeholders

    def _generate_time_expression(self, lang: str) -> str:
        """Генерирует временное выражение"""
        time_types = ['absolute', 'relative', 'recurring']
        time_type = random.choice(time_types)

        if lang == 'ru':
            if time_type == 'absolute':
                options = [
                    "завтра в 15:00",
                    "сегодня вечером",
                    "в понедельник утром",
                    "31 декабря",
                    "в следующем месяце",
                    "послезавтра в 10:30",
                    "в пятницу",
                    "в 18:00"
                ]
            elif time_type == 'relative':
                options = [
                    "через 2 часа",
                    "через 30 минут",
                    "через 3 дня",
                    "через неделю",
                    "через пару часов",
                    "через 15 минут",
                    "через 5 дней",
                    "через месяц"
                ]
            else:  # recurring
                options = [
                    "каждый день",
                    "по понедельникам",
                    "еженедельно",
                    "раз в месяц",
                    "каждое утро",
                    "каждую неделю",
                    "каждые выходные",
                    "ежедневно"
                ]
        else:  # en
            if time_type == 'absolute':
                options = [
                    "tomorrow at 3 PM",
                    "tonight",
                    "on Monday morning",
                    "on December 31st",
                    "next month",
                    "the day after tomorrow at 10:30 AM",
                    "on Friday",
                    "at 6 PM"
                ]
            elif time_type == 'relative':
                options = [
                    "in 2 hours",
                    "in 30 minutes",
                    "in 3 days",
                    "in a week",
                    "in a couple of hours",
                    "in 15 minutes",
                    "in 5 days",
                    "in a month"
                ]
            else:  # recurring
                options = [
                    "every day",
                    "on Mondays",
                    "weekly",
                    "monthly",
                    "every morning",
                    "every week",
                    "every weekend",
                    "daily"
                ]

        return random.choice(options)

    def _generate_action(self, lang: str) -> str:
        """Генерирует действие"""
        if lang == 'ru':
            actions = [
                "позвонить маме",
                "купить молоко",
                "отправить отчет",
                "забрать документы",
                "записаться к врачу",
                "оплатить счета",
                "починить кран",
                "заказать пиццу",
                "прочитать книгу",
                "посмотреть фильм",
                "написать письмо",
                "сделать домашнее задание",
                "приготовить ужин",
                "сходить в магазин",
                "записать видео"
            ]
        else:
            actions = [
                "call mom",
                "buy milk",
                "send the report",
                "pick up documents",
                "make a doctor appointment",
                "pay the bills",
                "fix the faucet",
                "order pizza",
                "read a book",
                "watch a movie",
                "write an email",
                "do homework",
                "cook dinner",
                "go to the store",
                "record a video"
            ]

        return random.choice(actions)

    def _generate_person(self, lang: str) -> str:
        """Генерирует имя персоны"""
        if lang == 'ru':
            persons = [
                "Ивану",
                "Марии",
                "Алексею",
                "Ольге",
                "Сергею",
                "Анне",
                "Дмитрию",
                "Елене",
                "коллегам",
                "директору",
                "врачу",
                "другу",
                "секретарю",
                "клиенту",
                "партнеру"
            ]
        else:
            persons = [
                "John",
                "Mary",
                "Alex",
                "Sarah",
                "Michael",
                "Emily",
                "David",
                "Lisa",
                "colleagues",
                "manager",
                "doctor",
                "friend",
                "secretary",
                "client",
                "partner"
            ]

        return random.choice(persons)

    def _generate_topic(self, lang: str) -> str:
        """Генерирует тему встречи"""
        if lang == 'ru':
            topics = [
                "проекту",
                "бюджету",
                "стратегии",
                "продажам",
                "маркетингу",
                "разработке",
                "дизайну",
                "проблеме",
                "планам",
                "результатам",
                "новому продукту",
                "отчетности",
                "персоналу",
                "контракту",
                "инвестициям"
            ]
        else:
            topics = [
                "the project",
                "budget",
                "strategy",
                "sales",
                "marketing",
                "development",
                "design",
                "the issue",
                "plans",
                "results",
                "new product",
                "reporting",
                "staff",
                "contract",
                "investments"
            ]

        return random.choice(topics)

    def _extract_entities(self, text: str, lang: str) -> List[Dict]:
        """Извлекает сущности из текста (упрощенная версия)"""
        entities = []

        # Временные сущности
        time_keywords = {
            'ru': ['завтра', 'сегодня', 'утром', 'вечером', 'через', 'каждый',
                   'понедельник', 'вторник', 'среду', 'четверг', 'пятницу',
                   'субботу', 'воскресенье', 'в', 'на', 'к', 'часов', 'минут'],
            'en': ['tomorrow', 'today', 'morning', 'evening', 'in', 'every',
                   'monday', 'tuesday', 'wednesday', 'thursday', 'friday',
                   'saturday', 'sunday', 'at', 'on', 'o\'clock', 'am', 'pm']
        }

        for keyword in time_keywords.get(lang, []):
            if keyword in text.lower():
                start = text.lower().find(keyword)
                entities.append({
                    'text': text[start:start+len(keyword)],
                    'label': 'TIME',
                    'start': start,
                    'end': start + len(keyword)
                })

        # Действия (для некоторых намерений)
        action_keywords = {
            'ru': ['позвонить', 'купить', 'отправить', 'забрать', 'записаться',
                   'оплатить', 'починить', 'заказать', 'прочитать', 'посмотреть'],
            'en': ['call', 'buy', 'send', 'pick', 'make', 'pay', 'fix',
                   'order', 'read', 'watch']
        }

        for keyword in action_keywords.get(lang, []):
            if keyword in text.lower():
                start = text.lower().find(keyword)
                entities.append({
                    'text': text[start:start+len(keyword)],
                    'label': 'ACTION',
                    'start': start,
                    'end': start + len(keyword)
                })

        return entities

def save_dataset(examples: List[TrainingExample], path: str):
    """Сохраняет датасет в файл"""
    data = []

    for example in examples:
        data.append({
            'text': example.text,
            'language': example.language,
            'intent': example.intent.value,
            'entities': example.entities,
            'time_expression': example.time_expression,
            'action': example.action,
            'metadata': example.metadata
        })

    # Создаем директорию, если не существует
    import os
    os.makedirs(os.path.dirname(path), exist_ok=True)

    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def split_dataset(examples: List[TrainingExample], train_ratio: float = 0.8):
    """Разделяет датасет на тренировочную и валидационную части"""
    random.shuffle(examples)
    split_idx = int(len(examples) * train_ratio)
    return examples[:split_idx], examples[split_idx:]

if __name__ == '__main__':
    generator = DatasetGenerator()

    print("Генерация датасета...")
    all_examples = generator.generate_dataset(6000)  # Общий датасет

    # Разделяем
    train_examples, val_examples = split_dataset(all_examples, 0.8)

    # Сохраняем
    save_dataset(train_examples, 'data/train_dataset.json')
    save_dataset(val_examples, 'data/val_dataset.json')

    # Статистика
    print(f"\nСтатистика:")
    print(f"Всего примеров: {len(all_examples)}")
    print(f"Тренировочных: {len(train_examples)}")
    print(f"Валидационных: {len(val_examples)}")

    # Распределение по языкам
    ru_train = sum(1 for e in train_examples if e.language == 'ru')
    en_train = sum(1 for e in train_examples if e.language == 'en')

    ru_val = sum(1 for e in val_examples if e.language == 'ru')
    en_val = sum(1 for e in val_examples if e.language == 'en')

    print(f"\nТренировочный набор:")
    print(f"  Русских: {ru_train} ({ru_train/len(train_examples)*100:.1f}%)")
    print(f"  Английских: {en_train} ({en_train/len(train_examples)*100:.1f}%)")

    print(f"\nВалидационный набор:")
    print(f"  Русских: {ru_val} ({ru_val/len(val_examples)*100:.1f}%)")
    print(f"  Английских: {en_val} ({en_val/len(val_examples)*100:.1f}%)")

    # Распределение по намерениям
    intent_counts = {}
    for example in all_examples:
        intent = example.intent.value
        intent_counts[intent] = intent_counts.get(intent, 0) + 1

    print(f"\nРаспределение по намерениям:")
    for intent, count in sorted(intent_counts.items()):
        print(f"  {intent}: {count} ({count/len(all_examples)*100:.1f}%)")