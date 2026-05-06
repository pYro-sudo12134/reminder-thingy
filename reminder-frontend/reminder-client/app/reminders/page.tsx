"use client";

import { ActionIcon, Title, Group, Transition } from "@mantine/core";
import { IconPlus, IconPlayerStop } from "@tabler/icons-react";
import { ReminderList } from "../components/cards/ReminderList";
import { ReminderInfoModal } from "../components/Modals/ReminderInfoModal";
import { ReminderEditModal } from "../components/Modals/ReminderEditModal";
import { deleteReminder, getUserReminders, updateReminder, ReminderUpdateRequest, ReminderRecord, recordVoiceReminder } from "../services/reminders/ReminderService";
import { GetUserLogin } from "../services/users/UserService";
import type { ReminderItem } from "../components/cards/ReminderCard";
import { useEffect, useState, useRef } from "react";
import { useRouter } from "next/navigation";
import ErrorNotification from "../components/Notifications/ErrorNotification";
import SuccessNotification from "../components/Notifications/SuccessNotification";
import { ReminderAutocomplete } from "../components/Autocomplete/ReminderAutocomplete";

const defaultValues = {
    id: "",
    userId: "",
    userEmail: "",
    originalText: "",
    actDescription: "",
    scheduledTime: "",
    createdAt: "",
    status: "",
    notificationSent: false,
} as ReminderItem;


function mapReminderRecordToItem(record: ReminderRecord): ReminderItem {
    return {
        id: record.reminderId,
        userId: record.userId,
        userEmail: record.userEmail,
        originalText: record.originalText,
        actDescription: record.extractedAction,
        scheduledTime: record.scheduledTime,
        createdAt: record.createdAt,
        status: record.status,
        notificationSent: record.notificationSent,
        intent: record.intent,
        eventBridgeRuleName: record.eventBridgeRuleName,
    };
}

