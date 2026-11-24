import type { Metadata } from "next";
import localFont from 'next/font/local';
import "./globals.css";
import { Experience } from '@/components/ui/Experience';

const manrope = localFont({ src: './fonts/Manrope-latin.woff2', variable: '--font-ui', weight: '200 800', display: 'swap', fallback: ['Segoe UI', 'Arial'] });
const cormorant = localFont({ src: './fonts/CormorantGaramond-latin.woff2', variable: '--font-editorial', weight: '300 700', display: 'swap', fallback: ['Georgia'] });

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
    <html lang="en" className={`${manrope.variable} ${cormorant.variable} h-full antialiased`}>
      <body className="min-h-full flex flex-col"><Experience>{children}</Experience></body>
    </html>
  );
}
