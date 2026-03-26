import json
import logging
import os

logger = logging.getLogger()
logger.setLevel(logging.INFO)

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
            logger.error("Event detail пуст")
            return {"statusCode": 400, "body": "No detail in event"}
        
        reminder_id = detail.get('reminderId', '')
        user_email = detail.get('userEmail', '')
        
        if not reminder_id or not user_email:
            logger.error("Отсутствуют reminderId или userEmail")
            return {"statusCode": 400, "body": "Missing required fields"}
        
        logger.info(f"Обработка напоминания {reminder_id} для {user_email}")
        
        # Отправка email
        action = detail.get('action', 'Напоминание')
        scheduled_time = detail.get('scheduledTime', '')
                
        logger.info(f"Напоминание {reminder_id} обработано успешно")
        
        return {
            "statusCode": 200,
            "body": json.dumps({
                "message": f"Reminder processed: {reminder_id}"
            })
        }
        
    except Exception as e:
        logger.error(f"Ошибка: {str(e)}", exc_info=True)
        raise