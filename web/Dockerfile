# FlowGuard Web — multi-stage Dockerfile
# Node 20 build → nginx serve (supports ARM64)

# ── Stage 1: Build ─────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci --legacy-peer-deps

COPY . .

# VITE_API_URL="" means axios uses relative paths → nginx on same origin handles /api/*
ARG VITE_API_URL=""
ENV VITE_API_URL=$VITE_API_URL

RUN npm run build

# ── Stage 2: Serve ─────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx-web.conf /etc/nginx/conf.d/default.conf

# Run nginx as non-root (port 8080 instead of privileged 80)
# The container is exposed on host port 80 via docker-compose port mapping
RUN apk update && apk upgrade --no-cache \
    && sed -i 's/listen 80;/listen 8080;/' /etc/nginx/conf.d/default.conf \
    && addgroup -S flowguard \
    && adduser -S flowguard -G flowguard \
    # Allow nginx worker to write its pid and cache files
    && chown -R flowguard:flowguard /var/cache/nginx /var/log/nginx /etc/nginx/conf.d \
    && touch /var/run/nginx.pid \
    && chown flowguard:flowguard /var/run/nginx.pid

USER flowguard

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
