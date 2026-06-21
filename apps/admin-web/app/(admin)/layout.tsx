/**
 * Admin shell layout — wraps all /(admin) routes.
 *
 * UI-SPEC §Layout:
 *   - Two-column: fixed 240px sidebar + fluid main
 *   - 56px top bar with page title slot (provided by children via a slot or convention)
 *   - 32px top padding, 24px horizontal padding on main content
 *
 * No page title in this layout — each page renders its own h1 in the content area.
 */
import AppSidebar from '@/components/AppSidebar'
import { Separator } from '@/src/components/ui/separator'

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen bg-zinc-50">
      <AppSidebar />

      <div className="flex-1 flex flex-col min-w-0">
        {/* 56px top bar — placeholder for future breadcrumb/actions */}
        <header className="h-14 flex items-center border-b border-zinc-200 bg-white px-6 flex-shrink-0">
          <span className="text-[14px] text-zinc-500">Open Desk Admin</span>
        </header>

        <Separator className="bg-zinc-200" />

        {/* Content area */}
        <main className="flex-1 px-6 pt-8 pb-8">
          {children}
        </main>
      </div>
    </div>
  )
}
