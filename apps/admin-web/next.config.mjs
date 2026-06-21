/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',  // Next.js standalone output for DOKS (1 replica) — reduces image size
}

export default nextConfig
