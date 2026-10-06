# ─── 阶段 1：构建依赖 ───
FROM node:25-alpine AS builder
WORKDIR /app

COPY package*.json ./
# Plain npm ci: hocuspocus and tiptap declare runtime packages (y-protocols,
# y-prosemirror, ...) as peer dependencies, so peers MUST be auto-installed.
# --legacy-peer-deps skipped them and the container crashed on boot with
# ERR_MODULE_NOT_FOUND.
RUN npm ci

COPY . .

# ─── 阶段 2：运行环境 ───
FROM node:25-alpine AS runtime
WORKDIR /app

# 安全考虑：使用非 root 用户运行
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --chown=appuser:appgroup --from=builder /app /app

USER appuser

EXPOSE 1234
ENV PORT=1234
ENV NODE_ENV=production

CMD ["node", "index.js"]