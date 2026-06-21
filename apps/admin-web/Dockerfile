# Multi-stage Dockerfile for admin-web Next.js 14 (standalone output)
# D-11: locked stack — Next.js 14 App Router, node:20-alpine
# Deploys to DOKS at 1 replica (MVP). Standalone output reduces image attack surface.

FROM node:20-alpine AS base

# Stage 1: Install dependencies
FROM base AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --only=production --ignore-scripts

# Stage 2: Build
FROM base AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Build with standalone output (next.config.mjs: output: 'standalone')
RUN npm ci && npm run build

# Stage 3: Runner (minimal image — no dev deps, no source)
FROM base AS runner
WORKDIR /app

ENV NODE_ENV production
ENV NEXT_TELEMETRY_DISABLED 1

RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

# Copy standalone output (includes all required node_modules subset)
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000
ENV PORT 3000
ENV HOSTNAME "0.0.0.0"

# Next.js standalone server
CMD ["node", "server.js"]
