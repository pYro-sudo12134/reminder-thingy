import logging
import requests
from typing import Dict, Any, Optional

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class TelegramBotService:
    def __init__(self, api_base_url: str, timeout: int = 10):
        self.api_base_url = api_base_url.rstrip('/')
        self.timeout = timeout

    def validate_code(self, code: str, chat_id: int) -> Dict[str, Any]:
        url = f"{self.api_base_url}/api/telegram/bot/validate"
        response = requests.post(
            url,
            json={"code": code, "chatId": chat_id},
            timeout=self.timeout
        )
        return self._handle_response(response)

    def get_binding_status(self, chat_id: int) -> Dict[str, Any]:
        url = f"{self.api_base_url}/api/telegram/bot/status"
        response = requests.get(
            url,
            params={"chatId": chat_id},
            timeout=self.timeout
        )
        if response.status_code == 200:
            return response.json()
        return {"success": True, "linked": False}

    def unbind_account(self, chat_id: int) -> Dict[str, Any]:
        url = f"{self.api_base_url}/api/telegram/bot/unbind"
        response = requests.post(
            url,
            json={"chatId": chat_id},
            timeout=self.timeout
        )
        return self._handle_response(response)

    def _handle_response(self, response):
        if response.status_code == 200:
            return response.json()
        return {"success": False, "error": response.text}