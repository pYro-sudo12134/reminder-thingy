
import { ReminderItem } from "../cards/ReminderCard";
import { ReminderUpdateRequest } from "../../services/reminders/ReminderService";
import { Button, Modal, Stack, TextInput, Select } from "@mantine/core";
import { useEffect, useState } from "react";

interface Props {
    isOpen: boolean;
    values: ReminderItem;
    handleUpdate: (id: string, request: ReminderUpdateRequest) => void;
    handleCancel: () => void;
}

export function ReminderEditModal({ isOpen, values, handleUpdate, handleCancel }: Props) {
    const [originalText, setOriginalText] = useState(values.originalText || "");
    const [extractedAction, setExtractedAction] = useState(values.actDescription || "");
    const [scheduledTime, setScheduledTime] = useState("");
    const [status, setStatus] = useState(values.status || "");
    const [showErrors, setShowErrors] = useState(false);
    const [extractedActionError, setExtractedActionError] = useState("");
    const [scheduledTimeError, setScheduledTimeError] = useState("");

    useEffect(() => {
        setOriginalText(values.originalText);
        setExtractedAction(values.actDescription);
        setStatus(values.status ? values.status.toLowerCase() : "");
        if (values.scheduledTime) {
            
            
            const utcDate = new Date(values.scheduledTime + 'Z');
            const year = utcDate.getFullYear();
            const month = String(utcDate.getMonth() + 1).padStart(2, '0');
            const day = String(utcDate.getDate()).padStart(2, '0');
            const hours = String(utcDate.getHours()).padStart(2, '0');
            const minutes = String(utcDate.getMinutes()).padStart(2, '0');
            setScheduledTime(`${year}-${month}-${day}T${hours}:${minutes}`);
        }
    }, [values]);

    useEffect(() => {
        setShowErrors(false);
        setExtractedActionError("");
        setScheduledTimeError("");
    }, [isOpen]);

    const validateForm = () => {
        let isValid = true;

        if (extractedAction.trim().length === 0) {
            setExtractedActionError("Извлечённое действие обязательно");
            isValid = false;
        } else {
            setExtractedActionError("");
        }

        if (!scheduledTime) {
            setScheduledTimeError("Плановая дата обязательна");
            isValid = false;
        } else {
            setScheduledTimeError("");
        }

        setShowErrors(true);
        return isValid;
    };

    const handleOnOk = () => {
        if (!validateForm()) {
            return;
        }

        
        
        const localDate = new Date(scheduledTime);
        const utcYear = localDate.getUTCFullYear();
        const utcMonth = String(localDate.getUTCMonth() + 1).padStart(2, '0');
        const utcDay = String(localDate.getUTCDate()).padStart(2, '0');
        const utcHours = String(localDate.getUTCHours()).padStart(2, '0');
        const utcMinutes = String(localDate.getUTCMinutes()).padStart(2, '0');
        const scheduledTimeIso = `${utcYear}-${utcMonth}-${utcDay}T${utcHours}:${utcMinutes}:00`;

        const updateRequest: ReminderUpdateRequest = {
            reminderId: values.id,
            extractedAction: extractedAction,
            scheduledTime: scheduledTimeIso,
            status: status ? status.toUpperCase() : undefined,
            userEmail: values.userEmail,
        };

        handleUpdate(values.id, updateRequest);
        setShowErrors(false);
    };

    return (
        <Modal opened={isOpen} title="Редактирование напоминания" onClose={handleCancel}>
            <Stack>
                <TextInput
                    label="Оригинальная запись"
                    value={originalText}
                    onChange={(e) => setOriginalText(e.target.value)}
                    placeholder="Введите оригинальный текст напоминания"
                    disabled
                />
                <TextInput
                    label="Извлечённое действие"
                    value={extractedAction}
                    onChange={(e) => setExtractedAction(e.target.value)}
                    placeholder="Введите извлечённое действие"
                    error={showErrors ? extractedActionError : false}
                />
                <TextInput
                    label="Плановая дата и время"
                    type="datetime-local"
                    value={scheduledTime}
                    onChange={(e) => setScheduledTime(e.target.value)}
                    error={showErrors ? scheduledTimeError : false}
                />
                <Select
                    label="Статус"
                    placeholder="Выберите статус"
                    value={status}
                    onChange={(value) => value && setStatus(value)}
                    data={[
                        { value: "scheduled", label: "Запланировано" },
                        { value: "completed", label: "Выполнено" },
                        { value: "cancelled", label: "Отменено" }
                    ]}
                />
                <Button onClick={handleOnOk}>Обновить напоминание</Button>
            </Stack>
        </Modal>
    );
}
