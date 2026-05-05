export default function formatDateTime(date: Date | string): string {
    
    
    const dateStr = typeof date === 'string' ? date : date.toISOString();
    const utcDate = new Date(dateStr.includes('Z') || dateStr.includes('+') ? dateStr : dateStr + 'Z');
    return utcDate.toLocaleString("ru-RU", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        timeZone: 'UTC'
    });
}