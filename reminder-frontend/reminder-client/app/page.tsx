"use client";

import { useEffect, useState } from "react";
import { getUserReminders, ReminderRecord } from "./services/reminders/ReminderService";
import { HeroText } from "./components/HeroText/HeroText";
import { TodaysReminders, TodayReminder } from "./components/Autocomplete/TodaysReminders/TodaysReminders";
import { GetUserLogin, IsAuthenticated } from "./services/users/UserService";
import formatDateTime from "./utility/LocaleTimeTranslator";

function isToday(dateString: string): boolean {
  const date = new Date(dateString.replace(' ', 'T') + 'Z');
  const today = new Date();

  return (
    date.getDate() === today.getDate() &&
    date.getMonth() === today.getMonth() &&
    date.getFullYear() === today.getFullYear()
  );
}

export default function Home() {
  const [todayReminders, setTodayReminders] = useState<TodayReminder[]>([]);
-
  useEffect(() => {
    const fetchTodayReminders = async () => {
      try {
        const isAuthenticated = await IsAuthenticated();
        if (!isAuthenticated) {
          setTodayReminders([]);
          return;
        }

        const user = await GetUserLogin();
        if (!user?.userId) {
        setTodayReminders([]);
        return;
        }
        const data = await getUserReminders(user.userId, 50);
        const today = data.reminders
          .filter((r: ReminderRecord) => isToday(r.scheduledTime))
          .map((r: ReminderRecord) => ({
            id: r.reminderId,
            actDescription: r.extractedAction,
            status: r.status,
            scheduledTime: r.scheduledTime,
          }))
          .slice(0, 5);
        setTodayReminders(today);
      } catch (error) {
        console.error("Failed to fetch today's reminders:", error);
        setTodayReminders([]);
      }
    };
    fetchTodayReminders();
  }, []);

  return (
    <div style={{ minHeight: "100vh", width: "100%", position: "relative" }}>
      <HeroText />
      <TodaysReminders reminders={todayReminders} />
    </div>
  );
}
