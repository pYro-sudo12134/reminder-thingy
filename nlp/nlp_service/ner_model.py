import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from transformers import AutoTokenizer, AutoModelForTokenClassification, AdamW
from seqeval.metrics import classification_report, accuracy_score
import numpy as np
from typing import List, Dict, Tuple
import json
from dataclasses import dataclass
import pickle

@dataclass
class NERConfig:
    model_name: str = "Davlan/bert-base-multilingual-cased-ner-hrl"
    max_length: int = 128
    batch_size: int = 16
    learning_rate: float = 3e-5
    epochs: int = 15
    device: str = "cuda" if torch.cuda.is_available() else "cpu"

    labels = [
        'O',
        'B-ACTION',
        'I-ACTION',
        'B-TIME',
        'I-TIME',
        'B-PERSON',
        'I-PERSON',
        'B-LOC',
        'I-LOC',
        'B-DATE',
        'I-DATE',
        'B-AMOUNT',
        'I-AMOUNT',
        'B-EVENT',
        'I-EVENT',
    ]

    label2id = {label: i for i, label in enumerate(labels)}
    id2label = {i: label for i, label in enumerate(labels)}

class NERDataset(Dataset):
    def __init__(self, texts: List[str], tags: List[List[str]], tokenizer, max_length: int):
        self.texts = texts
        self.tags = tags
        self.tokenizer = tokenizer
        self.max_length = max_length
        self.label2id = NERConfig.label2id

    def __len__(self):
        return len(self.texts)

    def __getitem__(self, idx):
        text = self.texts[idx]
        tags = self.tags[idx]

        encoding = self.tokenizer(
            text,
            truncation=True,
            padding='max_length',
            max_length=self.max_length,
            return_tensors='pt',
            return_offsets_mapping=True,
            return_special_tokens_mask=True
        )

        word_ids = encoding.word_ids()
        previous_word_idx = None
        label_ids = []

        for word_idx in word_ids:
            if word_idx is None:
                label_ids.append(-100)
            elif word_idx != previous_word_idx:
                label = tags[word_idx] if word_idx < len(tags) else 'O'
                label_ids.append(self.label2id.get(label, self.label2id['O']))
            else:
                label_ids.append(-100)

            previous_word_idx = word_idx

        return {
            'input_ids': encoding['input_ids'].flatten(),
            'attention_mask': encoding['attention_mask'].flatten(),
            'labels': torch.tensor(label_ids, dtype=torch.long),
            'offset_mapping': encoding['offset_mapping'].squeeze(),
            'original_text': text
        }

