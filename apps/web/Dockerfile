# Web image, multi-stage (Learning Notes 54.3)

# Stage 1: install and build with pnpm
FROM node:24-alpine AS build
WORKDIR /src
# corepack provides the exact pnpm version named in package.json ("packageManager")
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
# Dependencies first: this layer is reused until the lockfile changes
RUN pnpm install --frozen-lockfile
COPY . .
# Copied into the browser JavaScript at BUILD time: changing it means rebuilding the image (54.6)
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
RUN pnpm build

# Stage 2: only Node and the standalone output (server.js + the files it traced)
FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0
COPY --from=build --chown=node:node /src/.next/standalone ./
# server.js doesn't include these two folders by itself (Next.js docs: output → standalone)
COPY --from=build --chown=node:node /src/.next/static ./.next/static
COPY --from=build --chown=node:node /src/public ./public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
