import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "이코빌리지 교사 콘솔",
  description: "경제교실 운영을 위한 교사용 화면"
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
