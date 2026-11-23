import type { Metadata } from "next";
import "./globals.css";
import { Experience } from '@/components/ui/Experience';

export const metadata: Metadata = {
  title: "Chessoc",
  description: "A place for your next great game. Play chess, crazyhouse and bughouse, watch live matches, and revisit every move.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col"><Experience>{children}</Experience></body>
    </html>
  );
}
