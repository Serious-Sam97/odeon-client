FROM node:22-alpine AS dev
WORKDIR /app
# O código e o node_modules vêm por volume (ver docker-compose.yml), então o
# install roda no boot do container — mantém o lockfile do host como fonte.
CMD ["sh", "-c", "npm install && npm run dev -- --host 0.0.0.0"]

FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine AS runtime
COPY --from=builder /app/dist /usr/share/nginx/html
