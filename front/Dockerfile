# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app

# vite build는 import.meta.env.VITE_*를 빌드 시점에 번들에 박아 넣으므로,
# 컨테이너 런타임 environment가 아니라 build-time ARG로 전달받아야 한다.
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npm run build

# ---- serve ----
FROM node:22-alpine
WORKDIR /app

RUN npm install -g serve

COPY --from=build /app/dist ./dist

ENV PORT=5173
EXPOSE 5173

CMD ["sh", "-c", "serve -s dist -l ${PORT}"]
