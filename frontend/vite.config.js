import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig(function (_a) {
    var mode = _a.mode;
    var env = loadEnv(mode, process.cwd(), "");
    return {
        plugins: [react()],
        build: {
            chunkSizeWarningLimit: 650,
            rollupOptions: {
                output: {
                    manualChunks: {
                        react: ["react", "react-dom"],
                        charts: ["recharts"],
                        icons: ["lucide-react"]
                    }
                }
            }
        },
        server: {
            port: 5173,
            proxy: {
                "/api": {
                    target: env.VITE_PROXY_TARGET || "http://localhost:8022",
                    changeOrigin: true
                }
            }
        }
    };
});
