"use client";

import { Suspense } from "react";

export const dynamic = "force-dynamic";

import { useState } from "react";
import {
    LoginUser,
    RegisterUser,
    UserLoginRequest,
    UserRegisterRequest
} from "@/app/services/users/UserService";
import { useRouter, useSearchParams } from "next/navigation";
import { AuthenticationForm } from "@/app/components/AuthenticationFormComponent/AuthenticationForm";
import { User } from "@/app/models/User";

function LoginForm() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const authenticationType = searchParams.get("authenticationType");


    const defaultValues = {
        nickname: "",
        password: "",
    } as User;

    const [user, setUser] = useState<User>(defaultValues);

    const handleLogin = async (userRequest: UserLoginRequest) => {
        try {
            await LoginUser(userRequest);
        } catch (err: any) {
            if (err.message == "Incorrect login or password") {
                throw new Error(err.message);
            }
        }
        router.push("/reminders");

        setUser(defaultValues);
    }

    const handleRegister = async (userRequest: UserRegisterRequest) => {
        try {
            await RegisterUser(userRequest);
        } catch (error: any) {
            throw error;
        }
        setUser(defaultValues);
    }


    const handleCancel = () => {
    } //TODO: erase this later


    return (
        <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: '100vh' }}>
            <AuthenticationForm
                key={authenticationType}
                handle_login={handleLogin}
                handle_register={handleRegister}
                handle_cancel={handleCancel}
                isLoginState={authenticationType === 'login'}
            />
        </div>
    );
}

export default function UserLoginPage() {
    return (
        <Suspense fallback={<div>Loading...</div>}>
            <LoginForm />
        </Suspense>
    );
}
