# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
# Empty = same origin: the browser calls /api on whatever host served the app and nginx forwards it to the backend.
# Override (e.g. --build-arg VITE_API_URL=https://api.example.com) to bake in a separate API host instead.
ARG VITE_API_URL=""
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# ---------------------------------------------------------------- serve
# the unprivileged nginx image runs as a non-root user and listens on 8080
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1
