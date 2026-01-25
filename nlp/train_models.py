import os
import json
import torch
import sys
from datetime import datetime

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

try:
    from nlp_service.intent_classifier import IntentClassifierTrainer, IntentClassifierConfig
    from nlp_service.ner_model import NERModel, NERConfig
except ImportError as e:
    print(f"Import error: {e}")
    print("Check that model files are in nlp_service/")
    sys.exit(1)

def check_dataset():
    print("Checking dataset...")

    required_files = ['data/train_dataset.json', 'data/val_dataset.json']
    for file in required_files:
        if not os.path.exists(file):
            print(f"File not found: {file}")
            return False
        print(f"Found: {file}")

    with open('data/train_dataset.json', 'r', encoding='utf-8') as f:
        train_data = json.load(f)

    with open('data/val_dataset.json', 'r', encoding='utf-8') as f:
        val_data = json.load(f)

    print(f"  Training examples: {len(train_data)}")
    print(f"  Validation examples: {len(val_data)}")

    sample = train_data[0]
    print(f"  Example structure: {list(sample.keys())}")

    return True

def create_small_dataset():
    print("\nCreating test dataset...")

    with open('data/train_dataset.json', 'r', encoding='utf-8') as f:
        train_data = json.load(f)

    with open('data/val_dataset.json', 'r', encoding='utf-8') as f:
        val_data = json.load(f)

    small_train = train_data[:200]
    small_val = val_data[:50]

    os.makedirs('data/test', exist_ok=True)
    with open('data/test/train_small.json', 'w', encoding='utf-8') as f:
        json.dump(small_train, f, ensure_ascii=False, indent=2)

    with open('data/test/val_small.json', 'w', encoding='utf-8') as f:
        json.dump(small_val, f, ensure_ascii=False, indent=2)

    print(f"Created {len(small_train)} training and {len(small_val)} validation examples")

def train_intent_classifier():
    print("\nTraining intent classifier...")

    os.makedirs('models/intent_classifier', exist_ok=True)

    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Using device: {device}")

    config = IntentClassifierConfig(
        model_name="bert-base-multilingual-cased",
        num_labels=6,
        batch_size=8 if device == "cuda" else 4,
        epochs=15,
        learning_rate=2e-5,
        device=device
    )

    trainer = IntentClassifierTrainer(config)

    print("Starting training...")
    start_time = datetime.now()

    trainer.train(
        train_file='data/test/train_small.json',
        val_file='data/test/val_small.json',
        model_save_path='models/intent_classifier'
    )

    elapsed = datetime.now() - start_time
    print(f"Training completed in {elapsed.total_seconds():.1f} seconds")

def test_intent_classifier():
    print("\nTesting classifier...")

    try:
        from nlp_service.intent_classifier import IntentClassifierInference

        if not os.path.exists('models/intent_classifier/best_model.pt'):
            print("Model not found")
            return

        device = "cuda" if torch.cuda.is_available() else "cpu"
        classifier = IntentClassifierInference('models/intent_classifier', device=device)

        test_cases = [
            ("Remind me to buy milk tomorrow", "en"),
            ("Meeting with team tomorrow", "en"),
            ("Need to send report in an hour", "en"),
            ("Call mom in the evening", "en"),
            ("Maria's birthday on Saturday", "en"),
            ("Finish the report by Friday", "en")
        ]

        print("\nTest results:")
        print("-" * 60)
        for text, lang in test_cases:
            try:
                result = classifier.predict(text, language=lang)
                print(f"'{text[:40]}...'")
                print(f"  → Intent: {result['intent']}")
                print(f"  → Confidence: {result['confidence']:.2%}")
                print()
            except Exception as e:
                print(f"Error processing '{text}': {e}")

    except Exception as e:
        print(f"Test error: {e}")

def main():
    print("=" * 60)
    print("TRAINING NLP MODELS FOR REMINDER PARSER")
    print("=" * 60)

    if not check_dataset():
        print("\nFirst create dataset:")
        print("   python data_preparation/create_dataset.py")
        sys.exit(1)

    create_small_dataset()

    train_intent_classifier()

    test_intent_classifier()

    print("\n" + "=" * 60)
    print("TEST TRAINING COMPLETED!")
    print("=" * 60)
    print("\nNow you can run server:")
    print("  python run_server.py")
    print("\nOr test client:")
    print("  python test_client.py")

if __name__ == '__main__':
    main()