"use client";

import formatDateTime from "@/app/utility/LocaleTimeTranslator";
import { Box, List, ThemeIcon, Text } from "@mantine/core";
import { IconCircleCheck, IconCircleDashed, IconCircleDashedX } from "@tabler/icons-react";
import classes from './TodaysReminders.module.css';

export interface TodayReminder {
  id: string;
  actDescription: string;
  status: string;
  scheduledTime: string;
}

interface TodaysRemindersProps {
  reminders: TodayReminder[];
}

function getIconForStatus(status: string) {
  const normalizedStatus = status?.toLowerCase();
  switch (normalizedStatus) {
    case "completed":
      return (
        <ThemeIcon color="green" size={24} radius="xl">
          <IconCircleCheck size={16} />
        </ThemeIcon>
      );
    case "scheduled":
      return (
        <ThemeIcon color="yellow" size={24} radius="xl">
          <IconCircleDashed size={16} />
        </ThemeIcon>
      );
    case "cancelled":
      return (
        <ThemeIcon color="red" size={24} radius="xl">
          <IconCircleDashedX size={16} />
        </ThemeIcon>
      );
    default:
      return (
        <ThemeIcon color="gray" size={24} radius="xl">
          <IconCircleDashed size={16} />
        </ThemeIcon>
      );
  }
}

export function TodaysReminders({ reminders }: TodaysRemindersProps) {
  return (
    <Box
      className={classes.widget}
    >
      <Text size="lg" fw={700} c="text" style={{ marginBottom: "16px", color: "#fff" }}>
        What's Happening Today
      </Text>
      {reminders.length === 0 ? (
        <Text size="sm" style={{ color: "rgba(255, 255, 255, 0.6)" }}>
          No reminders scheduled for today
        </Text>
      ) : (
        <List
          spacing="xs"
          size="sm"
          styles={{
            root: {
              paddingLeft: 0,
            },
            item: {
              // color: "#fff",
              color: "var(--mantine-color-text)",
              fontWeight: 500,
              maxWidth: "100%",
              display: "flex",
              alignItems: "center",
              gap: "4px",
            },
            itemIcon: {
              marginRight: "8px",
            },
          }}
        >
          {reminders.map((reminder) => (
            <List.Item
              key={reminder.id}
              icon={getIconForStatus(reminder.status)}
            >
              <span
                style={{
                  flex: 1,
                  minWidth: 0,
                  display: "inline-flex",
                  alignItems: "center",
                }}
              >
                <span
                  style={{
                    wordBreak: "break-all",
                    display: "-webkit-box",
                    WebkitLineClamp: 1,
                    WebkitBoxOrient: "vertical",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    marginRight: "4px",
                  }}
                >
                  {reminder.actDescription}
                </span>
                <Text
                  component="span"
                  c="dimmed"
                  size="xs"
                  style={{
                    whiteSpace: "nowrap",
                    flexShrink: 0,
                  }}
                >
                  {formatDateTime(reminder.scheduledTime)}
                </Text>
              </span>
            </List.Item>
          ))}
        </List>
      )}
    </Box>
  );
}
