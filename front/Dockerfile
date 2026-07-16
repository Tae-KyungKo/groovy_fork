# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .

# Vite는 VITE_* 환경변수를 런타임이 아닌 빌드 시점에 정적 번들로 고정하므로,
# docker build --build-arg로 값을 주입받아 빌드 직전에 환경변수로 노출한다.
ARG VITE_API_BASE_URL
ARG VITE_USE_MOCK=false
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_USE_MOCK=$VITE_USE_MOCK

RUN npm run build

# ---- serve ----
FROM node:22-alpine
WORKDIR /app

RUN npm install -g serve

COPY --from=build /app/dist ./dist

ENV PORT=5173
EXPOSE 5173

CMD ["sh", "-c", "serve -s dist -l ${PORT}"]
