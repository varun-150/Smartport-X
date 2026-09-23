# SmartPortX Dashboard — multi-stage: Vite build → nginx static server + reverse proxy.
#
# The dashboard is browser-rendered and speaks to the backend through same-origin
# paths: REST via /api and STOMP-over-SockJS via /ws. nginx proxies both to the
# Spring Cloud Gateway (container name `gateway`, port 8080), so the VITE_API_BASE_URL
# build arg must remain the same-origin default '/api' (never a hard host).
#
# Build:
#   docker compose build dashboard
#   (or: docker build -t smartportx-dashboard --build-arg VITE_API_BASE_URL=/api .)
FROM node:24-alpine AS build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine

RUN apk add --no-cache curl

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]