FROM python:3.10-slim

WORKDIR /app

RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    curl \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
COPY requirements-llm.txt .

RUN pip install --no-cache-dir -r requirements.txt && \
    pip install --no-cache-dir -r requirements-llm.txt && \
    pip install --no-cache-dir boto3

COPY . .

RUN mkdir -p /app/models /app/data /app/logs /app/certs

ENV PYTHONPATH=/app
ENV PYTHONUNBUFFERED=1
ENV OMP_NUM_THREADS=4
ENV MKL_NUM_THREADS=4
ENV CUDA_VISIBLE_DEVICES=-1

CMD ["python", "run_server.py"]