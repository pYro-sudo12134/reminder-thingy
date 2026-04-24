import logging
import json
import ssl
from urllib.request import Request, urlopen
from urllib.error import URLError
from typing import Dict, Any

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class TelegramAdapter:
    def __init__(self, bot_token: str):
        self.bot_token = bot_token
        self.api_url = f"https://api.telegram.org/bot{bot_token}"
        self.timeout = 10

    def _make_request(self, url: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        try:
            data = json.dumps(payload).encode('utf-8')
            req = Request(url, data=data, headers={
                'Content-Type': 'application/json',
                'Content-Length': len(data)
            })

            ctx = ssl.create_default_context()
            with urlopen(req, timeout=self.timeout, context=ctx) as response:
                return json.loads(response.read().decode('utf-8'))

        except URLError as e:
            logger.error(f"Telegram API error: {e}")
            return {"ok": False, "error": str(e)}
        except Exception as e:
            logger.error(f"Failed to send message: {e}")
            return {"ok": False, "error": str(e)}

    def send_message(self, chat_id: int, text: str, parse_mode: str = "HTML") -> Dict[str, Any]:
        url = f"{self.api_url}/sendMessage"
        payload = {
            "chat_id": chat_id,
            "text": text,
            "parse_mode": parse_mode
        }
        return self._make_request(url, payload)

    def send_reminder(self, chat_id: int, reminder_id: str, action_text: str, scheduled_time: str) -> Dict[str, Any]:
        message = self._format_reminder_message(reminder_id, action_text, scheduled_time)
        return self.send_message(chat_id, message)

    def _format_reminder_message(self, reminder_id: str, action_text: str, scheduled_time: str) -> str:
        return (
            f"🔔 <b>Напоминание</b>\n\n"
            f"📝 Действие: <b>{action_text}</b>\n"
            f"⏰ Время: {scheduled_time}\n"
            f"🆔 ID: {reminder_id}"
        )