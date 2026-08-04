import { useEffect, useMemo, useRef, useState } from 'react';
import { Application, Container, Graphics, Text } from 'pixi.js';
import { assetProfileFor, BODY_STATES, frameKeyFor } from '../assetProfile';
import type { CosmeticDTO } from '../types';
import { BODY_LABEL } from '../types';
import { CELL, GRID_H, GRID_W, drawBase, drawCosmetic } from '../pixi/art';

interface RigQaMatrixProps {
  catalog: CosmeticDTO[];
}

const CELL_WIDTH = GRID_W * CELL;
const CELL_HEIGHT = GRID_H * CELL;
const LABEL_WIDTH = 180;
const HEADER_HEIGHT = 36;
const ROWS_PER_PAGE = 12;
const BEHIND_BODY = new Set(['SCENE', 'FLOOR', 'BACK', 'TAIL']);

export function RigQaMatrix({ catalog }: RigQaMatrixProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const [page, setPage] = useState(0);
  const pageCount = Math.max(1, Math.ceil(catalog.length / ROWS_PER_PAGE));
  const pageItems = useMemo(
    () => catalog.slice(page * ROWS_PER_PAGE, (page + 1) * ROWS_PER_PAGE),
    [catalog, page],
  );

  useEffect(() => {
    if (page >= pageCount) setPage(pageCount - 1);
  }, [page, pageCount]);

  useEffect(() => {
    const host = hostRef.current;
    if (!host || pageItems.length === 0) return;

    let cancelled = false;
    let initialized = false;
    const app = new Application();

    void app.init({
      width: LABEL_WIDTH + BODY_STATES.length * CELL_WIDTH,
      height: HEADER_HEIGHT + pageItems.length * CELL_HEIGHT,
      backgroundColor: 0xf7f8fa,
      antialias: false,
      resolution: 1,
      autoDensity: false,
      preference: 'webgl',
    }).then(() => {
      initialized = true;
      if (cancelled) {
        app.destroy(true, { children: true });
        return;
      }

      app.canvas.style.imageRendering = 'pixelated';
      const root = new Container();
      root.position.set(0, 0);
      app.stage.addChild(root);

      root.addChild(new Text({
        text: '아이템 / 체형',
        style: { fill: 0x596474, fontSize: 13, fontWeight: '700' },
        x: 10,
        y: 10,
      }));

      BODY_STATES.forEach((body, column) => {
        root.addChild(new Text({
          text: BODY_LABEL[body],
          style: { fill: 0x238873, fontSize: 13, fontWeight: '700' },
          x: LABEL_WIDTH + column * CELL_WIDTH + 8,
          y: 10,
        }));
      });

      pageItems.forEach((item, row) => {
        const rowY = HEADER_HEIGHT + row * CELL_HEIGHT;
        root.addChild(new Text({
          text: `${item.name}\n${frameKeyFor(item, 'BASE')}`,
          style: {
            fill: 0x3f4857,
            fontSize: 11,
            lineHeight: 15,
            wordWrap: true,
            wordWrapWidth: LABEL_WIDTH - 16,
          },
          x: 8,
          y: rowY + 8,
        }));

        BODY_STATES.forEach((body, column) => {
          const cell = new Container();
          cell.position.set(LABEL_WIDTH + column * CELL_WIDTH, rowY);

          const grid = new Graphics();
          grid
            .rect(0, 0, CELL_WIDTH, CELL_HEIGHT)
            .fill({ color: 0xffffff })
            .stroke({ color: 0xe4e7eb, width: 1 });
          cell.addChild(grid);

          const base = new Graphics();
          const cosmetic = new Graphics();
          drawBase(base, body);
          drawCosmetic(cosmetic, item.slot, item.spriteKey, body);

          if (BEHIND_BODY.has(assetProfileFor(item).rigSlot)) {
            cell.addChild(cosmetic, base);
          } else {
            cell.addChild(base, cosmetic);
          }
          root.addChild(cell);
        });
      });

      host.replaceChildren(app.canvas);
    });

    return () => {
      cancelled = true;
      host.replaceChildren();
      if (initialized) app.destroy(true, { children: true });
    };
  }, [pageItems]);

  return (
    <section aria-label={`${catalog.length}개 아이템과 4개 체형 자동 QA 매트릭스`}>
      <div className="rig-qa-pagination">
        <button type="button" onClick={() => setPage((value) => Math.max(0, value - 1))} disabled={page === 0}>
          이전
        </button>
        <strong>{page + 1} / {pageCount} 페이지</strong>
        <button
          type="button"
          onClick={() => setPage((value) => Math.min(pageCount - 1, value + 1))}
          disabled={page === pageCount - 1}
        >
          다음
        </button>
      </div>
      <div className="rig-qa-canvas" ref={hostRef} />
    </section>
  );
}
