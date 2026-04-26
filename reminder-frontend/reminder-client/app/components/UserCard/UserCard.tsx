import { Avatar, Button, Paper, Text } from '@mantine/core';
import { IconUser } from '@tabler/icons-react';

interface UserCardProps {
  name: string;
  email: string;
  position: string;
  onChangeAccount?: () => void;
}

export function UserCard({ name, email, position, onChangeAccount }: UserCardProps) {
  return (
    <Paper radius="md" withBorder p="lg" bg="var(--mantine-color-body)" maw={340} style={{ margin: '0' }}>
      <Avatar
        size={120}
        radius={120}
        mx="auto"
        alt="User"
      >
        <IconUser size={60} />
      </Avatar>
      <Text ta="center" fz="lg" fw={500} mt="md">
        {name}
      </Text>
      <Text ta="center" c="dimmed" fz="sm">
        {email} • {position}
      </Text>

      <Button
        component="a"
        href="/users/login?authenticationType=login"
        onClick={onChangeAccount}
        variant="default"
        fullWidth
        mt="md"
      >
        Change Account
      </Button>
    </Paper>
  );
}
