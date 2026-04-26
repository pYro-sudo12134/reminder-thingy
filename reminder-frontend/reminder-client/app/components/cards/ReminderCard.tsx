import { ActionIcon, Badge, Group, Text } from '@mantine/core';
import { IconGripVertical, IconEye, IconPencil, IconTrash } from '@tabler/icons-react';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import cx from 'clsx';
import classes from './ReminderList.module.css';
import formatDateTime from '@/app/utility/LocaleTimeTranslator';

export interface ReminderItem {
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

interface ReminderCardProps {
    item: ReminderItem;
    onOpen: () => void;
    onEdit: () => void;
    onDelete: () => void;
}


function getBadgeColor(status: string): string {
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

export function ReminderCard({ item, onOpen, onEdit, onDelete }: ReminderCardProps) {
    const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
        id: item.id,
    });

    const style: React.CSSProperties = {
        transform: CSS.Transform.toString(transform),
        transition,
    };

    return (
        <div
            className={cx(classes.item, { [classes.itemDragging]: isDragging })}
            ref={setNodeRef}
            style={style}
            {...attributes}
        >
            <div className={classes.dragHandle} {...listeners}>
                <IconGripVertical size={18} stroke={1.5} />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
                <Text
                    style={{
                        wordBreak: 'break-all',
                        display: '-webkit-box',
                        WebkitLineClamp: 1,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        maxWidth:'50%'
                    }}
                >
                    {item.actDescription}
                </Text>
                <Text c="dimmed" size="sm">
                    Плановая дата: {formatDateTime(item.scheduledTime)}
                </Text>
            </div>
            <Group gap="xs">
                <Group gap="xs" className={classes.actions}>
                    <ActionIcon variant="light" color="blue" size="lg" aria-label="Open" onClick={onOpen}>
                        <IconEye size={18} stroke={1.5} />
                    </ActionIcon>
                    <ActionIcon variant="light" color="yellow" size="lg" aria-label="Edit" onClick={onEdit}>
                        <IconPencil size={18} stroke={1.5} />
                    </ActionIcon>
                    <ActionIcon variant="light" color="red" size="lg" aria-label="Delete" onClick={onDelete}>
                        <IconTrash size={18} stroke={1.5} />
                    </ActionIcon>
                </Group>
                <Badge
                    color={getBadgeColor(item.status)}
                    variant="filled"
                >
                    {item.status}
                </Badge>
            </Group>
        </div>
    );
}
