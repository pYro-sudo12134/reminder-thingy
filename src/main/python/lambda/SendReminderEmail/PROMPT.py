import json
import logging
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.utils import formatdate, make_msgid
from typing import Dict, Any, Optional, Tuple

# Настройка логирования
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class SMTPAdapter:
    """
    Третий слой – низкоуровневая работа с SMTP.
    Отвечает за:
    - чтение конфигурации SMTP;
    - установку соединения;
    - применение шифрования (TLS/SSL);
    - аутентификацию;
    - формирование MIME-сообщения;
    - отправку и закрытие соединения.
    """

    def __init__(self, config: Dict[str, Any]):
        """
        :param config: словарь с ключами:
            - host: адрес SMTP-сервера
            - port: порт
            - use_tls: использовать TLS (обычно порт 587)
            - use_ssl: использовать SSL (обычно порт 465)
            - username: логин (опционально)
            - password: пароль (опционально)
        """
        self.config = config
        self.host = config['host']
        self.port = config['port']
        self.use_tls = config.get('use_tls', False)
        self.use_ssl = config.get('use_ssl', False)
        self.username = config.get('username')
        self.password = config.get('password')

    def send(self, from_addr: str, to_addrs: list, subject: str,
             text_content: str, html_content: Optional[str] = None) -> Tuple[bool, Optional[str], Optional[str]]:
        """
        Отправляет письмо через SMTP.

        :return: кортеж (успех, message_id, текст_ошибки)
        """
        try:
            # Создаём MIME-сообщение
            msg = MIMEMultipart('alternative')
            msg['From'] = from_addr
            msg['To'] = ', '.join(to_addrs)
            msg['Subject'] = subject
            msg['Date'] = formatdate(localtime=True)
            msg['Message-ID'] = make_msgid(domain=self.host.split('.')[-1] if '.' in self.host else 'localhost')

            # Текстовая часть
            msg.attach(MIMEText(text_content, 'plain', 'utf-8'))
            # HTML часть, если есть
            if html_content:
                msg.attach(MIMEText(html_content, 'html', 'utf-8'))

            # Установка соединения с сервером
            if self.use_ssl:
                server = smtplib.SMTP_SSL(self.host, self.port)
            else:
                server = smtplib.SMTP(self.host, self.port)

            if self.use_tls:
                server.starttls()

            # Аутентификация, если заданы учётные данные
            if self.username and self.password:
                server.login(self.username, self.password)

            # Отправка письма
            server.send_message(msg)
            server.quit()

            # Получение Message-ID из заголовка
            message_id = msg['Message-ID']
            logger.info(f"Email sent successfully, Message-ID: {message_id}")
            return True, message_id, None

        except Exception as e:
            error_msg = str(e)
            logger.error(f"SMTP sending failed: {error_msg}")
            return False, None, error_msg


class ReminderEmailService:
    """
    Второй слой – бизнес-логика формирования письма-напоминания.
    Отвечает за:
    - приём данных о напоминании (id, email, текст действия, время);
    - формирование темы на основе текста напоминания;
    - генерацию текстового и HTML-содержимого;
    - определение отправителя;
    - вызов SMTP-адаптера;
    - логирование результата;
    - возврат результата.
    """

    def __init__(self, smtp_adapter: SMTPAdapter, from_email: str):
        """
        :param smtp_adapter: экземпляр SMTPAdapter
        :param from_email: адрес отправителя по умолчанию
        """
        self.smtp_adapter = smtp_adapter
        self.from_email = from_email

    def send_reminder(self, reminder_id: str, recipient_email: str,
                      action_text: str, scheduled_time: str) -> Dict[str, Any]:
        """
        Отправляет письмо-напоминание.

        :param reminder_id: идентификатор напоминания
        :param recipient_email: email получателя
        :param action_text: текст действия (например, "позвонить врачу")
        :param scheduled_time: время срабатывания в читаемом виде
        :return: словарь с результатом отправки
        """
        try:
            # Формируем тему письма
            subject = f"Напоминание: {action_text}"

            # Генерируем текстовое содержимое
            text_content = (
                f"Здравствуйте!\n\n"
                f"Это напоминание о запланированном действии:\n"
                f"'{action_text}'\n"
                f"Время: {scheduled_time}\n\n"
                f"Идентификатор напоминания: {reminder_id}\n"
            )

            # Генерируем HTML-содержимое
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

            # Вызов SMTP-адаптера
            success, message_id, error = self.smtp_adapter.send(
                from_addr=self.from_email,
                to_addrs=[recipient_email],
                subject=subject,
                text_content=text_content,
                html_content=html_content
            )

            # Логирование
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


