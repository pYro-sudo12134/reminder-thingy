export default function translateStatus(status: string | undefined): string {
  switch (status?.toLowerCase()) {
    case 'scheduled': return 'Запланировано';
    case 'completed': return 'Выполнено';
    case 'cancelled': return 'Отменено';
    default: return status || '—';
  }
}