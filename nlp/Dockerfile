FROM python:3.10-slim

WORKDIR /app

# Установка системных зависимостей
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Копирование зависимостей
COPY requirements.txt .
COPY requirements-llm.txt .

# Установка Python зависимостей
RUN pip install --no-cache-dir -r requirements.txt && \
    pip install --no-cache-dir -r requirements-llm.txt && \
    pip install --no-cache-dir boto3 certifi

# Копирование кода
COPY . .

# Создание директорий
RUN mkdir -p /app/models /app/data /app/logs /app/certs

# Настройка переменных окружения
ENV PYTHONPATH=/app
ENV PYTHONUNBUFFERED=1

# Запуск
CMD ["python", "run_server.py"]