export default function RemindersPage() {
    const router = useRouter();
    const [currentUserId, setCurrentUserId] = useState<string | null>(null);
    const [currentUserEmail, setCurrentUserEmail] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [values, setValues] = useState<ReminderItem>(defaultValues);
    const [reminders, setReminders] = useState<ReminderItem[]>([]);
    const [filteredReminders, setFilteredReminders] = useState<ReminderItem[]>([]);
    const [searchQuery, setSearchQuery] = useState("");
    const [isInfoModalOpen, setIsInfoModalOpen] = useState(false);
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [isRecording, setIsRecording] = useState(false);
    const [isSending, setIsSending] = useState(false);
    const [showError, setShowError] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [showSuccess, setShowSuccess] = useState(false); 
    const [successMessage, setSuccessMessage] = useState<string | null>(null); 
    
    const mediaRecorderRef = useRef<MediaRecorder | null>(null);
    const audioChunksRef = useRef<Blob[]>([]);
    const streamRef = useRef<MediaStream | null>(null);
    const audioContextRef = useRef<AudioContext | null>(null);

    useEffect(() => {
        const authenticateAndFetch = async () => {
            try {
                const user = await GetUserLogin();
                if (!user) {
                    router.push('/users/login?authenticationType=login');
                    return;
                }
                setCurrentUserId(user.userId);
                setCurrentUserEmail(user.email || "");

                const data = await getUserReminders(user.userId, 50);
                const mappedReminders = data.reminders.map(mapReminderRecordToItem);
                setReminders(mappedReminders);
                setFilteredReminders(mappedReminders);
            } catch (error: any) {
                if (error?.status === 401) {
                    router.push('/users/login?authenticationType=login');
                    return;
                }
                console.error("Failed to authenticate or fetch reminders:", error);
            } finally {
                setIsLoading(false);
            }
        };
        authenticateAndFetch();
    }, []);

    useEffect(() => {
        if (searchQuery.trim() === "") {
            setFilteredReminders(reminders);
        } else {
            const filtered = reminders.filter(reminder =>
                reminder.actDescription?.toLowerCase().includes(searchQuery.toLowerCase()) ||
                reminder.originalText?.toLowerCase().includes(searchQuery.toLowerCase())
            );
            setFilteredReminders(filtered);
        }
    }, [searchQuery, reminders]);

    const handleAutocompleteSelect = (reminderId: string) => {
        const reminder = reminders.find(r => r.id === reminderId);
        if (reminder) {
            openInfoModal(reminder);
            setSearchQuery("");
        }
    };

    const handleUpdateReminder = async (id: string, request: ReminderUpdateRequest) => {
        if (!currentUserId) return;
        try {
            await updateReminder(id, request);
            closeEditModal();
            await new Promise(resolve => setTimeout(resolve, 1000));
            const userId = (await GetUserLogin())?.userId;
            const data = await getUserReminders(userId as string, 50);
            const mappedReminders = data.reminders.map(mapReminderRecordToItem);
            setReminders(mappedReminders);
            setFilteredReminders(mappedReminders);
        } catch (error) {
            console.error("Failed to update reminder:", error);
        }
    };

    const handleDeleteReminder = async (item: ReminderItem) => {
        if (!currentUserId) return;
        try {
            console.log("Deleting reminder:", item.id);
            await deleteReminder(item.id);
            console.log("Reminder deleted, waiting for backend...");
            
            await new Promise(resolve => setTimeout(resolve, 1000));
            console.log("Fetching updated list...");
            const userId = (await GetUserLogin())?.userId;
            const data = await getUserReminders(userId as string, 50);
            console.log("Fetched updated list:", data);
            const mappedReminders = data.reminders.map(mapReminderRecordToItem);
            setReminders(mappedReminders);
            setFilteredReminders(mappedReminders);
        } catch (error) {
            console.error("Failed to delete reminder:", error);
        }
    };

    const openInfoModal = (reminder: ReminderItem) => {
        setValues(reminder);
        setIsInfoModalOpen(true);
    };

    const closeInfoModal = () => {
        setValues(defaultValues);
        setIsInfoModalOpen(false);
    };

    const openEditModal = (reminder: ReminderItem) => {
        setValues(reminder);
        setIsEditModalOpen(true);
        setIsInfoModalOpen(false);
    };

    const closeEditModal = () => {
        setValues(defaultValues);
        setIsEditModalOpen(false);
    };

    const convertToWav = async (audioBlob: Blob): Promise<Blob> => {
        const arrayBuffer = await audioBlob.arrayBuffer();
        const audioBuffer = await audioContextRef.current!.decodeAudioData(arrayBuffer);
        
        const numChannels = audioBuffer.numberOfChannels;
        const sampleRate = audioBuffer.sampleRate;
        const format = 1; 
        const bitDepth = 16;
        
        const bytesPerSample = bitDepth / 8;
        const blockAlign = numChannels * bytesPerSample;
        
        const data = [];
        for (let i = 0; i < audioBuffer.length; i++) {
            for (let channel = 0; channel < numChannels; channel++) {
                const sample = audioBuffer.getChannelData(channel)[i];
                const intSample = Math.max(-1, Math.min(1, sample));
                data.push(intSample < 0 ? intSample * 0x8000 : intSample * 0x7FFF);
            }
        }
        
        const dataView = new DataView(new ArrayBuffer(44 + data.length * 2));
        
        
        writeString(dataView, 0, 'RIFF');
        dataView.setUint32(4, 36 + data.length * 2, true);
        writeString(dataView, 8, 'WAVE');
        
        
        writeString(dataView, 12, 'fmt ');
        dataView.setUint32(16, 16, true);
        dataView.setUint16(20, format, true);
        dataView.setUint16(22, numChannels, true);
        dataView.setUint32(24, sampleRate, true);
        dataView.setUint32(28, sampleRate * blockAlign, true);
        dataView.setUint16(32, blockAlign, true);
        dataView.setUint16(34, bitDepth, true);
        
        
        writeString(dataView, 36, 'data');
        dataView.setUint32(40, data.length * 2, true);
        
        
        for (let i = 0; i < data.length; i++) {
            dataView.setInt16(44 + i * 2, data[i], true);
        }
        
        return new Blob([dataView], { type: 'audio/wav' });
    };

    const writeString = (view: DataView, offset: number, string: string) => {
        for (let i = 0; i < string.length; i++) {
            view.setUint8(offset + i, string.charCodeAt(i));
        }
    };

    const startRecording = async () => {
        try {
            const constraints = {
                audio: {
                    sampleRate: 16000,
                    channelCount: 1,
                    echoCancellation: true,
                    noiseSuppression: true,
                    autoGainControl: true,
                    sampleSize: 16
                }
            };

            const stream = await navigator.mediaDevices.getUserMedia(constraints);
            streamRef.current = stream;

            audioContextRef.current = new AudioContext({
                sampleRate: 16000
            });

            const options = {
                mimeType: 'audio/webm;codecs=opus',
                audioBitsPerSecond: 64000
            };

            let mediaRecorder: MediaRecorder;
            try {
                mediaRecorder = new MediaRecorder(stream, options);
            } catch {
                mediaRecorder = new MediaRecorder(stream);
            }

            mediaRecorderRef.current = mediaRecorder;
            audioChunksRef.current = [];

            mediaRecorder.ondataavailable = (event) => {
                if (event.data.size > 0) {
                    audioChunksRef.current.push(event.data);
                }
            };

            mediaRecorder.start(1000);
            setIsRecording(true);
        } catch (error) {
            console.error('Microphone access error:', error);
            setErrorMessage('Microphone access error.')
            setShowError(true);
        }
    };

    const stopRecording = async () => {
        if (!mediaRecorderRef.current || !isRecording) return;

        setIsSending(true);

        return new Promise<void>((resolve) => {
            const mediaRecorder = mediaRecorderRef.current!;
            
            mediaRecorder.onstop = async () => {
                try {
                    const audioBlob = new Blob(audioChunksRef.current, {
                        type: mediaRecorder.mimeType || 'audio/webm'
                    });

                    const wavBlob = await convertToWav(audioBlob);

                    await recordVoiceReminder(currentUserId as string, currentUserEmail as string, wavBlob);
                    setSuccessMessage("Reminder was successfully created!");
                    setShowSuccess(true);
                    await new Promise(resolve => setTimeout(resolve, 1000));

                    const data = await getUserReminders(currentUserId as string, 50);
                    const mappedReminders = data.reminders.map(mapReminderRecordToItem);
                    setReminders(mappedReminders);
                    setFilteredReminders(mappedReminders);
                } catch (error) {
                    console.error('An error occured during audio processing:', error);
                    setErrorMessage('An error occured during audio processing.')
                    setShowError(true);
                } finally {
                    if (streamRef.current) {
                        streamRef.current.getTracks().forEach(track => track.stop());
                    }
                    if (audioContextRef.current) {
                        audioContextRef.current.close();
                    }
                    mediaRecorderRef.current = null;
                    streamRef.current = null;
                    audioContextRef.current = null;
                    setIsSending(false);
                    resolve();
                }
            };

            mediaRecorder.stop();
            setIsRecording(false);
        });
    };

    const handleRecordToggle = async () => {
        if (isRecording) {
            await stopRecording();
        } else {
            await startRecording();
        }
    };

    return (
        <>
        <Transition mounted={showError} transition="slide-down" duration={300} timingFunction="ease">
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
                message = {errorMessage as string}
                onClose={() => setShowError(false)}
              />
            </div>
          </div>
        )}
      </Transition>
        
        <Transition mounted={showSuccess} transition="slide-down" duration={300} timingFunction="ease">
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
              <SuccessNotification
                message={successMessage as string}
                onClose={() => setShowSuccess(false)}
              />
            </div>
          </div>
        )}
      </Transition>

        <div className="p-6">
            <h1 className="text-2xl font-bold mb-6">Reminders</h1>
            <ReminderInfoModal
                isOpen={isInfoModalOpen}
                handleClose={closeInfoModal}
                values={values}
            />
            <ReminderEditModal
                isOpen={isEditModalOpen}
                values={values}
                handleUpdate={handleUpdateReminder}
                handleCancel={closeEditModal}
            />
            
            <Group mb="md">
                <div style={{ flex: 1, maxWidth: 600 }}>
                    <ReminderAutocomplete
                        userId={currentUserId || ""}
                        onReminderSelect={handleAutocompleteSelect}
                        placeholder="Search reminders..."
                        label="Search"
                        reminders={reminders}
                    />
                </div>
            </Group>
            
            {isLoading ? (
                <Title>Loading...</Title>
            ) : (
                <ReminderList
                    items={filteredReminders}
                    onOpen={openInfoModal}
                    onEdit={openEditModal}
                    onDelete={handleDeleteReminder}
                />
            )}
            
            <div style={{
                position: 'fixed',
                bottom: '24px',
                right: '24px',
                zIndex: 1000
            }}>
                <ActionIcon
                    variant="filled"
                    color={isRecording ? "red" : "blue"}
                    size="xl"
                    radius="xl"
                    aria-label={isRecording ? "Stop recording" : "Start recording"}
                    onClick={handleRecordToggle}
                    loading={isSending}
                >
                    {isRecording ? (
                        <IconPlayerStop style={{ width: '70%', height: '70%' }} stroke={1.5} />
                    ) : (
                        <IconPlus style={{ width: '70%', height: '70%' }} stroke={1.5} />
                    )}
                </ActionIcon>
            </div>
        </div>
        </>
    );
}
