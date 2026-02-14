FROM python:3.9-alpine

RUN pip install boto3 prometheus-client pyyaml requests

WORKDIR /app
COPY exporter.py .
COPY config.yml .

EXPOSE 9106

CMD ["python", "exporter.py"]