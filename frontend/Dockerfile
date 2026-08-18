# =============================================================================
# MedPay Ledger — frontend image (Vite build served by Nginx).
#
# Stage 1 builds the SPA; stage 2 is Nginx with the static output and no Node
# runtime at all. Non-root at runtime, same as the backend.
#
#   docker build -t medpay-frontend ./frontend
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1 — build
# -----------------------------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /build

# Manifests first so the dependency layer survives source-only edits.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# `npm run build` is `tsc -b && vite build`, so a type error fails the image
# build rather than shipping a bundle that compiled around it.
RUN npm run build

# -----------------------------------------------------------------------------
# Stage 2 — runtime
# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# The stock config's `server` block would collide with ours on :80.
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/medpay.conf
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /build/dist /usr/share/nginx/html

# Run as the image's existing unprivileged `nginx` user (PLAN.md Phase 0: both
# images non-root at runtime).
#
# Two consequences to deal with. First, the pid file and the proxy/cache temp
# paths are root-owned in the base image and the master process must write both.
# Second, an unprivileged process cannot bind a port below 1024 — the stock
# nginx image expects a root master and only drops privileges for its workers.
# Granting the binary CAP_NET_BIND_SERVICE keeps the listener on :80, so both
# compose files' port mappings stay as they are; the alternative is switching to
# port 8080 and editing the published ports in two places.
RUN apk add --no-cache libcap \
    && setcap 'cap_net_bind_service=+ep' /usr/sbin/nginx \
    && apk del libcap \
    && touch /var/run/nginx.pid \
    && chown -R nginx:nginx /var/run/nginx.pid /var/cache/nginx /usr/share/nginx/html
USER nginx

EXPOSE 80

# Foreground: Nginx daemonises by default, which would exit PID 1 immediately
# and take the container with it.
CMD ["nginx", "-g", "daemon off;"]