class EventHandler:
    """
    Первый слой – обработка события от AWS EventBridge.
    Отвечает за:
    - получение события в стандартном формате AWS;
    - извлечение секции detail;
    - проверку обязательных полей;
    - логирование входящего события;
    - вызов сервиса отправки email;
    - логирование результата;
    - возврат ответа со статусом выполнения для EventBridge.
    """

    REQUIRED_FIELDS = ['reminder_id', 'email', 'action', 'scheduled_time']

    def __init__(self, reminder_service: ReminderEmailService):
        self.reminder_service = reminder_service

    def handle(self, event: Dict[str, Any]) -> Dict[str, Any]:
        """
        Обрабатывает событие от EventBridge.

        :param event: словарь события AWS
        :return: ответ в формате, ожидаемом EventBridge (например, для Lambda)
        """
        try:
            # Логирование входящего события
            logger.info(f"Received event: {json.dumps(event)}")

            # Извлечение секции detail
            detail = event.get('detail')
            if not detail:
                raise ValueError("Missing 'detail' section in event")

            # Проверка обязательных полей
            missing_fields = [f for f in self.REQUIRED_FIELDS if f not in detail]
            if missing_fields:
                raise ValueError(f"Missing required fields in detail: {missing_fields}")

            # Извлечение данных
            reminder_id = detail['reminder_id']
            recipient_email = detail['email']
            action_text = detail['action']
            scheduled_time = detail['scheduled_time']

            # Вызов сервиса отправки
            result = self.reminder_service.send_reminder(
                reminder_id=reminder_id,
                recipient_email=recipient_email,
                action_text=action_text,
                scheduled_time=scheduled_time
            )

            # Логирование результата
            logger.info(f"Reminder processing result: {result}")

            # Формирование ответа для EventBridge
            return {
                "statusCode": 200 if result['status'] == 'sent' else 500,
                "body": json.dumps(result)
            }

        except Exception as e:
            logger.exception("Error handling event")
            return {
                "statusCode": 400,
                "body": json.dumps({"error": str(e)})
            }


# Пример использования и конфигурации (для демонстрации)
if __name__ == "__main__":
    # Конфигурация SMTP (например, для Mailtrap или реального сервера)
    smtp_config = {
        "host": "smtp.example.com",
        "port": 587,
        "use_tls": True,
        "use_ssl": False,
        "username": "your_username",
        "password": "your_password"
    }

    # Инициализация слоёв
    smtp_adapter = SMTPAdapter(smtp_config)
    reminder_service = ReminderEmailService(smtp_adapter, from_email="noreply@example.com")
    event_handler = EventHandler(reminder_service)

    # Пример события EventBridge
    sample_event = {
        "version": "0",
        "id": "12345",
        "detail-type": "ReminderScheduled",
        "source": "my.reminder.app",
        "account": "123456789012",
        "time": "2025-01-01T12:00:00Z",
        "region": "us-east-1",
        "resources": [],
        "detail": {
            "reminder_id": "rem-001",
            "email": "user@example.com",
            "action": "Позвонить врачу",
            "scheduled_time": "2025-01-02 10:00"
        }
    }

    # Обработка события
    response = event_handler.handle(sample_event)
    print(json.dumps(response, indent=2, ensure_ascii=False))