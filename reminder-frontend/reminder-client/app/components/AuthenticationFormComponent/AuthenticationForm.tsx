"use client";

import {
    Anchor,
    Button,
    Checkbox,
    Divider,
    Group,
    Paper,
    PaperProps,
    PasswordInput,
    Stack,
    Text,
    TextInput,
} from "@mantine/core";
import { useForm } from "@mantine/form";
import Link from "next/link";
import { GoogleButton } from "./GoogleButton";
import { TwitterButton } from "./TwitterButton";
import { UserLoginRequest, UserRegisterRequest } from "@/app/services/users/UserService";
import { useState } from "react";
import ErrorNotification from "@/app/components/Notifications/ErrorNotification";

interface FormProps extends PaperProps {
    handle_login: (userRequest: UserLoginRequest) => void;
    handle_register: (userRequest: UserRegisterRequest) => void;
    handle_cancel: () => void;
    isLoginState: boolean;
}

export function AuthenticationForm(props: FormProps) {
    const [isLoginIn, setIsLoginIn] = useState(props.isLoginState);
    const [loginError, setLoginError] = useState(false);
    const [registerError, setRegisterError] = useState(false);
    const form = useForm({
        initialValues: {
            email: "",
            name: "",
            password: "",
            terms: true,
        },

        validate: {
            password: (val) => (val.length < 6 ? "Password should include at least 6 characters" : null),
            name: (val) =>
                (!isLoginIn && val.trim().length === 0 ? "Name is necessary!" : null),
            terms: (val) =>
                (!isLoginIn && !val ? "You must accept terms of condition to use the service!" : null),
        },
    });


    const onFormSubmit = async () => {
        let userRequest: UserLoginRequest | UserRegisterRequest;

        if (!isLoginIn) {
            userRequest = {
                username: form.values.name,
                email: form.values.email,
                password: form.values.password,
            } as UserRegisterRequest;

            try {
                await props.handle_register(userRequest as UserRegisterRequest);
                setRegisterError(false);
            } catch (error: any) {
                if (error?.message === "Such email is already registered") {
                    setRegisterError(true);
                } else {
                    console.error("Registration error", error);
                }
                return;
            }
        } else {
            userRequest = {
                username: form.values.name,
                password: form.values.password,
            } as UserLoginRequest;

            try {
                await props.handle_login(userRequest as UserLoginRequest);
                setLoginError(false);
            } catch (error: any) {
                if (error?.message === "Incorrect login or password") {
                    setLoginError(true);
                } else {
                    console.error("Login error", error);
                }
                return;
            }
        }
    };


    return (
        <Paper radius="md" p="lg" withBorder={true} style={{ width: "20%", minWidth: "370px" }}>
            <Text size="lg" fw={500}>
                Welcome to Reminder-Thingy, please log in or create an account.
            </Text>

            <Group grow mb="md" mt="md">
                <GoogleButton radius="xl">Google</GoogleButton>
                <TwitterButton radius="xl">Twitter</TwitterButton>
            </Group>

            <Divider label="Or continue with email" labelPosition="center" my="lg" />

            {isLoginIn && loginError && (
                <ErrorNotification message={"Incorrect login or password!"} onClose={() => { setLoginError(false); }} />
            )}
            {!isLoginIn && registerError && (
                <ErrorNotification message={"Such email is already registered"} onClose={() => { setRegisterError(false); }} />
            )}

            <form onSubmit={form.onSubmit(() => onFormSubmit())}>
                <Stack>

                    <TextInput
                        required
                        label="Username"
                        placeholder="Enter your username"
                        value={form.values.name}
                        onChange={(event) => form.setFieldValue("name", event.currentTarget.value)}
                        radius="md"
                    />

{!isLoginIn && (                 <TextInput
                       required
                        label="Email"
                        placeholder="Enter your email"
                        value={form.values.email}
                        onChange={(event) => form.setFieldValue("email", event.currentTarget.value)}
                        radius="md"
                    />)}
    

                    <PasswordInput
                        required
                        label="Password"
                        placeholder="Your password"
                        value={form.values.password}
                        onChange={(event) => form.setFieldValue("password", event.currentTarget.value)}
                        error={form.errors.password && "Password should include at least 6 characters"}
                        radius="md"
                    />

                    {isLoginIn && (
                        <Group justify="flex-end" mt={-10} mb={-20}>
                            <Anchor component={Link} href="/users/reset-password" c="dimmed" size="xs">
                                Forgot password?
                            </Anchor>
                        </Group>
                    )}

                    {!isLoginIn && (
                        <Checkbox
                            label="I accept terms and conditions"
                            checked={form.values.terms}
                            onChange={(event) => form.setFieldValue("terms", event.currentTarget.checked)}
                            error={form.errors.terms && "You must accept terms of condition to use the service!"}
                        />
                    )}
                </Stack>

                <Group justify="space-between" mt="xl">
                    <Anchor component="button" type="button" c="dimmed" onClick={() => { setIsLoginIn(!isLoginIn); setLoginError(false); setRegisterError(false); }} size="xs">
                        {!isLoginIn
                            ? "Already have an account? Login"
                            : "Don't have an account? Register"}
                    </Anchor>
                    <Button type="submit" radius="xl">
                        {!isLoginIn ? "Register" : "Login"}
                    </Button>
                </Group>
            </form>
        </Paper>
    );
}
