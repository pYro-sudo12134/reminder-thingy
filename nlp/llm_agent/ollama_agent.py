import os
import json
import requests
from typing import Dict, List, Optional, Any
from dataclasses import dataclass
from datetime import datetime, timedelta
import logging
import re
import hashlib

logger = logging.getLogger(__name__)

SYSTEM_PROMPT_TEMPLATE = """Ты помощник для парсинга напоминаний из текста на русском языке.

Из текста нужно извлечь структурированные данные и вернуть их в JSON формате.

*** ВАЖНО: Всегда ищи время в тексте! ***
Даже если время указано словами, например "двадцать два сорок пять" - это время 22:45 сегодня.

ПРАВИЛА ОПРЕДЕЛЕНИЯ ВРЕМЕНИ:

1. АБСОЛЮТНОЕ ВРЕМЯ (absolute):
   - Если есть числа или числительные, указывающие на время
   - "в 22:16" -> время 22:16 сегодня
   - "двадцать два сорок пять" -> время 22:45 сегодня
   - "в девять вечера" -> время 21:00 сегодня
   - "завтра в 9 утра" -> время 9:00 завтра
   - "сегодня в двадцать два сорок четыре" -> время 22:44 сегодня

2. ОТНОСИТЕЛЬНОЕ ВРЕМЯ (relative):
   - "через 2 часа", "через 30 минут", "через 5 секунд"
   - "через полчаса", "через час"

3. ПОВТОРЯЮЩЕЕСЯ (recurring):
   - "каждый день", "каждый понедельник", "ежедневно"

4. НЕ УКАЗАНО (unspecified) - ТОЛЬКО если в тексте совсем нет времени

*** КРИТИЧЕСКИ ВАЖНО: ***
- "двадцать два сорок пять" - это absolute время (22:45)
- "двадцать два сорок четыре" - это absolute время (22:44)
- Любые числа, похожие на время, должны быть распознаны как absolute!

ПРИМЕРЫ ПРАВИЛЬНЫХ ОТВЕТОВ:

Вход: "двадцать два сорок пять я хочу сходить погулять"
{{
    "action": "сходить погулять",
    "time_type": "absolute",
    "datetime": "{{today}}T22:45:00",
    "relative_seconds": null,
    "cron_expression": null,
    "natural_language_time": "двадцать два сорок пять",
    "confidence": 0.95,
    "intent": "reminder",
    "language": "ru"
}}

Вход: "сегодня в двадцать два сорок четыре я хочу сходить погулять"
{{
    "action": "сходить погулять",
    "time_type": "absolute",
    "datetime": "{{today}}T22:44:00",
    "relative_seconds": null,
    "cron_expression": null,
    "natural_language_time": "сегодня в двадцать два сорок четыре",
    "confidence": 0.95,
    "intent": "reminder",
    "language": "ru"
}}

Вход: "завтра в 9 утра купить молоко"
{{
    "action": "купить молоко",
    "time_type": "absolute",
    "datetime": "{{tomorrow}}T09:00:00",
    "relative_seconds": null,
    "cron_expression": null,
    "natural_language_time": "завтра в 9 утра",
    "confidence": 0.95,
    "intent": "reminder",
    "language": "ru"
}}

Вход: "через 2 часа позвонить маме"
{{
    "action": "позвонить маме",
    "time_type": "relative",
    "datetime": null,
    "relative_seconds": 7200,
    "cron_expression": null,
    "natural_language_time": "через 2 часа",
    "confidence": 0.95,
    "intent": "reminder",
    "language": "ru"
}}

Даты в примерах не образец для примера, в том числе и confidence.

Верни ТОЛЬКО JSON, никаких пояснений.
"""


def build_system_prompt():
    today = datetime.now().strftime('%Y-%m-%d')
    tomorrow = (datetime.now() + timedelta(days=1)).strftime('%Y-%m-%d')
    return SYSTEM_PROMPT_TEMPLATE.format(today=today, tomorrow=tomorrow)


@dataclass
class ReminderParseResult:
    """Структура результата парсинга напоминания"""
    action: str
    time_type: str  # "absolute", "relative", "recurring", "unspecified"
    datetime: Optional[str] = None
    relative_seconds: Optional[int] = None
    cron_expression: Optional[str] = None
    natural_language_time: str = ""
    confidence: float = 0.0
    intent: str = "reminder"
    language: str = "ru"
    entities: List[Dict] = None
    normalized_text: str = ""


