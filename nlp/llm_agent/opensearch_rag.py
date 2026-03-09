import json
import ssl
from typing import List, Dict, Any, Optional
from datetime import datetime, timedelta
from opensearchpy import OpenSearch, RequestsHttpConnection
from opensearchpy.helpers import bulk
import logging
import urllib3
import certifi

# Отключаем предупреждения
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

logger = logging.getLogger(__name__)

class OpenSearchReminderRAG:
    """RAG агент на базе OpenSearch для напоминаний"""

    def __init__(self):
        # Получаем конфигурацию из окружения
        self.host = os.getenv('OPENSEARCH_HOST', 'localstack')
        self.port = int(os.getenv('OPENSEARCH_PORT', '4510'))
        self.use_ssl = os.getenv('OPENSEARCH_USE_SSL', 'false').lower() == 'true'
        self.user = os.getenv('OPENSEARCH_USER', 'admin')
        self.password = os.getenv('OPENSEARCH_ADMIN_PASSWORD', '')

        logger.info(f" OpenSearch Config:")
        logger.info(f"   Host: {self.host}:{self.port}")
        logger.info(f"   SSL: {self.use_ssl}")
        logger.info(f"   Auth: {'enabled' if self.password else 'disabled'}")

        # Настройки подключения
        self.http_auth = (self.user, self.password) if self.password else None

        # Создаем клиент с правильным SSL контекстом
        self.client = self._create_client()

        # Имя индекса
        self.index_name = os.getenv('OPENSEARCH_INDEX', 'reminder_formats')

        # Проверяем подключение
        if self.client:
            self._check_connection()
        else:
            logger.warning("️ OpenSearch client not created, RAG disabled")

    def _create_ssl_context(self):
        """Создает SSL контекст как в Java (trust all)"""
        try:
            # Создаем SSL контекст который доверяет всем сертификатам
            ssl_context = ssl.create_default_context()
            ssl_context.check_hostname = False
            ssl_context.verify_mode = ssl.CERT_NONE

            # Важно: для Python 3.10+ нужно настроить протоколы
            ssl_context.minimum_version = ssl.TLSVersion.TLSv1_2
            ssl_context.maximum_version = ssl.TLSVersion.TLSv1_3

            # Включаем все нужные cipher suites
            ssl_context.set_ciphers('DEFAULT:@SECLEVEL=1')

            logger.info(" SSL context created with verification disabled")
            return ssl_context

        except Exception as e:
            logger.error(f" Error creating SSL context: {e}")
            return None

    def _create_client(self):
        """Создает клиент OpenSearch"""
        try:
            import ssl
            from opensearchpy import OpenSearch, RequestsHttpConnection

            # Получаем настройки
            use_ssl = os.getenv('OPENSEARCH_USE_SSL', 'false').lower() == 'true'
            disable_ssl_verification = os.getenv('OPENSEARCH_DISABLE_SSL_VERIFICATION', 'true').lower() == 'true'

            # ВАЖНО: Используем HTTP порт 4510, даже с SSL!
            # OpenSearch в LocalStack слушает HTTPS на том же порту
            port = int(os.getenv('OPENSEARCH_PORT', '4510'))

            logger.info(f" Connecting to OpenSearch at {self.host}:{port} with SSL={use_ssl}")

            client_params = {
                'hosts': [{'host': self.host, 'port': port}],
                'http_compress': True,
                'use_ssl': use_ssl,
                'verify_certs': not disable_ssl_verification,  # Инвертируем
                'ssl_show_warn': False,
                'http_auth': self.http_auth,
                'connection_class': RequestsHttpConnection,
                'timeout': 30,
                'max_retries': 3,
                'retry_on_timeout': True
            }

            if use_ssl and disable_ssl_verification:
                ssl_context = ssl.create_default_context()
                ssl_context.check_hostname = False
                ssl_context.verify_mode = ssl.CERT_NONE

                ssl_context.minimum_version = ssl.TLSVersion.TLSv1_2
                ssl_context.maximum_version = ssl.TLSVersion.TLSv1_3
                ssl_context.set_ciphers('DEFAULT:@SECLEVEL=1')

                client_params['ssl_context'] = ssl_context
                client_params['ssl_assert_hostname'] = False
                client_params['ssl_assert_fingerprint'] = False

                logger.info(" SSL verification disabled (like Java)")

            elif use_ssl and not disable_ssl_verification:
                # Используем сертификаты если есть
                ca_cert = os.getenv('OPENSEARCH_CA_CERT', '/app/certs/root-ca.pem')
                if os.path.exists(ca_cert):
                    client_params['ca_certs'] = ca_cert
                    logger.info(f" Using CA cert: {ca_cert}")

            client = OpenSearch(**client_params)

            # Проверка подключения
            info = client.info()
            logger.info(f" Connected to OpenSearch: {info['version']['number']}")
            logger.info(f"   Using SSL: {use_ssl}, verification: {not disable_ssl_verification}")

            return client

        except Exception as e:
            logger.error(f" Failed to create OpenSearch client: {e}")
            logger.info(" Falling back to HTTP without SSL...")

            try:
                client_params['use_ssl'] = False
                client_params['verify_certs'] = False
                client_params.pop('ssl_context', None)

                client = OpenSearch(**client_params)
                info = client.info()
                logger.info(f" Connected to OpenSearch (fallback HTTP): {info['version']['number']}")
                return client
            except Exception as e2:
                logger.error(f" Fallback also failed: {e2}")
                return None

    def _check_connection(self):
        """Проверяет подключение к OpenSearch"""
        if not self.client:
            return

        try:
            info = self.client.info()
            logger.info(f" Connected to OpenSearch: {info['version']['number']}")
            self._create_index_if_not_exists()
        except Exception as e:
            logger.warning(f"️ OpenSearch connection failed: {e}")
            logger.warning("RAG features will be disabled")
            self.client = None

    def _create_index_if_not_exists(self):
        """Создает индекс с k-NN поддержкой если не существует"""
        if not self.client:
            return

        if not self.client.indices.exists(index=self.index_name):
            index_body = {
                "settings": {
                    "index": {
                        "knn": True,
                        "knn.algo_param.ef_search": 100,
                        "number_of_shards": int(os.getenv('OPENSEARCH_SHARDS', '2')),
                        "number_of_replicas": int(os.getenv('OPENSEARCH_REPLICAS', '1'))
                    }
                },
                "mappings": {
                    "properties": {
                        "text": {
                            "type": "text",
                            "fields": {
                                "keyword": {"type": "keyword"}
                            }
                        },
                        "vector": {
                            "type": "knn_vector",
                            "dimension": int(os.getenv('EMBEDDING_DIMENSION', '1536'))
                        },
                        "format_type": {"type": "keyword"},
                        "language": {"type": "keyword"},
                        "time_expression": {"type": "text"},
                        "entities": {
                            "type": "nested",
                            "properties": {
                                "text": {"type": "text"},
                                "type": {"type": "keyword"},
                                "confidence": {"type": "float"}
                            }
                        },
                        "examples_count": {"type": "integer"},
                        "created_at": {"type": "date"},
                        "metadata": {"type": "object", "enabled": False}
                    }
                }
            }

            try:
                self.client.indices.create(index=self.index_name, body=index_body)
                logger.info(f" Created index {self.index_name} with k-NN support")
            except Exception as e:
                logger.error(f" Failed to create index: {e}")

    def add_format_examples(self, examples: List[Dict], embeddings: List[List[float]]):
        """Добавляет примеры форматов с векторами"""
        if not self.client:
            logger.warning("OpenSearch not available, skipping example storage")
            return 0

        actions = []
        for i, (example, embedding) in enumerate(zip(examples, embeddings)):
            doc_id = f"{example['language']}_{abs(hash(example['text']))}"

            action = {
                "_index": self.index_name,
                "_id": doc_id,
                "_source": {
                    "text": example['text'],
                    "vector": embedding,
                    "format_type": example['format_type'],
                    "language": example['language'],
                    "time_expression": example.get('time_expression', ''),
                    "entities": example.get('entities', []),
                    "examples_count": example.get('count', 1),
                    "created_at": datetime.now().isoformat(),
                    "metadata": {
                        "source": example.get('source', 'training'),
                        "version": os.getenv('MODEL_VERSION', '1.0')
                    }
                }
            }
            actions.append(action)

        success_count = 0
        batch_size = 100

        for i in range(0, len(actions), batch_size):
            batch = actions[i:i+batch_size]
            try:
                success, failed = bulk(
                    self.client,
                    batch,
                    raise_on_error=False,
                    request_timeout=60
                )
                success_count += success
                if failed:
                    logger.warning(f"Failed to index {len(failed)} documents")
            except Exception as e:
                logger.error(f"Bulk indexing error: {e}")

        logger.info(f" Indexed {success_count} format examples")
        return success_count

    def search_similar_formats(self, query_vector: List[float], k: int = 5,
                               language: Optional[str] = None,
                               min_score: float = 0.5) -> List[Dict]:
        """Поиск похожих форматов по вектору"""
        if not self.client:
            return []

        # k-NN запрос
        knn_query = {
            "vector": {
                "vector": query_vector,
                "k": k * 2
            }
        }

        if language:
            query = {
                "size": k,
                "query": {
                    "bool": {
                        "must": [
                            {"knn": knn_query}
                        ],
                        "filter": [
                            {"term": {"language": language}}
                        ]
                    }
                },
                "min_score": min_score
            }
        else:
            query = {
                "size": k,
                "query": {
                    "knn": knn_query
                },
                "min_score": min_score
            }

        try:
            response = self.client.search(index=self.index_name, body=query)

            results = []
            for hit in response['hits']['hits']:
                results.append({
                    'text': hit['_source']['text'],
                    'format_type': hit['_source']['format_type'],
                    'language': hit['_source']['language'],
                    'score': hit['_score'],
                    'time_expression': hit['_source'].get('time_expression', ''),
                    'entities': hit['_source'].get('entities', [])
                })

            logger.info(f"🔍 Found {len(results)} similar formats")
            return results

        except Exception as e:
            logger.error(f"Search error: {e}")
            return []

    def hybrid_search(self, text: str, query_vector: List[float], k: int = 5) -> List[Dict]:
        """Гибридный поиск: текст + вектор"""
        if not self.client:
            return []

        query = {
            "size": k,
            "query": {
                "bool": {
                    "should": [
                        {
                            "match": {
                                "text": {
                                    "query": text,
                                    "boost": 0.3
                                }
                            }
                        },
                        {
                            "knn": {
                                "vector": {
                                    "vector": query_vector,
                                    "k": k,
                                    "boost": 0.7
                                }
                            }
                        }
                    ]
                }
            }
        }

        try:
            response = self.client.search(index=self.index_name, body=query)
            return response['hits']['hits']
        except Exception as e:
            logger.error(f"Hybrid search error: {e}")
            return []

    def get_format_statistics(self) -> Dict:
        """Статистика по форматам"""
        if not self.client:
            return {}

        query = {
            "size": 0,
            "aggs": {
                "by_language": {
                    "terms": {"field": "language", "size": 10}
                },
                "by_format": {
                    "terms": {"field": "format_type", "size": 10}
                },
                "avg_examples": {
                    "avg": {"field": "examples_count"}
                },
                "formats_over_time": {
                    "date_histogram": {
                        "field": "created_at",
                        "calendar_interval": "day"
                    }
                }
            }
        }

        try:
            response = self.client.search(index=self.index_name, body=query)
            return response['aggregations']
        except Exception as e:
            logger.error(f"Statistics error: {e}")
            return {}

    def delete_old_examples(self, days: int = 30):
        """Удаляет старые примеры"""
        if not self.client:
            return

        cutoff = (datetime.now() - timedelta(days=days)).isoformat()

        query = {
            "query": {
                "range": {
                    "created_at": {
                        "lt": cutoff
                    }
                }
            }
        }

        try:
            response = self.client.delete_by_query(
                index=self.index_name,
                body=query,
                conflicts="proceed"
            )
            logger.info(f"Deleted {response['deleted']} old examples")
        except Exception as e:
            logger.error(f"Cleanup error: {e}")