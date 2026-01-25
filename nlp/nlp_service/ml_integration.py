import torch
import os
from typing import Dict, List, Optional
from datetime import datetime
import numpy as np

from .intent_classifier import IntentClassifierInference, IntentClassifierConfig
from .ner_model import NERModel, NERConfig
from .model import ReminderNLPModel, ParsedReminder, TimeExpression, ReminderType

class MLEnhancedReminderParser:
    def __init__(self, model_dir: str = None):
        self.base_parser = ReminderNLPModel(model_dir)
        self.intent_classifier = None
        self.ner_model = None
        self.ml_models_loaded = False

    def load_ml_models(self, intent_model_path: str, ner_model_path: str = None):
        print(f"Loading ML models...")
        print(f"Intent model path: {intent_model_path}")
        print(f"NER model path: {ner_model_path}")

        try:
            self.intent_classifier = IntentClassifierInference(
                intent_model_path,
                device="cuda" if torch.cuda.is_available() else "cpu"
            )
            print(f"Intent classifier loaded from {intent_model_path}")

            if ner_model_path:
                try:
                    self.ner_model = NERModel(NERConfig())
                    print(f"Trying to load NER model from {ner_model_path}")

                    if os.path.exists(ner_model_path):
                        from transformers import AutoModelForTokenClassification, AutoTokenizer
                        self.ner_model.model = AutoModelForTokenClassification.from_pretrained(ner_model_path)
                        self.ner_model.tokenizer = AutoTokenizer.from_pretrained(ner_model_path)
                        self.ner_model.model.to(self.ner_model.config.device)
                        self.ner_model.model.eval()
                        print(f"NER model loaded from {ner_model_path}")
                    else:
                        print(f"NER model not found at: {ner_model_path}")
                        self.ner_model = None
                except Exception as e:
                    print(f"Error loading NER model: {e}")
                    import traceback
                    traceback.print_exc()
                    self.ner_model = None
            else:
                print(f"NER model not provided")
                self.ner_model = None

            self.ml_models_loaded = bool(self.intent_classifier)
            print(f"ML models loaded: {self.ml_models_loaded}")
            print(f"Intent classifier: {self.intent_classifier is not None}")
            print(f"NER model: {self.ner_model is not None}")

        except Exception as e:
            print(f"Error loading ML models: {e}")
            import traceback
            traceback.print_exc()
            self.ml_models_loaded = False

    def parse_with_ml(self, text: str, language: str = None) -> ParsedReminder:
        if not self.ml_models_loaded or not self.intent_classifier:
            return self.base_parser.parse(text, language)

        if not language:
            language = self.base_parser.detect_language(text)

        normalized_text = self.base_parser.preprocess(text, language)

        try:
            intent_result = self.intent_classifier.predict(normalized_text, language)
            ml_intent = intent_result['intent']
            intent_confidence = intent_result['confidence']
        except Exception as e:
            print(f"Intent prediction error: {e}")
            return self.base_parser.parse(text, language)

        base_result = self.base_parser.parse(text, language)
        base_result.intent = ml_intent

        ner_entities = []
        if self.ner_model:
            try:
                ner_entities = self.ner_model.predict(normalized_text, language)
            except Exception as e:
                print(f"NER prediction error: {e}")
                ner_entities = base_result.entities

        base_result.entities = ner_entities
        base_result.confidence = self._calculate_ml_confidence(
            intent_confidence,
            base_result.time_expression.confidence,
            len(ner_entities)
        )

        return base_result

    def _calculate_ml_confidence(self, intent_conf: float, time_conf: float,
                               num_entities: int) -> float:
        base_confidence = (intent_conf + time_conf) / 2

        if num_entities > 0:
            entity_bonus = min(0.2, num_entities * 0.05)
            base_confidence += entity_bonus

        return min(max(base_confidence, 0.1), 0.99)

    def parse(self, text: str, language: str = None) -> ParsedReminder:
        if self.ml_models_loaded:
            return self.parse_with_ml(text, language)
        else:
            return self.base_parser.parse(text, language)