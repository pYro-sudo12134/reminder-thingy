#!/usr/bin/env python3
import os
import time
import yaml
import boto3
from prometheus_client import start_http_server, Gauge
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class CloudWatchCollector:
    def __init__(self):
        self.endpoint = os.getenv('AWS_ENDPOINT_URL', 'http://localstack:4566')
        self.region = os.getenv('AWS_REGION', 'us-east-1')

        with open('config.yml', 'r') as f:
            self.config = yaml.safe_load(f)

        self.cloudwatch = boto3.client(
            'cloudwatch',
            region_name=self.region,
            endpoint_url=self.endpoint,
            aws_access_key_id='test',
            aws_secret_access_key='test',
            verify=False
        )

        self.gauges = {}
        logger.info(f"CloudWatch collector initialized with endpoint: {self.endpoint}")

    def get_gauge(self, name, namespace, metric_name, statistic, dimensions):
        """Создает или возвращает существующую метрику"""
        key = f"{namespace}_{metric_name}_{statistic}"
        if key not in self.gauges:
            label_names = [d['Name'].lower() for d in dimensions] if dimensions else []
            self.gauges[key] = Gauge(
                name.replace('.', '_').replace('/', '_').lower(),
                f'{metric_name} {statistic}',
                label_names
            )
        return self.gauges[key]

    def collect(self):
        """Сбор метрик"""
        try:
            for metric in self.config.get('metrics', []):
                namespace = metric['aws_namespace']
                metric_name = metric['aws_metric_name']
                dimensions_config = metric.get('aws_dimensions', [])
                statistics = metric.get('aws_statistics', ['Average'])

                logger.debug(f"Collecting {namespace} - {metric_name}")

                response = self.cloudwatch.list_metrics(
                    Namespace=namespace,
                    MetricName=metric_name
                )

                for metric_data in response.get('Metrics', []):
                    dims = []
                    for dim in metric_data.get('Dimensions', []):
                        if dim['Name'] in dimensions_config:
                            dims.append({'Name': dim['Name'], 'Value': dim['Value']})

                    if not dims and dimensions_config:
                        continue

                    end = time.time()
                    start = end - 300

                    try:
                        stats = self.cloudwatch.get_metric_statistics(
                            Namespace=namespace,
                            MetricName=metric_name,
                            Dimensions=dims,
                            StartTime=start,
                            EndTime=end,
                            Period=300,
                            Statistics=statistics
                        )

                        if stats['Datapoints']:
                            datapoint = stats['Datapoints'][-1]

                            for stat in statistics:
                                if stat in datapoint:
                                    value = datapoint[stat]

                                    prom_name = f"aws_{namespace.lower().replace('/', '_')}_{metric_name.lower()}_{stat.lower()}"

                                    labels = {}
                                    for dim in dims:
                                        labels[dim['Name'].lower()] = dim['Value']

                                    gauge = self.get_gauge(prom_name, namespace, metric_name, stat, dims)
                                    if labels:
                                        gauge.labels(**labels).set(value)
                                    else:
                                        gauge.set(value)

                                    logger.debug(f"Set {prom_name} = {value} {labels}")

                    except Exception as e:
                        logger.error(f"Error getting stats for {metric_name}: {e}")

        except Exception as e:
            logger.error(f"Collection error: {e}")
            import traceback
            traceback.print_exc()

def main():
    port = 9106
    start_http_server(port)
    logger.info(f"CloudWatch Exporter started on port {port}")

    collector = CloudWatchCollector()

    while True:
        collector.collect()
        time.sleep(30)

if __name__ == '__main__':
    main()