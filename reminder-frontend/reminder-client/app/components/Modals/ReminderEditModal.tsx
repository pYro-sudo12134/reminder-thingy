
import { ReminderItem } from "../cards/ReminderCard";
import { ReminderUpdateRequest } from "../../services/reminders/ReminderService";
import { Button, Modal, Stack, TextInput, Select, Transition } from "@mantine/core";
import { useEffect, useState } from "react";
import ErrorNotification from "../Notifications/ErrorNotification";

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

    const [showPopupError, setShowPopupError] = useState(false);
    const [popupErrorMessage, setPopupErrorMessage] = useState("");


    useEffect(() => {
        setOriginalText(values.originalText);
        setExtractedAction(values.actDescription);
        setStatus(values.status ? values.status.toLowerCase() : "");
        if (values.scheduledTime) {
            const date = new Date(values.scheduledTime);
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');
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

            const selectedDate = new Date(scheduledTime);
            const now = new Date();
            if (selectedDate.getTime() < now.getTime()) {
                setPopupErrorMessage("Date cannot be in the past.");
                setShowPopupError(true);
                isValid = false;
            }
        }

        setShowErrors(true);
        return isValid;
    };

    const handleOnOk = () => {
        if (!validateForm()) {
            return;
        }

        const scheduledTimeIso = `${scheduledTime}:00`;

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
         <Transition mounted={showPopupError} transition="slide-down" duration={300} timingFunction="ease">
        {(styles) => (
          <div style={{
            position: 'fixed',
            top: '24px',
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 10000,
            width: '90%',
            maxWidth: '500px',
          }}>
            <div style={styles}>
              <ErrorNotification
                message={popupErrorMessage}
                onClose={() => setShowPopupError(false)}
              />
            </div>
          </div>
        )}
      </Transition>
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
