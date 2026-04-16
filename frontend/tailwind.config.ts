import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        canvas: "#08111f",
        panel: "#101b2d",
        panelSoft: "#18263d",
        accent: "#4fd1c5",
        accentWarm: "#f6ad55",
        accentDanger: "#fb7185"
      },
      boxShadow: {
        glow: "0 24px 80px rgba(5, 12, 24, 0.45)"
      }
    }
  },
  plugins: []
} satisfies Config;
