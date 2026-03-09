import os
import json
import requests
from typing import Dict, List, Optional
from dataclasses import dataclass
from datetime import datetime
import logging
import re

logger = logging.getLogger(__name__)

@dataclass
class ReminderParseResult:
    """Структура результата парсинга напоминания"""
    action: str
    time_type: str  # "absolute", "relative", "recurring", "unspecified"
    datetime: Optional[str] = None  # ISO формат для absolute времени
    relative_seconds: Optional[int] = None  # секунды для relative времени
    cron_expression: Optional[str] = None  # cron для recurring времени
    natural_language_time: str = ""
    confidence: float = 0.0
    intent: str = "reminder"
    language: str = "ru"
    entities: List[Dict] = None
    normalized_text: str = ""

class OllamaAgent:
    """Агент на базе локальной Ollama"""

    # Обновленный системный промпт с акцентом на распознавание времени
    SYSTEM_PROMPT = """Ты помощник для парсинга напоминаний из текста на русском языке.

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
{
  "action": "сходить погулять",
  "time_type": "absolute",
  "datetime": "2026-03-09T22:45:00",
  "relative_seconds": null,
  "cron_expression": null,
  "natural_language_time": "двадцать два сорок пять",
  "confidence": 0.95,
  "intent": "reminder",
  "language": "ru"
}

Вход: "сегодня в двадцать два сорок четыре я хочу сходить погулять"
{
  "action": "сходить погулять",
  "time_type": "absolute",
  "datetime": "2026-03-09T22:44:00",
  "relative_seconds": null,
  "cron_expression": null,
  "natural_language_time": "сегодня в двадцать два сорок четыре",
  "confidence": 0.95,
  "intent": "reminder",
  "language": "ru"
}

Вход: "завтра в 9 утра купить молоко"
{
  "action": "купить молоко",
  "time_type": "absolute",
  "datetime": "2026-03-10T09:00:00",
  "relative_seconds": null,
  "cron_expression": null,
  "natural_language_time": "завтра в 9 утра",
  "confidence": 0.95,
  "intent": "reminder",
  "language": "ru"
}

Вход: "через 2 часа позвонить маме"
{
  "action": "позвонить маме",
  "time_type": "relative",
  "datetime": null,
  "relative_seconds": 7200,
  "cron_expression": null,
  "natural_language_time": "через 2 часа",
  "confidence": 0.95,
  "intent": "reminder",
  "language": "ru"
}

Верни ТОЛЬКО JSON, никаких пояснений.
"""

    def __init__(self):
        # Получаем настройки из окружения
        self.ollama_host = os.getenv('OLLAMA_HOST', 'localhost')
        self.ollama_port = os.getenv('OLLAMA_PORT', '11434')
        self.model = os.getenv('OLLAMA_MODEL', 'llama3.2:latest')
        self.temperature = float(os.getenv('OLLAMA_TEMPERATURE', '0.1'))
        # Увеличиваем таймаут до 60 секунд
        self.timeout = int(os.getenv('OLLAMA_TIMEOUT', '60'))

        # Формируем URL для API
        self.base_url = f"http://{self.ollama_host}:{self.ollama_port}"
        self.chat_url = f"{self.base_url}/api/chat"

        logger.info(f" Ollama Agent initialized with model: {self.model}")
        logger.info(f"   API URL: {self.base_url}")
        logger.info(f"   Timeout: {self.timeout}s")

        # Проверяем доступность Ollama при инициализации
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
                    logger.warning(f"️ Model {self.model} not found. Available: {model_names}")
                    logger.warning(f"   You can pull it with: docker exec ollama ollama pull {self.model}")
            else:
                logger.warning(f"️ Ollama returned status {response.status_code}")
        except requests.exceptions.ConnectionError:
            logger.error(f" Cannot connect to Ollama at {self.base_url}")
            logger.error("   Make sure Ollama container is running: docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama ollama/ollama")
        except Exception as e:
            logger.error(f" Error checking Ollama connection: {e}")

    def _call_ollama(self, prompt: str) -> str:
        """Вызывает Ollama API с системным промптом"""
        request_data = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": self.SYSTEM_PROMPT},
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
            logger.error(f"Ollama request timeout after {self.timeout}s")
            raise
        except requests.exceptions.ConnectionError:
            logger.error(f"Connection error to Ollama at {self.base_url}")
            raise
        except Exception as e:
            logger.error(f" Ollama API error: {e}")
            raise

    def parse_reminder(self, text: str, language: str = "ru") -> ReminderParseResult:
        try:
            # Добавляем сегодняшнюю дату в контекст
            today = datetime.now().strftime('%Y-%m-%d')
            prompt = f"Сегодня: {today}\nТекст напоминания: {text}\nЯзык: {language}"
            logger.info(f"Sending to LLM: {text}")

            response = self._call_ollama(prompt)

            # Логируем сырой ответ
            logger.info(f"Raw LLM response: {response}")

            # Очищаем ответ от markdown
            response = response.strip()
            response = response.replace('```json', '').replace('```', '').strip()

            logger.info(f"Cleaned response: {response}")

            # Находим JSON
            json_match = re.search(r'\{.*\}', response, re.DOTALL)
            if json_match:
                json_str = json_match.group()
                logger.info(f"🔍 Found JSON: {json_str}")
                data = json.loads(json_str)
            else:
                logger.warning(f"No JSON found in response, trying full parse")
                data = json.loads(response)

            logger.info(f"Parsed data: {data}")

            # Создаем результат напрямую из данных, полученных от LLM
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

            logger.info(f"Final result: action='{result.action}', time_type={result.time_type}, conf={result.confidence}")
            if result.relative_seconds:
                logger.info(f"   Relative seconds: {result.relative_seconds}")
            elif result.datetime:
                logger.info(f"   Datetime: {result.datetime}")
            elif result.cron_expression:
                logger.info(f"   Cron: {result.cron_expression}")

            return result

        except Exception as e:
            logger.error(f"Error parsing reminder: {e}", exc_info=True)
            # Возвращаем fallback результат
            return ReminderParseResult(
                action=text[:50],
                time_type='unspecified',
                confidence=0.3,
                language=language,
                normalized_text=text
            )

    def _ensure_model(self):
        """Проверяет наличие модели и скачивает если нужно"""
        try:
            # Проверяем список моделей
            response = requests.get(f"{self.base_url}/api/tags", timeout=5)
            models = response.json().get('models', [])
            model_names = [m['name'] for m in models]

            if self.model in model_names:
                logger.info(f"Model {self.model} is available")
                return True

            # Модели нет - скачиваем
            logger.info(f"Model {self.model} not found. Starting download...")
            logger.info(f"   This may take a few minutes depending on your internet connection")

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
                                    logger.info(f"📥 Downloading {self.model}: {percent:.1f}% - {data['status']}")
                                else:
                                    logger.info(f"📥 {data['status']}")
                        except:
                            pass

                logger.info(f"Successfully pulled model {self.model}")
                return True
            else:
                logger.error(f"Failed to pull model: {pull_response.status_code}")
                return False

        except Exception as e:
            logger.error(f"Error ensuring model: {e}")
            return False

    async def parse_reminder_async(self, text: str, language: str = "ru") -> ReminderParseResult:
        """Асинхронный парсинг"""
        return self.parse_reminder(text, language)