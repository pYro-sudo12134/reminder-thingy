"use client";

import { useRef, useState, useCallback, useEffect } from 'react';
import { Autocomplete, Loader, Text, Group, Box } from '@mantine/core';
import { IconSearch } from '@tabler/icons-react';
import { autocompleteReminders } from '@/app/services/reminders/ReminderService';
import formatDateTime from '@/app/utility/LocaleTimeTranslator';

interface ReminderSuggestion {
    reminderId: string;
    action: string;
    text: string;
    score: number;
    scheduledTime?: string;
}

interface ReminderAutocompleteProps {
    userId: string;
    onReminderSelect?: (reminderId: string) => void;
    placeholder?: string;
    label?: string;
    reminders?: Array<{ id: string; scheduledTime: string; actDescription: string }>;
}


export function ReminderAutocomplete({
    userId,
    onReminderSelect,
    placeholder = "Search reminders...",
    reminders,
}: ReminderAutocompleteProps) {
    const [value, setValue] = useState('');
    const [loading, setLoading] = useState(false);
    const [suggestions, setSuggestions] = useState<ReminderSuggestion[]>([]);
    const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const handleSearch = useCallback(async (val: string) => {
        if (timeoutRef.current) {
            clearTimeout(timeoutRef.current);
        }
        setValue(val);

        if (val.trim().length < 2) {
            setLoading(false);
            setSuggestions([]);
            return;
        }

        setLoading(true);

        timeoutRef.current = setTimeout(async () => {
            try {
                const result = await autocompleteReminders(userId, val, 8);
                console.log('Autocomplete result:', result.suggestions);
                setSuggestions(result.suggestions || []);
            } catch (error) {
                console.error('Autocomplete error:', error);
                setSuggestions([]);
            } finally {
                setLoading(false);
            }
        }, 400);
    }, [userId]);

    useEffect(() => {
        return () => {
            if (timeoutRef.current) {
                clearTimeout(timeoutRef.current);
            }
        };
    }, []);

    
    const data = suggestions.map(s => {
        const displayText = s.action || s.text;
        
        let scheduledTime = s.scheduledTime;
        if (!scheduledTime && reminders) {
            const localReminder = reminders.find(r => r.id === s.reminderId);
            if (localReminder) {
                scheduledTime = localReminder.scheduledTime;
            }
        }
        return {
            value: `${s.reminderId}|||${displayText}`,
            label: displayText,
            scheduledTime: scheduledTime,
        };
    });

    return (
        <Box style={{marginTop: "24px"}}>
            <Autocomplete
                value={value}
                data={data}
                onChange={(val) => {
                    handleSearch(val);
                }}
                onOptionSubmit={(optionValue) => {
                    const reminderId = optionValue.split('|||')[0];
                    if (onReminderSelect) {
                        onReminderSelect(reminderId);
                    }
                    
                    const suggestion = suggestions.find(s => s.reminderId === reminderId);
                    if (suggestion) {
                        setValue(suggestion.action || suggestion.text);
                    }
                }}
                renderOption={({ option }) => {
                    const opt = option as typeof data[0];
                    return (
                        <Group gap="sm" wrap="nowrap">
                            <Text
                                size="sm"
                                style={{
                                    wordBreak: 'break-all',
                                    display: '-webkit-box',
                                    WebkitLineClamp: 1,
                                    WebkitBoxOrient: 'vertical',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    flex: '1 1 auto',
                                    minWidth: 0,
                                    maxWidth: '100%',
                                }}
                            >
                                {opt.label}
                            </Text>
                            {opt.scheduledTime && (
                                <Text
                                    size="xs"
                                    c="dimmed"
                                    style={{
                                        whiteSpace: 'nowrap',
                                        flex: '0 0 auto',
                                        minWidth: '100px',
                                    }}
                                >
                                    {formatDateTime(opt.scheduledTime)}
                                </Text>
                            )}
                        </Group>
                    );
                }}
                leftSection={<IconSearch size={16} stroke={1.5} />}
                rightSection={loading ? <Loader size={16} /> : null}
                placeholder={placeholder}
                autoComplete="off"
            />
        </Box>
    );
}