class OllamaAgent:
    """Агент на базе локальной Ollama с RAG поддержкой"""

    def __init__(self, rag_client=None):
        self.ollama_host = os.getenv('OLLAMA_HOST', 'localhost')
        self.ollama_port = os.getenv('OLLAMA_PORT', '11434')
        self.model = os.getenv('OLLAMA_MODEL', 'llama3.2:latest')
        self.temperature = float(os.getenv('OLLAMA_TEMPERATURE', '0.1'))
        self.timeout = int(os.getenv('OLLAMA_TIMEOUT', '60'))

        self.rag = rag_client
        self.use_rag = rag_client is not None
        self.rag_top_k = int(os.getenv('RAG_TOP_K', '5'))
        self.rag_min_score = float(os.getenv('RAG_MIN_SCORE', '0.5'))
        self.rag_save_confidence = float(os.getenv('RAG_SAVE_CONFIDENCE', '0.8'))

        self.base_url = f"http://{self.ollama_host}:{self.ollama_port}"
        self.chat_url = f"{self.base_url}/api/chat"

        logger.info(f" Ollama Agent initialized with model: {self.model}")
        logger.info(f"   API URL: {self.base_url}")
        logger.info(f"   Timeout: {self.timeout}s")
        logger.info(f"   RAG enabled: {self.use_rag}")
        logger.info(f"   RAG top_k: {self.rag_top_k}, min_score: {self.rag_min_score}")

        self._check_connection()
        self._ensure_model()

    def _check_connection(self):
        """Проверяет доступность Ollama"""
        try:
            response = requests.get(f"{self.base_url}/api/tags", timeout=5)
            if response.status_code == 200:
                models = response.json().get('models', [])
                model_names = [m['name'] for m in models]

                if self.model in model_names:
                    logger.info(f" Model {self.model} is available")
                else:
                    logger.warning(f"Model {self.model} not found. Available: {model_names}")
            else:
                logger.warning(f"Ollama returned status {response.status_code}")
        except Exception as e:
            logger.error(f" Error checking Ollama connection: {e}")

    def _get_text_hash(self, text: str) -> str:
        """Получает хеш текста для поиска"""
        return hashlib.md5(text.lower().encode()).hexdigest()[:8]

    def _find_similar_examples(self, text: str, language: str) -> List[Dict]:
        """Ищет похожие примеры в RAG с использованием векторов"""
        if not self.use_rag or not self.rag:
            return []

        try:
            examples = self.rag.search_similar_by_text(
                text=text,
                k=self.rag_top_k,
                language=language,
                min_score=self.rag_min_score
            )

            if examples:
                logger.info(f"Found {len(examples)} similar examples in RAG (vector search)")

            return examples

        except Exception as e:
            logger.warning(f"RAG vector search failed: {e}")
            return []

    def _build_rag_prompt(self, text: str, language: str, examples: List[Dict]) -> str:
        """Строит промпт с примерами из RAG"""

        today = datetime.now().strftime('%Y-%m-%d')

        if not examples:
            return f"""Сегодня: {today}
Текст напоминания: {text}
Язык: {language}"""

        prompt_parts = [f"Сегодня: {today}"]
        prompt_parts.append("\nВот похожие примеры напоминаний (используй их как образец):")

        for i, ex in enumerate(examples[:3]):
            ex_text = ex.get('text', '')
            ex_type = ex.get('format_type', 'unknown')
            ex_time = ex.get('time_expression', '')

            prompt_parts.append(f"\nПример {i + 1}:")
            prompt_parts.append(f'  Текст: "{ex_text}"')
            prompt_parts.append(f'  Тип: {ex_type}')
            if ex_time:
                prompt_parts.append(f'  Время: {ex_time}')

        prompt_parts.append(f"\nТеперь разбери новое напоминание по этому же образцу:")
        prompt_parts.append(f'Текст: "{text}"')
        prompt_parts.append(f"Язык: {language}")

        return "\n".join(prompt_parts)

    def _save_to_rag(self, text: str, result: ReminderParseResult):
        """Сохраняет удачный парсинг в RAG"""
        if not self.use_rag or not self.rag or result.confidence < self.rag_save_confidence:
            return

        try:
            time_expr = ""
            if result.time_type == "absolute" and result.datetime:
                time_expr = result.datetime
            elif result.time_type == "relative" and result.relative_seconds:
                time_expr = f"через {result.relative_seconds} сек"
            elif result.time_type == "recurring" and result.cron_expression:
                time_expr = result.cron_expression

            example = {
                'text': text,
                'format_type': result.time_type,
                'language': result.language,
                'time_expression': time_expr,
                'entities': result.entities or [],
                'count': 1,
                'source': 'ollama_agent'
            }

            success = self.rag.add_format_examples([example])

            if success > 0:
                logger.info(f"Saved to RAG: {text[:50]}...")

        except Exception as e:
            logger.warning(f"Failed to save to RAG: {e}")

    def _call_ollama(self, prompt: str) -> str:
        """Вызывает Ollama API с системным промптом"""
        request_data = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": build_system_prompt()},
                {"role": "user", "content": prompt}
            ],
            "stream": False,
            "options": {
                "temperature": self.temperature,
                "num_predict": 256,
                "top_k": 40,
                "top_p": 0.9
            }
        }

        try:
            logger.debug(f"Calling Ollama with model {self.model}")

            response = requests.post(
                self.chat_url,
                json=request_data,
                timeout=self.timeout
            )

            if response.status_code != 200:
                logger.error(f"Ollama API error: {response.status_code} - {response.text}")
                response.raise_for_status()

            result = response.json()
            return result.get('message', {}).get('content', '')

        except requests.exceptions.Timeout:
            logger.error(f" Ollama request timeout after {self.timeout}s")
            raise
        except Exception as e:
            logger.error(f" Ollama API error: {e}")
            raise

    def parse_reminder(self, text: str, language: str = "ru") -> ReminderParseResult:
        try:
            examples = self._find_similar_examples(text, language)

            prompt = self._build_rag_prompt(text, language, examples)
            logger.info(f"Sending to LLM with {len(examples)} RAG examples")

            response = self._call_ollama(prompt)

            logger.info(f" Raw LLM response: {response}")

            response = response.strip()
            response = response.replace('```json', '').replace('```', '').strip()

            json_match = re.search(r'\{.*\}', response, re.DOTALL)
            if json_match:
                json_str = json_match.group()
                data = json.loads(json_str)
            else:
                data = json.loads(response)

            result = ReminderParseResult(
                action=data.get('action', text[:50]),
                time_type=data.get('time_type', 'unspecified'),
                datetime=data.get('datetime'),
                relative_seconds=data.get('relative_seconds'),
                cron_expression=data.get('cron_expression'),
                natural_language_time=data.get('natural_language_time', ''),
                confidence=float(data.get('confidence', 0.5)),
                intent=data.get('intent', 'reminder'),
                language=data.get('language', language),
                entities=data.get('entities', []),
                normalized_text=text
            )

            logger.info(
                f" Final result: action='{result.action}', time_type={result.time_type}, conf={result.confidence}")

            if result.confidence >= self.rag_save_confidence:
                self._save_to_rag(text, result)

            return result

        except Exception as e:
            logger.error(f" Error parsing reminder: {e}", exc_info=True)
            return ReminderParseResult(
                action=text[:50],
                time_type='unspecified',
                confidence=0.3,
                language=language,
                normalized_text=text
            )

    def _ensure_model(self):
        """Проверяет наличие модели"""
        try:
            response = requests.get(f"{self.base_url}/api/tags", timeout=5)
            models = response.json().get('models', [])
            model_names = [m['name'] for m in models]

            if self.model in model_names:
                logger.info(f" Model {self.model} is available")
                return True

            logger.info(f" Model {self.model} not found. Pulling...")
            pull_response = requests.post(
                f"{self.base_url}/api/pull",
                json={"name": self.model},
                stream=True,
                timeout=300
            )

            if pull_response.status_code == 200:
                for line in pull_response.iter_lines():
                    if line:
                        try:
                            data = json.loads(line)
                            if 'status' in data:
                                if 'completed' in data and 'total' in data:
                                    percent = (data['completed'] / data['total']) * 100
                                    logger.info(f"Downloading: {percent:.1f}%")
                                else:
                                    logger.info(f"{data['status']}")
                        except:
                            pass
                logger.info(f" Successfully pulled model {self.model}")
                return True
            else:
                logger.error(f" Failed to pull model: {pull_response.status_code}")
                return False

        except Exception as e:
            logger.error(f"Error ensuring model: {e}")
            return False

    async def parse_reminder_async(self, text: str, language: str = "ru") -> ReminderParseResult:
        """Асинхронный парсинг"""
        return self.parse_reminder(text, language)
