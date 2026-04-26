FROM node:22.17-alpine AS builder

WORKDIR /app

COPY reminder-client/package.json reminder-client/package-lock.json ./
RUN npm ci

COPY reminder-client ./

ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}

RUN npm run build

FROM node:22.17-alpine AS runner

WORKDIR /app
ENV NODE_ENV=production

RUN apk add --no-cache curl

COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/public ./public
COPY --from=builder /app/next.config.ts ./next.config.ts

EXPOSE 3015

CMD ["npm", "run", "start"]
