FROM node:24-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ARG EXPO_PUBLIC_API_BASE_URL
ARG EXPO_PUBLIC_WEBSOCKET_URL
ENV EXPO_PUBLIC_API_BASE_URL=${EXPO_PUBLIC_API_BASE_URL} \
    EXPO_PUBLIC_WEBSOCKET_URL=${EXPO_PUBLIC_WEBSOCKET_URL} \
    EXPO_PUBLIC_USE_MOCK_STUDENT_LOGIN=false \
    EXPO_PUBLIC_USE_MOCK_STUDENT_FEATURES=false

RUN npx expo export --platform web --output-dir dist

FROM nginx:1.29-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

