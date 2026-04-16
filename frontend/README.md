# Power Guard Frontend

## 启动

```bash
npm install
npm run dev
```

默认通过 Vite 代理把 `/api` 请求转发到 `http://localhost:8080`。

前端请求优先使用 `VITE_API_BASE_URL`；未配置时会先尝试相对 `/api`，失败后自动回退到当前主机的 `:8080`。

如果后端不在本机 8080，可复制 `.env.example` 为 `.env` 并修改：

```bash
VITE_API_BASE_URL=http://your-backend-host:8080
VITE_PROXY_TARGET=http://your-backend-host:8080
```

`VITE_PROXY_TARGET` 只影响 `npm run dev` 的 Vite 代理，`VITE_API_BASE_URL` 用于预览、静态部署或代理不可用时的直接请求。

## 构建

```bash
npm run build
```
