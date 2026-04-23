import os
import sys
import logging
from telegram import Update
from telegram.ext import Application, CommandHandler, MessageHandler, filters, ContextTypes

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'python'))
from telegram_bot.TelegramBotService import TelegramBotService

logging.basicConfig(
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    level=logging.INFO
)
logger = logging.getLogger(__name__)

TELEGRAM_TOKEN = os.environ.get('TELEGRAM_BOT_TOKEN', '8791705854:AAFRWOaGHT0n84Myu6R-nkh-C80cd4ivFpc')
API_BASE_URL = os.environ.get('API_BASE_URL', 'http://localhost:8090')

bot_service = TelegramBotService(API_BASE_URL)


async def start_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        "Привет! Я бот для напоминаний.\n\n"
        "Отправьте код привязки, чтобы связать ваш аккаунт.\n"
        "Код можно получить в приложении Voice Reminder."
    )


async def help_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        "Доступные команды:\n"
        "/start - Начать\n"
        "/help - Помощь\n"
        "/status - Проверить привязку\n"
        "/unbind - Отвязать аккаунт\n\n"
        "Отправьте код привязки, чтобы привязать Telegram к аккаунту."
    )


async def status_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    chat_id = update.effective_chat.id
    result = bot_service.get_binding_status(chat_id)

    if result.get('linked'):
        await update.message.reply_text(f"Аккаунт привязан. Chat ID: {chat_id}")
    else:
        await update.message.reply_text("Аккаунт не привязан. Отправьте код привязки.")


async def unbind_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    chat_id = update.effective_chat.id
    result = bot_service.unbind_account(chat_id)

    if result.get('success'):
        await update.message.reply_text("Аккаунт успешно отвязан.")
    else:
        await update.message.reply_text(f"Ошибка: {result.get('error', 'Неизвестная ошибка')}")


async def handle_binding_code(update: Update, context: ContextTypes.DEFAULT_TYPE):
    code = update.message.text.strip().upper()
    chat_id = update.effective_chat.id

    if len(code) != 6:
        return

    if not all(c in '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ' for c in code):
        return

    logger.info(f"Processing binding code {code} from chat {chat_id}")
    result = bot_service.validate_code(code, chat_id)

    if result.get('success'):
        await update.message.reply_text(
            f"Аккаунт успешно привязан!\n\n"
            f"Теперь вы будете получать напоминания в Telegram."
        )
    elif result.get('error') == 'Invalid code':
        pass
    elif result.get('error') == 'Code expired':
        await update.message.reply_text("Код истёк. Получите новый код в приложении.")
    else:
        await update.message.reply_text(f"Ошибка: {result.get('error', 'Неизвестная ошибка')}")


async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        "Отправьте /help для списка команд или код привязки."
    )


def main():
    application = Application.builder().token(TELEGRAM_TOKEN).build()

    application.add_handler(CommandHandler("start", start_command))
    application.add_handler(CommandHandler("help", help_command))
    application.add_handler(CommandHandler("status", status_command))
    application.add_handler(CommandHandler("unbind", unbind_command))
    application.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_binding_code))
    application.add_handler(MessageHandler(filters.TEXT, handle_message))

    logger.info("Bot started")
    application.run_polling()


if __name__ == '__main__':
    main()