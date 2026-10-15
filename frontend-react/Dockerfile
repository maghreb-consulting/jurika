FROM node:23-alpine AS builder

WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

ARG VITE_API_URL=https://api.jurika.ma/api/v1
ARG VITE_REALTIME_URL=
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_REALTIME_URL=$VITE_REALTIME_URL
# Lot L7 (E3) : adresse de contact de la vitrine, vide par defaut (aucune adresse affichee).
ARG VITE_CONTACT_EMAIL=
ENV VITE_CONTACT_EMAIL=$VITE_CONTACT_EMAIL

RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK CMD wget -q --spider http://localhost/ || exit 1
