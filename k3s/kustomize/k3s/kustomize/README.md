# Kustomize Configuration for Voice Reminder

## Structure

- `base/` - базовые манифесты с плейсхолдерами
- `overlays/` - настройки для разных окружений (dev/staging/prod)
- `components/` - переиспользуемые компоненты

## Usage

### Подготовка секретов
```bash
cd k3s
./generate-secrets.sh  # генерирует сертификаты и пароли
cd kustomize
./scripts/generate-secrets-k8s.sh  # создает kustomize файлы
