import { IconCheck } from '@tabler/icons-react';
import { Notification } from '@mantine/core';

interface Props {
  message: string;
  onClose: () => void;
}

export default function SuccessNotification(props: Props) {
  return (
    <Notification icon={<IconCheck size={20} />} color="teal" title="Success!" onClose={props.onClose}>
      {props.message}
    </Notification>
  );
}