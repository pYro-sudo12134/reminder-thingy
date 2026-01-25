import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from transformers import AutoTokenizer, AutoModel, AdamW
from sklearn.preprocessing import LabelEncoder
import numpy as np
from typing import List, Dict, Tuple
import json
from dataclasses import dataclass
import pickle

@dataclass
class IntentClassifierConfig:
    model_name: str = "bert-base-multilingual-cased"
    num_labels: int = 6
    max_length: int = 128
    batch_size: int = 16
    learning_rate: float = 2e-5
    epochs: int = 10
    device: str = "cuda" if torch.cuda.is_available() else "cpu"

class IntentDataset(Dataset):
    def __init__(self, texts: List[str], labels: List[int], tokenizer, max_length: int):
        self.texts = texts
        self.labels = labels
        self.tokenizer = tokenizer
        self.max_length = max_length

    def __len__(self):
        return len(self.texts)

    def __getitem__(self, idx):
        text = str(self.texts[idx])
        label = self.labels[idx]

        encoding = self.tokenizer(
            text,
            truncation=True,
            padding='max_length',
            max_length=self.max_length,
            return_tensors='pt'
        )

        return {
            'input_ids': encoding['input_ids'].flatten(),
            'attention_mask': encoding['attention_mask'].flatten(),
            'labels': torch.tensor(label, dtype=torch.long)
        }

class IntentClassifier(nn.Module):
    def __init__(self, config: IntentClassifierConfig):
        super(IntentClassifier, self).__init__()
        self.config = config

        self.bert = AutoModel.from_pretrained(config.model_name)

        for param in self.bert.parameters():
            param.requires_grad = False

        for param in self.bert.encoder.layer[-2:].parameters():
            param.requires_grad = True

        self.dropout = nn.Dropout(0.3)
        self.classifier = nn.Linear(self.bert.config.hidden_size, config.num_labels)

        nn.init.xavier_uniform_(self.classifier.weight)

    def forward(self, input_ids, attention_mask):
        outputs = self.bert(
            input_ids=input_ids,
            attention_mask=attention_mask,
            return_dict=True
        )

        pooled_output = outputs.pooler_output

        pooled_output = self.dropout(pooled_output)
        logits = self.classifier(pooled_output)

        return logits

class IntentClassifierTrainer:
    def __init__(self, config: IntentClassifierConfig):
        self.config = config
        self.tokenizer = AutoTokenizer.from_pretrained(config.model_name)
        self.label_encoder = LabelEncoder()

    def load_data(self, filepath: str) -> Tuple[List[str], List[str]]:
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)

        texts = []
        intents = []

        for item in data:
            texts.append(item['text'])
            intents.append(item['intent'])

        return texts, intents

    def train(self, train_file: str, val_file: str, model_save_path: str):
        train_texts, train_intents = self.load_data(train_file)
        val_texts, val_intents = self.load_data(val_file)

        all_intents = train_intents + val_intents
        self.label_encoder.fit(all_intents)

        train_labels = self.label_encoder.transform(train_intents)
        val_labels = self.label_encoder.transform(val_intents)

        with open(f"{model_save_path}/label_encoder.pkl", 'wb') as f:
            pickle.dump(self.label_encoder, f)

        train_dataset = IntentDataset(
            train_texts, train_labels, self.tokenizer, self.config.max_length
        )
        val_dataset = IntentDataset(
            val_texts, val_labels, self.tokenizer, self.config.max_length
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

        model = IntentClassifier(self.config)
        model.to(self.config.device)

        optimizer = AdamW(model.parameters(), lr=self.config.learning_rate)
        criterion = nn.CrossEntropyLoss()

        best_val_loss = float('inf')

        for epoch in range(self.config.epochs):
            print(f"\nEpoch {epoch + 1}/{self.config.epochs}")

            model.train()
            train_loss = 0
            train_correct = 0

            for batch in train_loader:
                input_ids = batch['input_ids'].to(self.config.device)
                attention_mask = batch['attention_mask'].to(self.config.device)
                labels = batch['labels'].to(self.config.device)

                optimizer.zero_grad()

                outputs = model(input_ids, attention_mask)
                loss = criterion(outputs, labels)

                loss.backward()
                optimizer.step()

                train_loss += loss.item()
                _, predicted = torch.max(outputs, 1)
                train_correct += (predicted == labels).sum().item()

            model.eval()
            val_loss = 0
            val_correct = 0

            with torch.no_grad():
                for batch in val_loader:
                    input_ids = batch['input_ids'].to(self.config.device)
                    attention_mask = batch['attention_mask'].to(self.config.device)
                    labels = batch['labels'].to(self.config.device)

                    outputs = model(input_ids, attention_mask)
                    loss = criterion(outputs, labels)

                    val_loss += loss.item()
                    _, predicted = torch.max(outputs, 1)
                    val_correct += (predicted == labels).sum().item()

            train_acc = train_correct / len(train_dataset)
            val_acc = val_correct / len(val_dataset)

            avg_train_loss = train_loss / len(train_loader)
            avg_val_loss = val_loss / len(val_loader)

            print(f"Train Loss: {avg_train_loss:.4f}, Train Acc: {train_acc:.4f}")
            print(f"Val Loss: {avg_val_loss:.4f}, Val Acc: {val_acc:.4f}")

            if avg_val_loss < best_val_loss:
                best_val_loss = avg_val_loss
                torch.save({
                    'epoch': epoch,
                    'model_state_dict': model.state_dict(),
                    'optimizer_state_dict': optimizer.state_dict(),
                    'val_loss': avg_val_loss,
                    'val_acc': val_acc,
                }, f"{model_save_path}/best_model.pt")
                print(f"Model saved with val loss: {avg_val_loss:.4f}")

        self.tokenizer.save_pretrained(model_save_path)

class IntentClassifierInference:
    def __init__(self, model_path: str, device: str = None):
        if device is None:
            self.device = "cuda" if torch.cuda.is_available() else "cpu"
        else:
            self.device = device

        self.config = IntentClassifierConfig()
        self.config.device = self.device

        self.model = IntentClassifier(self.config)

        checkpoint = torch.load(
            f"{model_path}/best_model.pt",
            map_location=self.device
        )
        self.model.load_state_dict(checkpoint['model_state_dict'])
        self.model.to(self.device)
        self.model.eval()

        self.tokenizer = AutoTokenizer.from_pretrained(model_path)

        with open(f"{model_path}/label_encoder.pkl", 'rb') as f:
            self.label_encoder = pickle.load(f)

    def predict(self, text: str, language: str = None):
        encoding = self.tokenizer(
            text,
            truncation=True,
            padding='max_length',
            max_length=self.config.max_length,
            return_tensors='pt'
        )

        input_ids = encoding['input_ids'].to(self.device)
        attention_mask = encoding['attention_mask'].to(self.device)

        with torch.no_grad():
            outputs = self.model(input_ids, attention_mask)
            probabilities = torch.softmax(outputs, dim=1)
            predicted_class = torch.argmax(probabilities, dim=1)

        intent = self.label_encoder.inverse_transform([predicted_class.item()])[0]
        confidence = probabilities[0][predicted_class].item()

        all_probabilities = {}
        for i, label in enumerate(self.label_encoder.classes_):
            all_probabilities[label] = probabilities[0][i].item()

        return {
            'intent': intent,
            'confidence': confidence,
            'probabilities': all_probabilities,
            'language': language
        }

    def predict_batch(self, texts: List[str]):
        results = []
        for text in texts:
            results.append(self.predict(text))
        return results