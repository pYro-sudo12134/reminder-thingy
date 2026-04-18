import logging
from typing import Dict, Any

from SMTPAdapter import SMTPAdapter

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class ReminderEmailService:
    def __init__(self, smtp_adapter: SMTPAdapter, from_email: str):
        self.smtp_adapter = smtp_adapter
        self.from_email = from_email

    def send_reminder(self, reminder_id: str, recipient_email: str,
                  action_text: str, scheduled_time: str) -> Dict[str, Any]:
        try:
            subject = f"Напоминание: {action_text}"

            text_content = (
                f"Здравствуйте!\n\n"
                f"Это напоминание о запланированном действии:\n"
                f"'{action_text}'\n"
                f"Время: {scheduled_time}\n\n"
                f"Идентификатор напоминания: {reminder_id}\n"
            )

            html_content = f"""
            <html>
              <body>
                <h2>Напоминание</h2>
                <p>Здравствуйте!</p>
                <p>Это напоминание о запланированном действии:</p>
                <p><strong>{action_text}</strong></p>
                <p>Время: {scheduled_time}</p>
                <p>Идентификатор напоминания: {reminder_id}</p>
              </body>
            </html>
            """

            success, message_id, error = self.smtp_adapter.send(
                from_addr=self.from_email,
                to_addrs=[recipient_email],
                subject=subject,
                text_content=text_content,
                html_content=html_content
            )

            if success:
                logger.info(f"Reminder {reminder_id} sent to {recipient_email}, Message-ID: {message_id}")
                return {
                    "reminder_id": reminder_id,
                    "status": "sent",
                    "message_id": message_id,
                    "recipient": recipient_email
                }
            else:
                logger.error(f"Failed to send reminder {reminder_id}: {error}")
                return {
                    "reminder_id": reminder_id,
                    "status": "failed",
                    "error": error,
                    "recipient": recipient_email
                }

        except Exception as e:
            logger.exception(f"Unexpected error in ReminderEmailService: {e}")
            return {
                "reminder_id": reminder_id,
                "status": "failed",
                "error": str(e),
                "recipient": recipient_email
            }