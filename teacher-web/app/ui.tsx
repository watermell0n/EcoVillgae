// 화면 공통 레이아웃 조각입니다.
// 소유: 교사 프런트 — AI 컴포넌트도 가져다 쓰므로 변경 시 팀에 공유합니다.

import type { ReactNode } from "react";

export function PlainSection({
  title,
  description,
  children
}: Readonly<{ title: string; description?: string; children: ReactNode }>) {
  return (
    <section className="plain-section">
      <div className="section-title">
        <h2>{title}</h2>
        {description ? <p>{description}</p> : null}
      </div>
      {children}
    </section>
  );
}
