
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
                            <Text fw={500} size="sm" c="dimmed">Оригинальная запись</Text>
                            <Text style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
                                {values.originalText || "—"}
                            </Text>
                        </div>
                        <div>
                            <Text fw={500} size="sm" c="dimmed">Плановая дата и время</Text>
                            <Text fw={600}>
                                {formatDateTime(values.scheduledTime)}
                            </Text>
                        </div>
                        <div>
                            <Text fw={500} size="sm" c="dimmed">Статус</Text>
                                {translateStatus(values.status)}
                        </div>
                        <div>
                            <Text fw={500} size="sm" c="dimmed">Дата создания</Text>
                            <Text fw={600}>
                                {formatDateTime(values.createdAt)}
                            </Text>
                        </div>
                        <div>
                            <Text fw={500} size="sm" c="dimmed">Уведомление отправлено</Text>
                            <Text fw={600} c={values.notificationSent ? "green" : "gray"}>
                                {values.notificationSent ? "Да" : "Нет"}
                            </Text>
                        </div>
                        <div>
                            <Text fw={500} size="sm" c="dimmed">Email пользователя</Text>
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
