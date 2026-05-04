'use client';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { UserCard } from '../components/UserCard/UserCard';
import { TelegramBindCard } from '../components/TelegramBindCard/TelegramBindCard';
import { generateTelegramBindingCode, GetUserLogin } from '../services/users/UserService';
import { Transition } from '@mantine/core';
import ErrorNotification from '../components/Notifications/ErrorNotification';

export default function AccountPage() {
  const router = useRouter();
  const [username, setUsername] = useState<string>('');
  const [email, setEmail] = useState<string>('');
  const [isLoading, setIsLoading] = useState(true);
  const [showError, setShowError] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  
  const [telegramCode, setTelegramCode] = useState<string | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);

  useEffect(() => {
    GetUserLogin()
      .then((user) => {
        if (user) {
          setUsername(user.username);
          setEmail(user.email)
        }
        else router.push('/users/login?authenticationType=login');
      })
      .catch((error) => {
        if (error?.status === 401) router.push('/users/login?authenticationType=login');
      })
      .finally(() => setIsLoading(false));
  }, [router]);

  
const handleGenerateCode = async () => {
  setIsGenerating(true);
  setTelegramCode(null);
  
  try {
    const user = await GetUserLogin();
    if (!user?.userId) throw new Error('User not authenticated');
    
    const result = await generateTelegramBindingCode(user.userId);
    setTelegramCode(result.code || result.bindingCode || 'ERROR');
    
  } catch (error) {
    console.error('Failed to generate code:', error);
    setErrorMessage("Failed to generate code. Try later.");
    setShowError(true);
  } finally {
    setIsGenerating(false);
  }
};

  if (isLoading) return <div>Loading...</div>;

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
    <div style={{ 
      display: 'flex', 
      justifyContent: 'center', 
      alignItems: 'center', 
      minHeight: '100vh',
      width: '100%',
      padding: '20px',
      boxSizing: 'border-box'
    }}>
      <div style={{ 
        display: 'flex', 
        flexWrap: 'wrap', 
        justifyContent: 'center', 
        alignItems: 'stretch', 
        gap: '32px',
        maxWidth: '800px',
        width: '100%'
      }}>
        <UserCard
          name={username || 'Guest'}
          email={email || "me@example.io"}
          position="Regular"
        />
        
        <TelegramBindCard
          cardTitle="Link your Telegram"
          buttonText="Generate Code"
          onGenerateClick={handleGenerateCode}
          code={telegramCode}
          loading={isGenerating}
        />
      </div>
    </div>
    </>
  );
}