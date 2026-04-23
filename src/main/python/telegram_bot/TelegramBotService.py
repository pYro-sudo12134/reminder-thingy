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
        try:
            url = f"{self.api_base_url}/api/user/0/telegram/bot/validate"
            response = requests.post(
                url,
                json={"code": code, "chatId": chat_id},
                timeout=self.timeout
            )
            if response.status_code == 200:
                return response.json()
            else:
                logger.error(f"API error: {response.status_code} - {response.text}")
                return {"success": False, "error": response.text}
        except requests.RequestException as e:
            logger.error(f"Request failed: {e}")
            return {"success": False, "error": str(e)}

    def get_binding_status(self, chat_id: int) -> Dict[str, Any]:
        try:
            url = f"{self.api_base_url}/api/user/0/telegram/bot/status"
            response = requests.get(
                url,
                params={"chatId": chat_id},
                timeout=self.timeout
            )
            if response.status_code == 200:
                return response.json()
            else:
                logger.error(f"API error: {response.status_code}")
                return {"linked": False}
        except requests.RequestException as e:
            logger.error(f"Request failed: {e}")
            return {"linked": False, "error": str(e)}

    def unbind_account(self, chat_id: int) -> Dict[str, Any]:
        try:
            url = f"{self.api_base_url}/api/user/0/telegram/bot/unbind"
            response = requests.post(
                url,
                json={"chatId": chat_id},
                timeout=self.timeout
            )
            if response.status_code == 200:
                return response.json()
            else:
                logger.error(f"API error: {response.status_code}")
                return {"success": False, "error": response.text}
        except requests.RequestException as e:
            logger.error(f"Request failed: {e}")
            return {"success": False, "error": str(e)}

    def send_reminder(self, chat_id: int, message: str) -> Dict[str, Any]:
        try:
            url = f"{self.api_base_url}/api/telegram/send"
            response = requests.post(
                url,
                json={"chatId": chat_id, "message": message},
                timeout=self.timeout
            )
            if response.status_code == 200:
                return response.json()
            else:
                logger.error(f"API error: {response.status_code}")
                return {"success": False, "error": response.text}
        except requests.RequestException as e:
            logger.error(f"Request failed: {e}")
            return {"success": False, "error": str(e)}