class NERModel:
    def __init__(self, config: NERConfig):
        self.config = config
        self.tokenizer = AutoTokenizer.from_pretrained(config.model_name)

        self.model = AutoModelForTokenClassification.from_pretrained(
            config.model_name,
            num_labels=len(config.labels),
            id2label=config.id2label,
            label2id=config.label2id,
            ignore_mismatched_sizes=True
        )
        self.model.to(config.device)

    def align_predictions(self, predictions: np.ndarray, label_ids: np.ndarray,
                         word_ids: List) -> Tuple[List[str], List[str]]:
        true_labels = []
        pred_labels = []

        for preds, labels, w_ids in zip(predictions, label_ids, word_ids):
            for pred, label, word_id in zip(preds, labels, w_ids):
                if word_id is None or label == -100:
                    continue

                true_labels.append(self.config.id2label[label])
                pred_labels.append(self.config.id2label[pred])

        return true_labels, pred_labels

    def train(self, train_file: str, val_file: str, model_save_path: str):
        train_texts, train_tags = self.load_data(train_file)
        val_texts, val_tags = self.load_data(val_file)

        train_dataset = NERDataset(
            train_texts, train_tags, self.tokenizer, self.config.max_length
        )
        val_dataset = NERDataset(
            val_texts, val_tags, self.tokenizer, self.config.max_length
        )

        train_loader = DataLoader(
            train_dataset,
            batch_size=self.config.batch_size,
            shuffle=True
        )
        val_loader = DataLoader(
            val_dataset,
            batch_size=self.config.batch_size
        )

        optimizer = AdamW(self.model.parameters(), lr=self.config.learning_rate)

        best_f1 = 0

        for epoch in range(self.config.epochs):
            print(f"\nEpoch {epoch + 1}/{self.config.epochs}")

            self.model.train()
            train_loss = 0

            for batch in train_loader:
                input_ids = batch['input_ids'].to(self.config.device)
                attention_mask = batch['attention_mask'].to(self.config.device)
                labels = batch['labels'].to(self.config.device)

                optimizer.zero_grad()

                outputs = self.model(
                    input_ids=input_ids,
                    attention_mask=attention_mask,
                    labels=labels
                )

                loss = outputs.loss
                loss.backward()
                optimizer.step()

                train_loss += loss.item()

            self.model.eval()
            val_loss = 0
            all_true_labels = []
            all_pred_labels = []

            with torch.no_grad():
                for batch in val_loader:
                    input_ids = batch['input_ids'].to(self.config.device)
                    attention_mask = batch['attention_mask'].to(self.config.device)
                    labels = batch['labels'].to(self.config.device)

                    outputs = self.model(
                        input_ids=input_ids,
                        attention_mask=attention_mask,
                        labels=labels
                    )

                    val_loss += outputs.loss.item()

                    predictions = torch.argmax(outputs.logits, dim=2)
                    predictions = predictions.cpu().numpy()
                    label_ids = labels.cpu().numpy()

                    word_ids = []
                    for i in range(input_ids.size(0)):
                        encoding = self.tokenizer(
                            batch['original_text'][i],
                            truncation=True,
                            padding='max_length',
                            max_length=self.config.max_length,
                            return_offsets_mapping=True
                        )
                        word_ids.append(encoding.word_ids())

                    true_labels, pred_labels = self.align_predictions(
                        predictions, label_ids, word_ids
                    )

                    all_true_labels.extend(true_labels)
                    all_pred_labels.extend(pred_labels)

            report = classification_report(
                [all_true_labels], [all_pred_labels], output_dict=True
            )

            avg_train_loss = train_loss / len(train_loader)
            avg_val_loss = val_loss / len(val_loader)

            f1_score = report['weighted avg']['f1-score']

            print(f"Train Loss: {avg_train_loss:.4f}")
            print(f"Val Loss: {avg_val_loss:.4f}")
            print(f"F1 Score: {f1_score:.4f}")
            print(f"Precision: {report['weighted avg']['precision']:.4f}")
            print(f"Recall: {report['weighted avg']['recall']:.4f}")

            if f1_score > best_f1:
                best_f1 = f1_score
                self.model.save_pretrained(f"{model_save_path}/ner_model")
                self.tokenizer.save_pretrained(f"{model_save_path}/ner_model")

                with open(f"{model_save_path}/metrics.json", 'w') as f:
                    json.dump({
                        'epoch': epoch,
                        'train_loss': avg_train_loss,
                        'val_loss': avg_val_loss,
                        'f1_score': f1_score,
                        'report': report
                    }, f, indent=2)

                print(f"Model saved with F1: {f1_score:.4f}")

    def load_data(self, filepath: str) -> Tuple[List[str], List[List[str]]]:
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)

        texts = []
        all_tags = []

        for item in data:
            text = item['text']
            entities = item.get('entities', [])

            tags = ['O'] * len(text.split())

            for entity in entities:
                entity_text = entity['text']
                label = entity['label']
                start = entity['start']
                end = entity['end']

                words = text.split()
                word_start = 0
                word_index = 0

                for i, word in enumerate(words):
                    word_end = word_start + len(word)

                    if start >= word_start and start < word_end:
                        tags[i] = f'B-{label}'
                        word_index = i + 1

                        while word_index < len(words) and end > word_end:
                            tags[word_index] = f'I-{label}'
                            word_index += 1
                            word_end += len(words[word_index]) + 1

                    word_start = word_end + 1

            texts.append(text)
            all_tags.append(tags)

        return texts, all_tags

    def predict(self, text: str, language: str = None):
        encoding = self.tokenizer(
            text,
            truncation=True,
            padding='max_length',
            max_length=self.config.max_length,
            return_tensors='pt',
            return_offsets_mapping=True
        )

        input_ids = encoding['input_ids'].to(self.config.device)
        attention_mask = encoding['attention_mask'].to(self.config.device)

        with torch.no_grad():
            outputs = self.model(input_ids=input_ids, attention_mask=attention_mask)
            predictions = torch.argmax(outputs.logits, dim=2)

        entities = self._convert_to_entities(
            text, predictions[0].cpu().numpy(), encoding['offset_mapping'][0]
        )

        return entities

    def _convert_to_entities(self, text: str, predictions: np.ndarray,
                            offset_mapping: np.ndarray) -> List[Dict]:
        entities = []
        current_entity = None

        for pred, offset in zip(predictions, offset_mapping):
            start, end = offset

            if start == end == 0:
                continue

            label = self.config.id2label[pred]

            if label.startswith('B-'):
                if current_entity:
                    entities.append(current_entity)

                entity_type = label[2:]
                current_entity = {
                    'text': text[start:end],
                    'label': entity_type,
                    'start': start,
                    'end': end,
                    'confidence': 0.9
                }

            elif label.startswith('I-') and current_entity:
                if label[2:] == current_entity['label']:
                    current_entity['text'] += text[start:end]
                    current_entity['end'] = end
                else:
                    entities.append(current_entity)
                    current_entity = None

            elif label == 'O' and current_entity:
                entities.append(current_entity)
                current_entity = None

        if current_entity:
            entities.append(current_entity)

        return entities