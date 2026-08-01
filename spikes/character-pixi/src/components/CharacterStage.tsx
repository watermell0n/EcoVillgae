import { useEffect, useRef } from 'react';
import { PaperDoll } from '../pixi/PaperDoll';
import type { BodyState, EquippedMap } from '../types';

interface CharacterStageProps {
  equipped: EquippedMap;
  bodyState: BodyState;
}

export function CharacterStage({ equipped, bodyState }: CharacterStageProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const paperDollRef = useRef<PaperDoll | null>(null);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;

    const paperDoll = new PaperDoll();
    let cancelled = false;

    void paperDoll.init(host).then(() => {
      if (cancelled) {
        paperDoll.destroy();
        return;
      }

      paperDollRef.current = paperDoll;
      paperDoll.update(equipped, bodyState);
    });

    return () => {
      cancelled = true;
      if (paperDollRef.current === paperDoll) {
        paperDoll.destroy();
        paperDollRef.current = null;
      }
      host.replaceChildren();
    };
  }, []);

  useEffect(() => {
    paperDollRef.current?.update(equipped, bodyState);
  }, [equipped, bodyState]);

  return <div className="character-stage" ref={hostRef} aria-label="캐릭터 미리보기" />;
}
