# ─── 阶段 1：构建依赖 ───
FROM node:22-alpine AS builder
WORKDIR /app

COPY package*.json ./
# 使用 --legacy-peer-deps 确保对等依赖安全安装
RUN npm ci --legacy-peer-deps

COPY . .

# ─── 阶段 2：运行环境 ───
FROM node:22-alpine AS runtime
WORKDIR /app

# 安全考虑：使用非 root 用户运行
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --chown=appuser:appgroup --from=builder /app /app

USER appuser

EXPOSE 1234
ENV PORT=1234
ENV NODE_ENV=production

CMD ["node", "index.js"]