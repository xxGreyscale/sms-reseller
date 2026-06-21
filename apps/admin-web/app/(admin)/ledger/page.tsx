// Ledger index — redirect to users search (ledger is accessed via user search)
import { redirect } from 'next/navigation'

export default function LedgerIndexPage() {
  redirect('/users')
}
