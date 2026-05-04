export default function translateStatus(status: string | undefined): string {
  switch (status?.toLowerCase()) {
    case 'scheduled': return 'Scheduled';
    case 'completed': return 'Completed';
    case 'cancelled': return 'Cancelled';
    default: return status || '—';
  }
}