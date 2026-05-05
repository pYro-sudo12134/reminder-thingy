import json
import logging
import os
import boto3
from datetime import datetime

from SMTPAdapter import SMTPAdapter
from ReminderEmailService import ReminderEmailService

logger = logging.getLogger()
logger.setLevel(logging.INFO)

def parse_bool(value):
    if value is None:
        return False
    return str(value).lower() in ('true', '1', 'yes')

smtp_tls_raw = os.environ.get('SMTP_TLS', '')
smtp_ssl_raw = os.environ.get('SMTP_SSL', '')
smtp_port_raw = os.environ.get('SMTP_PORT', '')

SMTP_CONFIG = {
    'host': os.environ.get('SMTP_HOST', '') or 'smtp.gmail.com',
    'port': int(smtp_port_raw) if smtp_port_raw else 587,
    'use_tls': parse_bool(smtp_tls_raw),
    'use_ssl': parse_bool(smtp_ssl_raw),
    'username': os.environ.get('SMTP_USERNAME', ''),
    'password': os.environ.get('SMTP_PASSWORD', '')
}

FROM_EMAIL = os.environ.get('FROM_EMAIL', '')

AWS_REGION = os.environ.get('AWS_REGION', 'us-east-1')
AWS_ENDPOINT_URL = os.environ.get('AWS_ENDPOINT_URL', '')
DLQ_QUEUE_NAME = os.environ.get('DLQ_QUEUE_NAME', 'dev-reminder-dlq')

smtp_adapter = SMTPAdapter(SMTP_CONFIG)
reminder_service = ReminderEmailService(smtp_adapter, FROM_EMAIL)

sqs_client = boto3.client('sqs', region_name=AWS_REGION, endpoint_url=AWS_ENDPOINT_URL) if AWS_ENDPOINT_URL else boto3.client('sqs', region_name=AWS_REGION)

def send_to_dlq(detail: dict, error_message: str) -> None:
    try:
        dlq_url = sqs_client.get_queue_url(QueueName=DLQ_QUEUE_NAME)['QueueUrl']
        
        message_body = {
            'errorMessage': error_message,
            'timestamp': datetime.utcnow().isoformat(),
            'detail': detail
        }
        
        sqs_client.send_message(
            QueueUrl=dlq_url,
            MessageBody=json.dumps(message_body, ensure_ascii=False)
        )
        logger.info(f"Message sent to DLQ: {DLQ_QUEUE_NAME}")
    except Exception as e:
        logger.error(f"Failed to send to DLQ: {str(e)}", exc_info=True)


def lambda_handler(event, context):
    """
    Обработчик событий EventBridge для отправки напоминаний.
    
    EventBridge передаёт detail с полями:
    - reminderId: UUID напоминания
    - userEmail: email получателя
    - action: текст напоминания
    - scheduledTime: время срабатывания
    """
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
        
        result = reminder_service.send_reminder(
            reminder_id=reminder_id,
            recipient_email=user_email,
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
        
        logger.info(f"Напоминание {reminder_id} отправлено успешно")

        return {
            "statusCode": 200,
            "body": json.dumps({
                "message": f"Reminder processed: {reminder_id}",
                "message_id": result.get('message_id')
            })
        }
        
    except Exception as e:
        logger.error(f"Ошибка: {str(e)}", exc_info=True)
        raise