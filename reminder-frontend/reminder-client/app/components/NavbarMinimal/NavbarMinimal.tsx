"use client";

import { usePathname } from 'next/navigation';
import {
  IconCalendar,
  IconUser,
  IconLogout,
  IconSunMoon
} from '@tabler/icons-react';
import { Center, Stack, Tooltip, UnstyledButton, useMantineColorScheme } from '@mantine/core';
import classes from './NavbarMinimal.module.css';
import Link from 'next/link';
import { MantineLogo } from '@mantinex/mantine-logo';
import { LogoutUser } from '../../services/users/UserService';
import { ReminderLogo } from './ReminderLogo';

interface NavbarLinkProps {
  icon: typeof IconCalendar;
  label: string;
  href: string;
  active?: boolean;
}

function NavbarLink({ icon: Icon, label, href, active }: NavbarLinkProps) {
  return (
    <Tooltip label={label} position="right" transitionProps={{ duration: 0 }}>
      <UnstyledButton
        component={Link}
        href={href}
        className={classes.link}
        data-active={active || undefined}
        aria-label={label}
      >
        <Icon size={20} stroke={1.5} />
      </UnstyledButton>
    </Tooltip>
  );
}

export function NavbarMinimal() {
  const pathname = usePathname();
  const { toggleColorScheme } = useMantineColorScheme();

  const handleLogout = async () => {
    try {
      await LogoutUser();
      window.location.reload();
    } catch (error) {
      console.error('Logout failed:', error);
    }
  };

  return (
    <nav className={classes.navbar}>
      <Center className={classes.logo}>
        <Tooltip label="Home" position="right" transitionProps={{ duration: 0 }}>
          <UnstyledButton component={Link} href="/" className={classes.logoLink} aria-label="Home">
            <ReminderLogo src="/ReminderLogo.png" size={40} />
          </UnstyledButton>
        </Tooltip>
      </Center>

      <div className={classes.navbarMain}>
        <Stack justify="center" gap={0}>
          <NavbarLink
            icon={IconCalendar}
            label="Reminders"
            href="/reminders"
            active={pathname === '/reminders'}
          />
          <NavbarLink
            icon={IconUser}
            label="Account"
            href="/account"
            active={pathname === '/account'}
          />
        </Stack>
      </div>

      <div>
        <Stack justify="center" gap={0}>

          <Tooltip label="Change Theme" position="right" transitionProps={{ duration: 0 }}>
            <UnstyledButton
              className={classes.link}
              onClick={toggleColorScheme}
              aria-label="Toggle Theme"
            >
              <IconSunMoon size={20} stroke={1.5} />
            </UnstyledButton>
          </Tooltip>

          <Tooltip label="Logout" position="right" transitionProps={{ duration: 0 }}>
            <UnstyledButton
              className={classes.link}
              onClick={handleLogout}
              aria-label="Logout"
            >
              <IconLogout size={20} stroke={1.5} />
            </UnstyledButton>
          </Tooltip>
        </Stack>
      </div>
    </nav>
  );
}
