FROM node:24-alpine AS dependencies

RUN corepack enable
WORKDIR /app

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

FROM node:24-alpine AS build

RUN corepack enable
WORKDIR /app

ARG NEXT_PUBLIC_API_BASE_URL=/backend-api
ARG NEXT_PUBLIC_WEBSOCKET_URL
ARG NEXT_PUBLIC_OIDC_AUTHORIZATION_URL
ARG NEXT_PUBLIC_OIDC_TOKEN_URL
ARG NEXT_PUBLIC_OIDC_CLIENT_ID
ARG NEXT_PUBLIC_OIDC_REDIRECT_URI
ARG NEXT_PUBLIC_OIDC_SCOPE="openid profile email"

ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL} \
    NEXT_PUBLIC_WEBSOCKET_URL=${NEXT_PUBLIC_WEBSOCKET_URL} \
    NEXT_PUBLIC_OIDC_AUTHORIZATION_URL=${NEXT_PUBLIC_OIDC_AUTHORIZATION_URL} \
    NEXT_PUBLIC_OIDC_TOKEN_URL=${NEXT_PUBLIC_OIDC_TOKEN_URL} \
    NEXT_PUBLIC_OIDC_CLIENT_ID=${NEXT_PUBLIC_OIDC_CLIENT_ID} \
    NEXT_PUBLIC_OIDC_REDIRECT_URI=${NEXT_PUBLIC_OIDC_REDIRECT_URI} \
    NEXT_PUBLIC_OIDC_SCOPE=${NEXT_PUBLIC_OIDC_SCOPE} \
    NEXT_TELEMETRY_DISABLED=1

COPY --from=dependencies /app/node_modules ./node_modules
COPY . .
RUN pnpm build

FROM node:24-alpine AS runtime

RUN apk add --no-cache curl \
    && addgroup --system --gid 1001 nodejs \
    && adduser --system --uid 1001 nextjs

WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

COPY --from=build --chown=nextjs:nodejs /app/public ./public
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000

CMD ["node", "server.js"]
