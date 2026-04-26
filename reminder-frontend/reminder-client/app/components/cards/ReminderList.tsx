import {
    closestCenter,
    DndContext,
    DragEndEvent,
    KeyboardSensor,
    PointerSensor,
    useSensor,
    useSensors,
} from '@dnd-kit/core';
import {
    arrayMove,
    SortableContext,
    verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { useListState } from '@mantine/hooks';
import { ReminderCard, ReminderItem } from './ReminderCard';
import classes from './ReminderList.module.css';
import { useEffect } from 'react';

interface ReminderListProps {
    items: ReminderItem[];
    onReorder?: (items: ReminderItem[]) => void;
    onOpen?: (item: ReminderItem) => void;
    onEdit?: (item: ReminderItem) => void;
    onDelete?: (item: ReminderItem) => void;
}

export function ReminderList({ items: initialItems, onReorder, onOpen, onEdit, onDelete }: ReminderListProps) {
    const [state, handlers] = useListState(initialItems);

    useEffect(() => {
        handlers.setState(initialItems);
    }, [initialItems]);

    const sensors = useSensors(
        useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
        useSensor(KeyboardSensor)
    );

    const handleDragEnd = (event: DragEndEvent) => {
        const { active, over } = event;
        if (!over || active.id === over.id) {
            return;
        }
        const oldIndex = state.findIndex((i) => i.id === active.id);
        const newIndex = state.findIndex((i) => i.id === over.id);
        const newState = arrayMove(state, oldIndex, newIndex);
        handlers.setState(newState);
        onReorder?.(newState);
    };

    return (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
            <SortableContext items={state.map((i) => i.id)} strategy={verticalListSortingStrategy}>
                <div className={classes.list}>
                    {state.map((item) => (
                        <ReminderCard
                            key={item.id}
                            item={item}
                            onOpen={() => onOpen?.(item)}
                            onEdit={() => onEdit?.(item)}
                            onDelete={() => onDelete?.(item)}
                        />
                    ))}
                </div>
            </SortableContext>
        </DndContext>
    );
}
