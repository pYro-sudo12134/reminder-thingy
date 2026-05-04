"use client";
import { useState } from 'react';
import { Paper, TextInput, Button, Text, Stack, ActionIcon } from '@mantine/core';
import { IconCopy, IconCheck } from '@tabler/icons-react';

interface TelegramBindCardProps {
  cardTitle: string;
  buttonText: string;
  onGenerateClick: () => Promise<void>;
  code: string | null;
  loading?: boolean;
}

export function TelegramBindCard({
  cardTitle,
  buttonText,
  onGenerateClick,
  code,
  loading = false
}: TelegramBindCardProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    if (!code) return;
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <Paper
      radius="md"
      withBorder
      p="lg"
      bg="var(--mantine-color-body)"
      style={{
        minWidth: '300px',
        maxWidth: '340px',
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        boxSizing: 'border-box',
        marginTop: 'auto'
      }}
    >
      <Text ta="center" fz="lg" fw={500} mb="md">
        {cardTitle}
      </Text>
      <Stack gap="md" style={{ marginTop: 'auto' }}>
        <TextInput
          label="Binding Code"
          placeholder="Press button below to generate"
          value={code || ''}
          readOnly
          rightSection={
            code ? (
              <ActionIcon
                color={copied ? 'teal' : 'blue'}
                variant="subtle"
                onClick={handleCopy}
              >
                {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
              </ActionIcon>
            ) : null
          }
        />
        <Button
          fullWidth
          loading={loading}
          onClick={onGenerateClick}
        >
          {buttonText}
        </Button>
      </Stack>
    </Paper>
  );
}