import {IconX, IconCheck} from '@tabler/icons-react';
import {Notification} from '@mantine/core';

interface Props {
    message: string;
    onClose: () => void;
}

export default function ErrorNotification(props: Props) {
    const xIcon = <IconX size={20}/>;
    const checkIcon = <IconCheck size={20}/>;


    return (
        <>
            <Notification icon={xIcon} color="red" title="Error!" onClose={props.onClose} >
                {props.message}
            </Notification>
        </>
    );
}