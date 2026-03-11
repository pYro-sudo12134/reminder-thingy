# Kustomize Configuration for Voice Reminder

## Structure

- `base/` - базовые манифесты с плейсхолдерами
- `overlays/` - настройки для разных окружений (dev/staging/prod)
- `components/` - переиспользуемые компоненты

## Usage

### Подготовка секретов
```bash
cd /vagrant/kustomize
./scripts/generate-secrets-k3s.sh  # создает kustomize файлы
kubectl apply -k overlays/dev
