# 35단계: 프론트엔드 이미지 — 정적 빌드 결과를 nginx로 서빙하고 /api는 백엔드로 넘긴다(개발 시 vite 프록시와 같은 구조).
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# BACKEND_URL은 컨테이너 기동 시 nginx 공식 이미지의 템플릿 치환으로 채워진다
ENV BACKEND_URL=http://backend:8080
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
