"use client";

import { usePathname } from 'next/navigation';
import { MantineProvider } from "@mantine/core";
import "@mantine/core/styles.css";
import { NavbarMinimal } from "./components/NavbarMinimal/NavbarMinimal";


export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const pathname = usePathname();
  const showNavbar = pathname !== '/';

  return (
    <html lang="en">
      <body>
        <MantineProvider defaultColorScheme="dark">
          <div style={{ display: "flex", minHeight: "100vh" }}>
            {showNavbar && <NavbarMinimal />}
            <main style={{ flex: 1, marginLeft: showNavbar ? "80px" : "0" }}>
              {children}
            </main>
          </div>
        </MantineProvider>
      </body>
    </html>
  );
}
