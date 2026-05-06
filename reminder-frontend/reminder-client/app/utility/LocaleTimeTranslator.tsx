export default function formatDateTime(date: Date | string): string {
    const dateStr = typeof date === 'string' ? date : date.toISOString();

    let parsedDate: Date;
    if (dateStr.includes('Z') || dateStr.includes('+') || dateStr.includes('-', 10)) {
        parsedDate = new Date(dateStr);
    } else {
        parsedDate = new Date(dateStr.replace(' ', 'T') + 'Z');
    }

    if (isNaN(parsedDate.getTime())) {
        return dateStr;
    }

    return parsedDate.toLocaleString("ru-RU", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        timeZone: 'Europe/Moscow',
    });
}