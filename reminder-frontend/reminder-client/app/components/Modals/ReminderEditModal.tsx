
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
            setExtractedActionError("Extracted action is required");
            isValid = false;
        } else {
            setExtractedActionError("");
        }

        if (!scheduledTime) {
            setScheduledTimeError("Target date is required");
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
        <Modal opened={isOpen} title="Edit Reminder" onClose={handleCancel}>
            <Stack>
                <TextInput
                    label="Original record"
                    value={originalText}
                    onChange={(e) => setOriginalText(e.target.value)}
                    placeholder="Enter original record"
                    disabled
                />
                <TextInput
                    label="Extracted action"
                    value={extractedAction}
                    onChange={(e) => setExtractedAction(e.target.value)}
                    placeholder="Enter extracted action"
                    error={showErrors ? extractedActionError : false}
                />
                <TextInput
                    label="Target date and time"
                    type="datetime-local"
                    value={scheduledTime}
                    onChange={(e) => setScheduledTime(e.target.value)}
                    error={showErrors ? scheduledTimeError : false}
                />
                <Select
                    label="Status"
                    placeholder="Select status"
                    value={status}
                    onChange={(value) => value && setStatus(value)}
                    data={[
                        { value: "scheduled", label: "Scheduled" },
                        { value: "completed", label: "Completed" },
                        { value: "cancelled", label: "Cancelled" }
                    ]}
                />
                <Button onClick={handleOnOk}>Update Reminder</Button>
            </Stack>
        </Modal>
    );
}
