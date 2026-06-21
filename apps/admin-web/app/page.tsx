import { redirect } from 'next/navigation'

// Root page: redirect to admin dashboard (middleware protects /admin/* routes)
export default function Home() {
  redirect('/login')
}
