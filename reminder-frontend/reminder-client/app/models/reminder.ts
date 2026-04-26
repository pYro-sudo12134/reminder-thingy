export interface Reminder {
    id: string;
    userId: string;
    userEmail: string;
    originalText: string;
    actDescription: string;
    scheduledTime: string;
    createdAt: string;
    status: string;
    notificationSent: boolean;
    intent?: string;
    eventBridgeRuleName?: string;
}
