import { memo, useEffect, useRef } from 'react';
import {
  CHARACTER_BODY_ROWS,
  CHARACTER_CANVAS_HEIGHT,
  CHARACTER_CANVAS_WIDTH,
  CHARACTER_PALETTE,
  CHARACTER_SCALE,
  characterDisplaySize,
  describeMissingAssets,
  describeUnresolvedAsset,
  planCharacterLayers,
  reportMissingCharacterAsset,
  type CharacterLayer,
  type EquippedMap,
} from '@character-core';
import { assetSource } from '../assetMap';

const spriteCache = new Map<string, HTMLImageElement>();
const spritePromiseCache = new Map<string, Promise<HTMLImageElement>>();
/** 로드에 실패한 URL. 로딩 중인 레이어와 그릴 수 없는 레이어를 구분하는 데 쓴다. */
const failedSources = new Set<string>();

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
      failedSources.add(source);
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

/**
 * BASE PNG를 읽지 못했을 때 그리는 폴백 실루엣 (규격 §3.1 좌표).
 * 빈 캔버스나 깨진 이미지 대신 사람 형태를 보여준다(가이드라인 §4.3).
 */
function paintFallbackSilhouette(context: CanvasRenderingContext2D) {
  context.fillStyle = CHARACTER_PALETTE.rim;
  for (const [y, left, right] of CHARACTER_BODY_ROWS) {
    context.fillRect(left, y, right - left + 1, 1);
  }
  context.fillStyle = CHARACTER_PALETTE.ink;
  for (const [y, left, right] of CHARACTER_BODY_ROWS) {
    context.fillRect(left, y, 1, 1);
    context.fillRect(right, y, 1, 1);
  }
}

/**
 * @returns 이미지를 해석하지 못해 그리지 못한 레이어. 호출 측이 알린다.
 */
function paint(context: CanvasRenderingContext2D, layers: CharacterLayer[]): CharacterLayer[] {
  context.imageSmoothingEnabled = false;
  context.globalCompositeOperation = 'source-over';
  context.clearRect(0, 0, CHARACTER_CANVAS_WIDTH, CHARACTER_CANVAS_HEIGHT);

  const unresolved: CharacterLayer[] = [];
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
    if (sprite) {
      context.drawImage(sprite, 0, 0);
      continue;
    }
    // 아직 로딩 중인 것과 해석 자체가 불가능한 것을 구분한다. 전자는 곧 다시 그려진다.
    if (!url || failedSources.has(url)) {
      unresolved.push(layer);
      if (layer.assetId === 'base_default') paintFallbackSilhouette(context);
    }
  }
  return unresolved;
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
      if (cancelled) return;
      // 로딩이 끝난 뒤에도 못 그린 레이어는 조용히 넘기지 않고 알린다(가이드라인 §4.3).
      for (const layer of paint(context, layers)) {
        if (reportMissingCharacterAsset(layer.assetId)) {
          console.warn(describeUnresolvedAsset(layer.assetId, layer.spriteKey));
        }
      }
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
