
import { Badge, Modal, Stack, Text } from "@mantine/core";
import { ReminderItem } from "../cards/ReminderCard";
import formatDateTime from "@/app/utility/LocaleTimeTranslator";
import translateStatus from "@/app/utility/ReminderStatusTranslator";

interface Props {
    isOpen: boolean;
    handleClose: () => void;
    values: ReminderItem;
}

export function ReminderInfoModal({ isOpen, handleClose, values }: Props) {
    return (
        <Modal.Root opened={isOpen} onClose={handleClose} trapFocus={false}>
            <Modal.Overlay />
            <Modal.Content>
                <Modal.Header style={{ position: "relative", padding: '4%', paddingBottom: '2%', borderBottom: '1px solid var(--mantine-color-gray-3)' }}>
                    <Stack>
                        <div style={{ marginBottom: '8px' }}>
                            <Modal.Title style={{ whiteSpace: "normal", wordBreak: "break-word" }}>
                                {values.actDescription}
                            </Modal.Title>
                        </div>
                    </Stack>
                </Modal.Header>

                <Modal.Body style={{ padding: '4%', paddingTop: '2%' }}>
                    <Stack gap="md">
                        <div>
                            <Text fw={500} size="sm" c="dimmed">Original record</Text>
                            <Text style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
                                {values.originalText || "—"}
                            </Text>
                        </div>
                        <div>
                            <Text fw={500} size="sm" c="dimmed">Scheduled date and time</Text>
                            <Text fw={600}>
                                {formatDateTime(values.scheduledTime)}
                            </Text>
                        </div>
                        <div>
                            <Text fw={500} size="sm" c="dimmed">Status</Text>
                                {translateStatus(values.status)}
                        </div>
                        <div>
                            <Text fw={500} size="sm" c="dimmed">Created at</Text>
                            <Text fw={600}>
                                {formatDateTime(values.createdAt)}
                            </Text>
                        </div>
                        {/* Всегда будет false, поэтому пока не используем */}
                        {/* <div>
                            <Text fw={500} size="sm" c="dimmed">Notification is sent</Text>
                            <Text fw={600} c={values.notificationSent ? "green" : "gray"}>
                                {values.notificationSent ? "Yes" : "No"}
                            </Text>
                        </div> */} 
                        <div>
                            <Text fw={500} size="sm" c="dimmed">User's email</Text>
                            <Text fw={600}>
                                {values.userEmail}
                            </Text>
                        </div>
                    </Stack>
                </Modal.Body>
            </Modal.Content>
        </Modal.Root>
    );
}


function getStatusColor(status: string): string {
    const normalizedStatus = status?.toLowerCase();
    switch (normalizedStatus) {
        case 'scheduled':
            return 'yellow';
        case 'completed':
            return 'green';
        case 'cancelled':
            return 'red';
        default:
            return 'gray';
    }
}
