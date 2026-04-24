import json
import logging
import os
from urllib.request import Request, urlopen
from urllib.error import URLError
from datetime import datetime
import boto3

from TelegramAdapter import TelegramAdapter
from ReminderTelegramService import ReminderTelegramService

logger = logging.getLogger()
logger.setLevel(logging.INFO)

TELEGRAM_BOT_TOKEN = os.environ.get('TELEGRAM_BOT_TOKEN', '')
JAVA_API_URL = os.environ.get('JAVA_API_URL', 'http://reminder-app:8090')
AWS_REGION = os.environ.get('AWS_REGION', 'us-east-1')
AWS_ENDPOINT_URL = os.environ.get('AWS_ENDPOINT_URL', '')
DLQ_QUEUE_NAME = os.environ.get('DLQ_QUEUE_NAME', 'dev-reminder-dlq')
INTERNAL_API_KEY = os.environ.get('INTERNAL_API_KEY', '')

def _make_http_request(url: str, method: str = 'GET', data: dict = None, timeout: int = 5):
    try:
        req = Request(url, method=method)

        req.add_header('X-API-Key', INTERNAL_API_KEY)

        if data:
            body = json.dumps(data).encode('utf-8')
            req.add_header('Content-Type', 'application/json')
            req = Request(url, data=body, method=method, headers=req.headers)

        import ssl
        ctx = ssl.create_default_context()
        with urlopen(req, timeout=timeout, context=ctx) as response:
            return json.loads(response.read().decode('utf-8'))
    except URLError as e:
        logger.error(f"HTTP request failed: {e}")
        return None

sqs_client = boto3.client('sqs', region_name=AWS_REGION, endpoint_url=AWS_ENDPOINT_URL) if AWS_ENDPOINT_URL else boto3.client('sqs', region_name=AWS_REGION)
events_client = boto3.client('events', region_name=AWS_REGION, endpoint_url=AWS_ENDPOINT_URL) if AWS_ENDPOINT_URL else boto3.client('events', region_name=AWS_REGION)
EVENT_BUS_NAME = os.environ.get('EVENT_BUS_NAME_TELEGRAM', 'telegram-events')

telegram_adapter = TelegramAdapter(TELEGRAM_BOT_TOKEN)
reminder_service = ReminderTelegramService(telegram_adapter)


def send_to_dlq(detail: dict, error_message: str) -> None:
    try:
        dlq_url = sqs_client.get_queue_url(QueueName=DLQ_QUEUE_NAME)['QueueUrl']

        message_body = {
            'errorMessage': error_message,
            'timestamp': datetime.utcnow().isoformat(),
            'detail': detail,
            'channel': 'telegram'
        }

        sqs_client.send_message(
            QueueUrl=dlq_url,
            MessageBody=json.dumps(message_body, ensure_ascii=False)
        )
        logger.info(f"Message sent to DLQ: {DLQ_QUEUE_NAME}")
    except Exception as e:
        logger.error(f"Failed to send to DLQ: {str(e)}", exc_info=True)


def lambda_handler(event, context):
    try:
        logger.info(f"Получено событие: {json.dumps(event)}")

        detail = event.get('detail', {})
        if not detail:
            detail = event

        reminder_id = detail.get('reminderId', '')
        user_email = detail.get('userEmail', '')

        if not reminder_id or not user_email:
            logger.error(f"Отсутствуют reminderId или userEmail: reminderId={reminder_id}, userEmail={user_email}")
            return {"statusCode": 400, "body": "Missing required fields"}

        logger.info(f"Обработка напоминания {reminder_id} для {user_email}")

        action = detail.get('action', 'Напоминание')
        scheduled_time = detail.get('scheduledTime', '')

        # Получаем chatId по email из Java API
        try:
            user_data = _make_http_request(f"{JAVA_API_URL}/api/external/user/email/{user_email}")
            if not user_data:
                logger.error(f"User not found or no chatId")
                send_to_dlq(detail, f"User lookup failed")
                return {'statusCode': 404}

            chat_id = user_data.get('telegramChatId')
            if not chat_id:
                logger.warning(f"Telegram not linked for {user_email}")
                return {'statusCode': 200, 'body': 'Telegram not linked'}
        except Exception as e:
            logger.exception("Failed to call user API")
            send_to_dlq(detail, str(e))
            return {'statusCode': 500}

        result = reminder_service.send_reminder(
            reminder_id=reminder_id,
            chat_id=chat_id,
            action_text=action,
            scheduled_time=scheduled_time
        )

        if result['status'] == 'failed':
            error_msg = result.get('error', 'Unknown error')
            logger.error(f"Failed to send reminder {reminder_id}: {error_msg}")
            send_to_dlq(detail, error_msg)
            return {
                "statusCode": 500,
                "body": json.dumps({
                    "message": f"Reminder failed: {reminder_id}",
                    "error": error_msg
                })
            }

        logger.info(f"Telegram напоминание {reminder_id} отправлено успешно chat_id={chat_id}")

        try:
            events_client.put_events(
                Entries=[{
                    'Source': 'by.losik.reminder',
                    'DetailType': 'ReminderSent',
                    'Detail': json.dumps({
                        **detail,
                        'channel': 'telegram',
                        'chatId': chat_id
                    }),
                    'EventBusName': EVENT_BUS_NAME
                }]
            )
            logger.info(f"Событие опубликовано в {EVENT_BUS_NAME}")
        except Exception as e:
            logger.warning(f"Не удалось опубликовать событие: {e}")

        return {
            "statusCode": 200,
            "body": json.dumps({
                "message": f"Reminder processed: {reminder_id}",
                "chat_id": chat_id
            })
        }

    except Exception as e:
        logger.error(f"Ошибка: {str(e)}", exc_info=True)
        raise