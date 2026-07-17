# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .

# Vite는 VITE_* 환경변수를 런타임이 아닌 빌드 시점에 정적 번들로 고정한다.
# nginx 리버스 프록시 뒤에서 서빙되므로 API 호출은 상대경로(/api/...)를 그대로 쓰면 되어
# VITE_API_BASE_URL은 비워둔 채(상대경로) 빌드한다.
ARG VITE_API_BASE_URL=
ARG VITE_USE_MOCK=false
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_USE_MOCK=$VITE_USE_MOCK

RUN npm run build

# ---- serve ----
FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
