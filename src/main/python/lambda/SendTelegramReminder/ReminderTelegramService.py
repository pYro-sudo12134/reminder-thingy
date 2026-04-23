import logging
from typing import Dict, Any

from TelegramAdapter import TelegramAdapter

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class ReminderTelegramService:
    def __init__(self, telegram_adapter: TelegramAdapter):
        self.telegram_adapter = telegram_adapter

    def send_reminder(self, reminder_id: str, chat_id: int,
                     action_text: str, scheduled_time: str) -> Dict[str, Any]:
        try:
            result = self.telegram_adapter.send_reminder(
                chat_id=chat_id,
                reminder_id=reminder_id,
                action_text=action_text,
                scheduled_time=scheduled_time
            )

            if result.get("ok"):
                logger.info(f"Reminder {reminder_id} sent to chat {chat_id}")
                return {
                    "reminder_id": reminder_id,
                    "status": "sent",
                    "chat_id": chat_id
                }
            else:
                error = result.get("error", "Unknown error")
                logger.error(f"Failed to send reminder {reminder_id}: {error}")
                return {
                    "reminder_id": reminder_id,
                    "status": "failed",
                    "error": error,
                    "chat_id": chat_id
                }

        except Exception as e:
            logger.exception(f"Unexpected error in ReminderTelegramService: {e}")
            return {
                "reminder_id": reminder_id,
                "status": "failed",
                "error": str(e),
                "chat_id": chat_id
            }