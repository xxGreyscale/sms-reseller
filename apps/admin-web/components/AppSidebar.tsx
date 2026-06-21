'use client'

/**
 * AppSidebar — fixed 240px sidebar for the admin shell.
 *
 * UI-SPEC §Layout:
 *   - Top: logo/app name (24px semibold)
 *   - Middle: 6 vertical nav items with active-state zinc-900 bg + zinc-50 text
 *   - Bottom: admin email + logout link
 *
 * Nav items (exact order per UI-SPEC):
 *   Users, Ledger, Sender IDs, Refunds, Bundle Catalog, Audit Log
 */
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import {
  Users,
  BookOpen,
  BadgeCheck,
  RotateCcw,
  Package,
  ClipboardList,
  LogOut,
} from 'lucide-react'
import { Separator } from '@/src/components/ui/separator'
import { cn } from '@/src/lib/utils'

const NAV_ITEMS = [
  { label: 'Users', href: '/users', icon: Users },
  { label: 'Ledger', href: '/ledger', icon: BookOpen },
  { label: 'Sender IDs', href: '/sender-ids', icon: BadgeCheck },
  { label: 'Refunds', href: '/refunds', icon: RotateCcw },
  { label: 'Bundle Catalog', href: '/bundles', icon: Package },
  { label: 'Audit Log', href: '/audit', icon: ClipboardList },
]

interface AppSidebarProps {
  adminEmail?: string
}

export default function AppSidebar({ adminEmail = 'admin' }: AppSidebarProps) {
  const pathname = usePathname()

  return (
    <aside className="w-[240px] flex-shrink-0 bg-zinc-900 min-h-screen flex flex-col">
      {/* Logo / App Name */}
      <div className="px-4 py-5">
        <span className="text-[24px] font-semibold text-zinc-50">Open Desk</span>
        <p className="text-[12px] text-zinc-400 mt-0.5">Admin Panel</p>
      </div>

      <Separator className="bg-zinc-700" />

      {/* Navigation */}
      <nav className="flex-1 px-2 py-3 space-y-0.5">
        {NAV_ITEMS.map(({ label, href, icon: Icon }) => {
          const isActive = pathname === href || pathname.startsWith(`${href}/`)
          return (
            <Link
              key={href}
              href={href}
              className={cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-md text-[14px] font-normal transition-colors',
                isActive
                  ? 'bg-zinc-50 text-zinc-900 font-medium'
                  : 'text-zinc-400 hover:text-zinc-50 hover:bg-zinc-800'
              )}
            >
              <Icon className="h-4 w-4 flex-shrink-0" />
              {label}
            </Link>
          )
        })}
      </nav>

      <Separator className="bg-zinc-700" />

      {/* Admin email + logout */}
      <div className="px-4 py-4 space-y-2">
        <p className="text-[12px] text-zinc-400 truncate">{adminEmail}</p>
        <Link
          href="/login"
          className="flex items-center gap-2 text-[12px] text-zinc-400 hover:text-zinc-50 transition-colors"
        >
          <LogOut className="h-3.5 w-3.5" />
          Sign out
        </Link>
      </div>
    </aside>
  )
}
