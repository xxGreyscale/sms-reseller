import type { Metadata } from 'next'
import { Inter } from 'next/font/google'
import './globals.css'

// Inter font stack — CLAUDE.md UI-SPEC design system
const inter = Inter({ subsets: ['latin'] })

export const metadata: Metadata = {
  title: 'Open Desk Admin',
  description: 'Operator admin panel — Open Desk SMS platform',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body className={inter.className}>{children}</body>
    </html>
  )
}
