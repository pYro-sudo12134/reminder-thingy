import ssl
import os
import requests
from typing import List, Dict, Any, Optional
from datetime import datetime, timedelta
from opensearchpy import OpenSearch, RequestsHttpConnection
from opensearchpy.helpers import bulk
import logging
import urllib3
import hashlib

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

logger = logging.getLogger(__name__)

class OpenSearchReminderRAG:
    """RAG агент на базе OpenSearch для напоминаний с векторизацией через Ollama"""

    def __init__(self):
        self.host = os.getenv('OPENSEARCH_HOST', 'localstack')
        self.port = int(os.getenv('OPENSEARCH_PORT', '4510'))
        self.use_ssl = os.getenv('OPENSEARCH_USE_SSL', 'false').lower() == 'true'
        self.user = os.getenv('OPENSEARCH_USER', 'admin')
        self.password = os.getenv('OPENSEARCH_ADMIN_PASSWORD', '')

        self.ollama_host = os.getenv('OLLAMA_HOST', 'ollama')
        self.ollama_port = os.getenv('OLLAMA_PORT', '11434')
        self.ollama_embedding_model = os.getenv('OLLAMA_EMBEDDING_MODEL', 'nomic-embed-text')
        self.embedding_dimension = int(os.getenv('EMBEDDING_DIMENSION', '768'))

        logger.info(f"OpenSearch Config:")
        logger.info(f"   Host: {self.host}:{self.port}")
        logger.info(f"   SSL: {self.use_ssl}")
        logger.info(f"   Auth: {'enabled' if self.password else 'disabled'}")
        logger.info(f"   Ollama embeddings: {self.ollama_embedding_model}")
        logger.info(f"   Embedding dimension: {self.embedding_dimension}")

        self.http_auth = (self.user, self.password) if self.password else None
        self.client = self._create_client()
        self.index_name = os.getenv('OPENSEARCH_INDEX', 'reminder_formats')

        if self.client:
            self._check_connection()
            self._create_index_if_not_exists()
        else:
            logger.warning("OpenSearch client not created, RAG disabled")

    def _create_client(self):
        """Создает клиент OpenSearch"""
        try:
            use_ssl = os.getenv('OPENSEARCH_USE_SSL', 'false').lower() == 'true'
            disable_ssl_verification = os.getenv('OPENSEARCH_DISABLE_SSL_VERIFICATION', 'true').lower() == 'true'

            logger.info(f"🔌 Connecting to OpenSearch at {self.host}:{self.port} with SSL={use_ssl}")

            client_params = {
                'hosts': [{'host': self.host, 'port': self.port}],
                'http_compress': True,
                'use_ssl': use_ssl,
                'verify_certs': not disable_ssl_verification,
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
                client_params['ssl_context'] = ssl_context
                logger.info("   SSL verification disabled")

            client = OpenSearch(**client_params)
            return client

        except Exception as e:
            logger.error(f"Failed to create OpenSearch client: {e}")
            return None

    def _check_connection(self):
        """Проверяет подключение к OpenSearch"""
        if not self.client:
            return

        try:
            info = self.client.info()
            logger.info(f"Connected to OpenSearch: {info['version']['number']}")
        except Exception as e:
            logger.warning(f"OpenSearch connection failed: {e}")
            self.client = None

    def _create_index_if_not_exists(self):
        """Создает индекс с k-NN поддержкой если не существует"""
        if not self.client or self.client.indices.exists(index=self.index_name):
            return

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
                            "keyword": {"type": "keyword", "ignore_above": 256}
                        }
                    },
                    "vector": {
                        "type": "knn_vector",
                        "dimension": self.embedding_dimension,
                        "method": {
                            "name": "hnsw",
                            "space_type": "cosinesimil",
                            "engine": "lucene",
                            "parameters": {
                                "ef_construction": 128,
                                "m": 24
                            }
                        }
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
                    "updated_at": {"type": "date"},
                    "source": {"type": "keyword"}
                }
            }
        }

        try:
            self.client.indices.create(index=self.index_name, body=index_body)
            logger.info(f"Created index {self.index_name} with k-NN support (dim={self.embedding_dimension})")
        except Exception as e:
            logger.error(f"Failed to create index: {e}")

    def _generate_embedding(self, text: str) -> Optional[List[float]]:
        """Генерирует эмбеддинг через Ollama API"""
        try:
            response = requests.post(
                f"http://{self.ollama_host}:{self.ollama_port}/api/embeddings",
                json={
                    "model": self.ollama_embedding_model,
                    "prompt": text
                },
                timeout=10
            )
            if response.status_code == 200:
                return response.json()["embedding"]
            else:
                logger.warning(f"Ollama embedding error: {response.status_code}")
                return None
        except Exception as e:
            logger.error(f"Ollama embedding failed: {e}")
            return None

    def add_format_examples(self, examples: List[Dict]) -> int:
        """Добавляет примеры форматов с векторами"""
        if not self.client:
            logger.warning("OpenSearch not available, skipping example storage")
            return 0

        actions = []
        current_time = datetime.now().isoformat()

        for example in examples:
            text = example.get('text', '')
            if not text:
                continue

            doc_id = hashlib.md5(f"{text}_{example.get('language', 'ru')}".encode()).hexdigest()

            vector = self._generate_embedding(text)

            action = {
                "_index": self.index_name,
                "_id": doc_id,
                "_source": {
                    "text": text,
                    "format_type": example.get('format_type', 'unspecified'),
                    "language": example.get('language', 'ru'),
                    "time_expression": example.get('time_expression', ''),
                    "entities": example.get('entities', []),
                    "examples_count": example.get('count', 1),
                    "created_at": current_time,
                    "updated_at": current_time,
                    "source": example.get('source', 'ollama_agent')
                }
            }

            if vector:
                action["_source"]["vector"] = vector
                actions.append(action)
            else:
                logger.debug(f"Skipping {text[:30]}... no embedding")

        if not actions:
            return 0

        try:
            success, failed = bulk(
                self.client,
                actions,
                raise_on_error=False,
                request_timeout=60,
                refresh=True
            )
            logger.info(f"✅ Indexed {success} format examples with Ollama embeddings")
            return success
        except Exception as e:
            logger.error(f"Bulk indexing error: {e}")
            return 0

    def search_similar_by_text(self, text: str, k: int = 5,
                               language: Optional[str] = None,
                               min_score: float = 0.5) -> List[Dict]:
        """Поиск похожих форматов по тексту с векторами"""
        if not self.client:
            return []

        query_vector = self._generate_embedding(text)

        if not query_vector:
            logger.warning("No embedding available, using text search")
            return self._text_search(text, k, language)

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
                        "must": [{"knn": knn_query}],
                        "filter": [{"term": {"language": language}}]
                    }
                },
                "min_score": min_score
            }
        else:
            query = {
                "size": k,
                "query": {"knn": knn_query},
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

            logger.info(f"🔍 Vector search found {len(results)} similar formats")
            return results

        except Exception as e:
            logger.error(f"Vector search error: {e}")
            return self._text_search(text, k, language)

    def _text_search(self, text: str, k: int = 5,
                     language: Optional[str] = None) -> List[Dict]:
        """Текстовый поиск (fallback)"""
        try:
            if language:
                query = {
                    "size": k,
                    "query": {
                        "bool": {
                            "must": [{"match": {"text": text}}],
                            "filter": [{"term": {"language": language}}]
                        }
                    }
                }
            else:
                query = {
                    "size": k,
                    "query": {"match": {"text": text}}
                }

            response = self.client.search(index=self.index_name, body=query)

            results = []
            for hit in response['hits']['hits']:
                results.append({
                    'text': hit['_source']['text'],
                    'format_type': hit['_source']['format_type'],
                    'language': hit['_source']['language'],
                    'score': hit['_score'],
                    'time_expression': hit['_source'].get('time_expression', '')
                })

            logger.info(f"Text search found {len(results)} similar formats")
            return results

        except Exception as e:
            logger.error(f"Text search error: {e}")
            return []

    def get_format_statistics(self) -> Dict:
        """Статистика по форматам"""
        if not self.client:
            return {}

        try:
            count = self.client.count(index=self.index_name)['count']
            return {'total_documents': count}
        except Exception as e:
            logger.error(f"Statistics error: {e}")
            return {}