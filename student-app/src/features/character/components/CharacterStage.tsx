import { memo, useEffect, useRef } from 'react';
import {
  CHARACTER_CANVAS_HEIGHT,
  CHARACTER_CANVAS_WIDTH,
  CHARACTER_SCALE,
  characterDisplaySize,
  describeMissingAssets,
  planCharacterLayers,
  type CharacterLayer,
  type EquippedMap,
} from '@character-core';
import { assetSource } from '../assetMap';

const spriteCache = new Map<string, HTMLImageElement>();
const spritePromiseCache = new Map<string, Promise<HTMLImageElement>>();

function loadSprite(source: string) {
  const cached = spriteCache.get(source);
  if (cached) return Promise.resolve(cached);

  const pending = spritePromiseCache.get(source);
  if (pending) return pending;

  const promise = new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image();
    image.decoding = 'async';
    image.onload = () => {
      spriteCache.set(source, image);
      spritePromiseCache.delete(source);
      resolve(image);
    };
    image.onerror = () => {
      spritePromiseCache.delete(source);
      reject(new Error(`Character sprite failed to load: ${image.src}`));
    };
    image.src = source;
  });

  spritePromiseCache.set(source, promise);
  return promise;
}

/** 이 레이어가 필요한 이미지 URL 전체 (본체 + 가림 마스크). */
function sourcesFor(layer: CharacterLayer): string[] {
  const urls: string[] = [];
  const main = assetSource(layer.assetId);
  if (main) urls.push(main);
  if (layer.occlusionAssetId) {
    const mask = assetSource(layer.occlusionAssetId);
    if (mask) urls.push(mask);
  }
  return urls;
}

function paint(context: CanvasRenderingContext2D, layers: CharacterLayer[]) {
  context.imageSmoothingEnabled = false;
  context.globalCompositeOperation = 'source-over';
  context.clearRect(0, 0, CHARACTER_CANVAS_WIDTH, CHARACTER_CANVAS_HEIGHT);

  for (const layer of layers) {
    // 가림 마스크는 본체를 그리기 직전에 아래 레이어를 파낸다.
    if (layer.occlusionAssetId) {
      const maskUrl = assetSource(layer.occlusionAssetId);
      const mask = maskUrl ? spriteCache.get(maskUrl) : undefined;
      if (mask) {
        context.save();
        context.globalCompositeOperation = 'destination-out';
        context.drawImage(mask, 0, 0);
        context.restore();
      }
    }
    const url = assetSource(layer.assetId);
    const sprite = url ? spriteCache.get(url) : undefined;
    if (sprite) context.drawImage(sprite, 0, 0);
  }
}

interface CharacterPixelCanvasProps {
  equipped: EquippedMap;
  displayScale?: number;
  className?: string;
  label?: string;
}

/**
 * 모든 미리보기가 하나의 BASE 몸체와 64×64 상품 레이어를 쓴다.
 * 레이어 순서는 core의 planCharacterLayers가 정하므로 Expo와 결과가 같다.
 */
export const CharacterPixelCanvas = memo(function CharacterPixelCanvas({
  equipped,
  displayScale = CHARACTER_SCALE.preview,
  className = '',
  label = 'BASE 도트 캐릭터',
}: CharacterPixelCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const { layers, missingSpriteKeys } = planCharacterLayers(equipped);
  const { width, height } = characterDisplaySize(displayScale);

  useEffect(() => {
    if (!import.meta.env.DEV) return;
    const message = describeMissingAssets(missingSpriteKeys);
    if (message) console.warn(message);
  }, [missingSpriteKeys.join(',')]);

  useEffect(() => {
    const context = canvasRef.current?.getContext('2d');
    if (!context) return;
    let cancelled = false;

    // 캐시에 있는 것으로 먼저 그려 깜빡임을 막고, 나머지가 오면 다시 그린다.
    paint(context, layers);

    const urls = layers.flatMap(sourcesFor);
    void Promise.all(urls.map((url) => loadSprite(url).catch(() => null))).then(() => {
      if (!cancelled) paint(context, layers);
    });

    return () => {
      cancelled = true;
    };
  }, [layers.map((layer) => layer.key).join(',')]);

  return (
    <canvas
      aria-label={label}
      className={`character-pixel-canvas ${className}`.trim()}
      height={CHARACTER_CANVAS_HEIGHT}
      ref={canvasRef}
      role="img"
      style={{ width, height }}
      width={CHARACTER_CANVAS_WIDTH}
    />
  );
});

export function CharacterStage({ equipped }: { equipped: EquippedMap }) {
  return (
    <div className="character-stage" aria-label="BASE 캐릭터 착용 미리보기">
      <CharacterPixelCanvas displayScale={CHARACTER_SCALE.preview} equipped={equipped} />
    </div>
  );
}
