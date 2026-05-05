// app/about/page.tsx
"use client";

import { Text, Stack, Group, Anchor, Badge, ThemeIcon, List, Box } from "@mantine/core";
import { IconMicrophone, IconMail, IconBrandTelegram, IconCalendar, IconCheck } from "@tabler/icons-react";
import { usePathname } from "next/navigation";

export default function AboutPage() {
  const pathname = usePathname();

  return (
    // Идентичная обёртка как в reminders/page.tsx (p-6)
    <div className="p-6" style={{ minHeight: "100vh", width: "100%", boxSizing: "border-box" }}>
      <Stack gap="xl" align="flex-start" maw={800}>
        
        {/* Заголовок с теми же классами, что и "Reminders" */}
        <div>
          <h1 className="text-2xl font-bold mb-6">
            About <span style={{ color: "var(--mantine-color-blue-6)" }}>Reminder-Thingy</span>
          </h1>
          <Text c="dimmed" size="lg">
            Your intelligent voice-powered reminder assistant
          </Text>
        </div>

        {/* Основное описание */}
        <Text size="md" lh={1.7} mb="xl">
          <strong>Reminder-Thingy</strong> — это современное приложение, созданное для того, чтобы сделать процесс создания напоминаний максимально простым и естественным. 
          Вместо ручного ввода текста просто <strong>скажите вслух</strong>, что вам нужно запомнить, и наша система автоматически обработает ваш запрос, извлечёт суть и создаст напоминание с указанной датой и временем.
        </Text>

        {/* Ключевые возможности */}
        <Stack gap="md" w="100%" mb="xl">
          <Text size="xl" fw={700}>Key Features</Text>
          
          <List
            spacing="lg"
            size="md"
            icon={
              <ThemeIcon color="teal" size={28} radius="xl">
                <IconCheck size={18} />
              </ThemeIcon>
            }
          >
            <List.Item>
              <Group gap="sm" wrap="nowrap" align="flex-start">
                <IconMicrophone size={22} color="var(--mantine-color-blue-6)" style={{ marginTop: 2 }} />
                <div>
                  <Text fw={600}>Голосовой ввод</Text>
                  <Text c="dimmed" size="sm">Создавайте напоминания голосом — быстро, удобно, без набора текста</Text>
                </div>
              </Group>
            </List.Item>
            
            <List.Item>
              <Group gap="sm" wrap="nowrap" align="flex-start">
                <IconCalendar size={22} color="var(--mantine-color-blue-6)" style={{ marginTop: 2 }} />
                <div>
                  <Text fw={600}>Умное планирование</Text>
                  <Text c="dimmed" size="sm">Система автоматически распознаёт даты и время из вашей речи</Text>
                </div>
              </Group>
            </List.Item>
            
            <List.Item>
              <Group gap="sm" wrap="nowrap" align="flex-start">
                <IconMail size={22} color="var(--mantine-color-blue-6)" style={{ marginTop: 2 }} />
                <div>
                  <Text fw={600}>Email-уведомления</Text>
                  <Text c="dimmed" size="sm">Получайте напоминания на вашу электронную почту в назначенное время</Text>
                </div>
              </Group>
            </List.Item>
            
            <List.Item>
              <Group gap="sm" wrap="nowrap" align="flex-start">
                <IconBrandTelegram size={22} color="var(--mantine-color-blue-6)" style={{ marginTop: 2 }} />
                <div>
                  <Text fw={600}>Telegram-уведомления</Text>
                  <Text c="dimmed" size="sm">
                    Мгновенные уведомления прямо в мессенджер. Подключите бота:{" "}
                    <Anchor 
                      href="https://t.me/voice_reminder_dev_bot" 
                      target="_blank" 
                      rel="noopener noreferrer"
                      fw={500}
                    >
                      @voice_reminder_dev_bot
                    </Anchor>
                  </Text>
                </div>
              </Group>
            </List.Item>
          </List>
        </Stack>

        {/* Блок с Telegram ботом — акцентный */}
        <Box 
          w="100%"
          p="lg" 
          style={{ 
            background: "var(--mantine-color-blue-light)",
            border: "1px solid var(--mantine-color-blue-outline)",
            borderRadius: "var(--mantine-radius-md)",
          }}
        >
          <Group justify="space-between" wrap="nowrap">
            <Stack gap="xs" style={{ flex: 1 }}>
              <Group gap="sm">
                <IconBrandTelegram size={24} color="var(--mantine-color-blue-6)" />
                <Text fw={600}>Подключите Telegram-бота</Text>
              </Group>
              <Text c="dimmed" size="sm">
                Чтобы получать уведомления прямо в чат, перейдите по ссылке и нажмите «Запустить»:
              </Text>
            </Stack>
            <Anchor 
              href="https://t.me/voice_reminder_dev_bot" 
              target="_blank" 
              rel="noopener noreferrer"
            >
              <Badge 
                size="lg" 
                color="blue" 
                variant="filled" 
                rightSection={<IconBrandTelegram size={14} />}
                style={{ cursor: "pointer" }}
              >
                Открыть бота
              </Badge>
            </Anchor>
          </Group>
        </Box>

        {/* Футер — минималистичный */}
        <Text c="dimmed" size="sm" pt="sm">
          Reminder-Thingy © {new Date().getFullYear()}
        </Text>
        
      </Stack>
    </div>
  );
}