import { ReminderItem } from "../../components/cards/ReminderCard";

export type ReminderStatus = "SCHEDULED" | "CANCELLED" | "COMPLETED";

export interface ReminderRecord {
  reminderId: string;
  userId: string;
  userEmail: string;
  originalText: string;
  extractedAction: string;
  scheduledTime: string; 
  createdAt: string; 
  status: ReminderStatus;
  notificationSent: boolean;
  intent?: string;
  eventBridgeRuleName?: string;
}

export interface UserRemindersResponse {
  userId: string;
  total: number;
  filtered: number;
  reminders: ReminderRecord[];
  timestamp: string;
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8090";

export const getReminder = async (id: string): Promise<ReminderRecord> => {
  const url = `${API_BASE_URL}/api/reminders/api/reminder/${id}`;
  const response = await fetch(url, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`HTTP Error: ${response.status}`);
  }

  return response.json();
};

export const getUserReminders = async (
  userId: string,
  limit: number = 10,
  statusFilter?: string,
): Promise<UserRemindersResponse> => {
  let url = `${API_BASE_URL}/api/reminders/user/${userId}/reminders?limit=${limit}`;
  if (statusFilter) {
    url += `&status=${statusFilter}`;
  }
  const response = await fetch(url, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`HTTP Error: ${response.status}`);
  }

  return response.json();
};

export const getReminderTranscription = async (
  reminderId: string,
): Promise<{
  reminderId: string;
  transcription: string;
  source: string;
  confidence?: number;
  language?: string;
  duration?: number;
  completedAt?: string;
  message?: string;
}> => {
  const url = `${API_BASE_URL}/api/reminders/reminder/${reminderId}/transcription`;
  const response = await fetch(url, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`HTTP Error: ${response.status}`);
  }

  return response.json();
};

export const getUserStats = async (
  userId: string,
): Promise<Record<string, unknown> & { userId: string; timestamp: string }> => {
  const url = `${API_BASE_URL}/api/reminders/stats/${userId}`;
  const response = await fetch(url, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`HTTP Error: ${response.status}`);
  }

  return response.json();
};

export const autocompleteReminders = async (
  userId: string,
  query: string,
  limit: number = 5,
): Promise<{
  userId: string;
  query: string;
  total: number;
  suggestions: Array<{
    reminderId: string;
    action: string;
    text: string;
    score: number;
    scheduledTime?: string;
  }>;
  timestamp: string;
}> => {
  const url = `${API_BASE_URL}/api/reminders/autocomplete?userId=${userId}&query=${query}&limit=${limit}`;
  const response = await fetch(url, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`HTTP Error: ${response.status}`);
  }

  return response.json();
};

export const deleteReminder = async (
  id: string,
): Promise<{ reminderId: string; deleted: boolean; timestamp: string }> => {
  const url = `${API_BASE_URL}/api/reminders/reminder/${id}`;
  const response = await fetch(url, {
    method: "DELETE",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`HTTP Error: ${response.status}`);
  }

  return response.json();
};

export interface ReminderUpdateRequest {
  reminderId: string; 
  extractedAction?: string;
  scheduledTime?: string; 
  status?: string; 
  userEmail?: string;
}

export const updateReminder = async (
  id: string,
  data: ReminderUpdateRequest,
): Promise<{ reminderId: string; updated: boolean; timestamp: string }> => {
  const url = `${API_BASE_URL}/api/reminders/reminder/${id}`;
  const response = await fetch(url, {
    method: "PUT",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      reminderId: data.reminderId,
      extractedAction: data.extractedAction,
      scheduledTime: data.scheduledTime,
      status: data.status,
      userEmail: data.userEmail,
    }),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      `HTTP ${response.status}: ${error.error || response.statusText}`,
    );
  }
  return response.json();
};

export const cancelReminder = async (
  id: string,
): Promise<{ reminderId: string; cancelled: boolean; timestamp: string }> => {
  const url = `${API_BASE_URL}/api/reminders/reminder/${id}/cancel`;
  const response = await fetch(url, {
    method: "POST",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`HTTP Error: ${response.status}`);
  }

  return response.json();
};

export const recordVoiceReminder = async (
  userId: string,
  userEmail: string,
  audioBlob: Blob,
): Promise<{
  reminderId: string;
  userId: string;
  userEmail: string;
  status: string;
  message: string;
  timestamp: string;
}> => {
  const url = `${API_BASE_URL}/api/reminders/reminder/record`;
  const formData = new FormData();
  formData.append("userId", userId);
  formData.append("userEmail", userEmail);
  formData.append("audio", audioBlob, "recording.wav");

  const response = await fetch(url, {
    method: "POST",
    credentials: "include",
    body: formData,
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      `HTTP ${response.status}: ${error.error || response.statusText}`,
    );
  }

  return response.json();
};
