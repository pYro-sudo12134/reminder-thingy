const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8090";

export interface UserLoginRequest {
    username: string;
    password: string;
}

export interface UserRegisterRequest {
    username: string;
    email: string;
    password: string;
}

export interface UserMeResponse {
    username: string;
    userId: string;
    email: string;
}

export const LoginUser = async (userRequest: UserLoginRequest): Promise<void> => {
    const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
            username: userRequest.username,
            password: userRequest.password
        }),
        credentials: 'include'
    });

    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        if (response.status === 401) {
            throw new Error("Incorrect login or password");
        }
        throw new Error(errorData.message || `HTTP Error: ${response.status}`);
    }
};

export const RegisterUser = async (userRequest: UserRegisterRequest): Promise<void> => {
  const response = await fetch(`${API_BASE_URL}/api/auth/register`, {
    method: 'POST',
    body: new URLSearchParams({
      username: userRequest.username,
      email: userRequest.email,
      password: userRequest.password
    }),
    credentials: 'include'
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    if (response.status === 409) {
      throw new Error("Such email is already registered");
    }
    throw new Error(errorData.message || `HTTP Error: ${response.status}`);
  }
};

export const SendConfirmationEmail = async (data: { email: string }): Promise<void> => {
    const response = await fetch(`${API_BASE_URL}/api/auth/send-confirmation`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(data),
        credentials: 'include'
    });

    if (!response.ok) {
        throw new Error(`HTTP Error: ${response.status}`);
    }
};

export const LoginWithHardcodedCredentials = async (): Promise<void> => {
    const credentials = {
        username: 'Agrobat',
        password: 'YMW5GYLuFG4V4iN'
    };

    const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
            username: credentials.username,
            password: credentials.password
        }),
        credentials: 'include'
    });

    if (!response.ok) {
        throw new Error(`HTTP Error: ${response.status}`);
    }
};

export const GetUserLogin = async (): Promise<UserMeResponse | null> => {
    try {
        const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
            credentials: 'include',
        });

        if (!response.ok) {
            return null;
        }

        return await response.json();
    } catch (error) {
        console.error('Auth check failed:', error);
        return null;
    }
};

export const IsAuthenticated = async (): Promise<boolean> => {
    try {
        const response = await fetch(`${API_BASE_URL}/api/auth/is-auth`, {
            credentials: 'include',
        });

        if (!response.ok) {
            return false;
        }

        const data = await response.json();
        return data.active === true;
    } catch (error) {
        console.error('Auth status check failed:', error);
        return false;
    }
};

export const LogoutUser = async (): Promise<void> => {
    const response = await fetch(`${API_BASE_URL}/api/auth/logout`, {
        method: 'POST',
        credentials: 'include',
    });

    if (!response.ok) {
        throw new Error(`HTTP Error: ${response.status}`);
    }
};

export interface TelegramCodeResponse {
  success: boolean;
  code?: string;
  bindingCode?: string;
  error?: string;
}

export const generateTelegramBindingCode = async (
  userId: string
): Promise<TelegramCodeResponse> => {
  const url = `${API_BASE_URL}/api/user/${userId}/telegram/generate-code`;
  
  const response = await fetch(url, {
    method: 'POST',
    credentials: 'include',
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(data.error || data.message || 'Generation code error');
  }

  return {
    success: true,
    code: data.code,
    bindingCode: data.bindingCode,
  };
};
