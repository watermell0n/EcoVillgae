import { memo, useEffect, useRef } from 'react';
import { BASE_CHARACTER_PROFILE } from '../baseProfile';
import { basicLayerProfileFor } from '../basicLayerProfile';
import { CATALOG_BY_ID } from '../catalog';
import {
  CHARACTER_PIXEL_HEIGHT,
  CHARACTER_PIXEL_WIDTH,
  renderPixelCharacter,
  type CharacterLayerSprites,
} from '../pixelRenderer';
import type { EquippedMap, Slot } from '../types';

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

interface LayerRequest {
  slot: Slot;
  source: string;
  placement: 'BEHIND_BASE' | 'FRONT';
  part: 'back' | 'front' | 'occlusion' | 'overlay';
}

interface LayerParts {
  back?: CanvasImageSource;
  front?: CanvasImageSource;
  occlusion?: CanvasImageSource;
  overlay?: CanvasImageSource;
}

type CharacterLayerParts = Partial<Record<Slot, LayerParts>>;

function canRenderAtMasterResolution(equipped: EquippedMap): boolean {
  return Object.values(equipped).every((itemId) => {
    if (!itemId) return true;
    return basicLayerProfileFor(CATALOG_BY_ID[itemId]?.spriteKey) !== null;
  });
}

function layerRequestsFor(equipped: EquippedMap, useMasterSprites: boolean): LayerRequest[] {
  const requests: LayerRequest[] = [];

  for (const [slot, itemId] of Object.entries(equipped) as Array<[Slot, string | null]>) {
    if (!itemId) continue;
    const item = CATALOG_BY_ID[itemId];
    const profile = basicLayerProfileFor(item?.spriteKey);
    if (!profile || profile.slot !== slot) continue;

    requests.push({
      slot,
      source: useMasterSprites ? profile.masterSprite : profile.renderSprite,
      placement: profile.placement,
      part:
        useMasterSprites && profile.frontPlacement === 'OVERLAY'
          ? 'overlay'
          : 'front',
    });

    if (useMasterSprites && profile.masterBackSprite) {
      requests.push({
        slot,
        source: profile.masterBackSprite,
        placement: profile.placement,
        part: 'back',
      });
    }

    if (useMasterSprites && profile.masterOcclusionSprite) {
      requests.push({
        slot,
        source: profile.masterOcclusionSprite,
        placement: profile.placement,
        part: 'occlusion',
      });
    }
  }

  return requests;
}

function assignLayerPart(
  layers: CharacterLayerParts,
  request: LayerRequest,
  image: CanvasImageSource,
) {
  const parts = layers[request.slot] ?? {};
  parts[request.part] = image;
  layers[request.slot] = parts;
}

function cachedLayerSprites(requests: LayerRequest[]): CharacterLayerParts {
  const layers: CharacterLayerParts = {};
  for (const request of requests) {
    const image = spriteCache.get(request.source);
    if (image) assignLayerPart(layers, request, image);
  }
  return layers;
}

const MASTER_LAYER_ORDER: Slot[] = [
  'BACK',
  'TOP',
  'BOTTOM',
  'FACE',
  'HAT',
];

function applyOcclusion(
  context: CanvasRenderingContext2D,
  occlusion?: CanvasImageSource,
) {
  if (!occlusion) return;
  context.save();
  context.globalCompositeOperation = 'destination-out';
  context.drawImage(occlusion, 0, 0);
  context.restore();
}

function renderMasterCharacter(
  context: CanvasRenderingContext2D,
  baseSprite?: CanvasImageSource,
  layerSprites: CharacterLayerParts = {},
  requests: LayerRequest[] = [],
) {
  context.imageSmoothingEnabled = false;
  context.globalCompositeOperation = 'source-over';
  context.clearRect(
    0,
    0,
    BASE_CHARACTER_PROFILE.masterWidth,
    BASE_CHARACTER_PROFILE.masterHeight,
  );

  const behindBaseSlots = new Set(
    requests
      .filter((request) => request.placement === 'BEHIND_BASE')
      .map((request) => request.slot),
  );

  // Back pieces such as the headphone band sit behind the BASE silhouette.
  for (const slot of [...MASTER_LAYER_ORDER, 'SHOES'] as Slot[]) {
    const sprite = layerSprites[slot]?.back;
    if (sprite) context.drawImage(sprite, 0, 0);
  }

  for (const slot of [...MASTER_LAYER_ORDER, 'SHOES'] as Slot[]) {
    if (!behindBaseSlots.has(slot)) continue;
    const parts = layerSprites[slot];
    applyOcclusion(context, parts?.occlusion);
    if (parts?.front) context.drawImage(parts.front, 0, 0);
  }

  if (baseSprite) {
    context.drawImage(baseSprite, 0, 0);
  }

  for (const slot of MASTER_LAYER_ORDER) {
    if (behindBaseSlots.has(slot)) continue;
    const parts = layerSprites[slot];
    applyOcclusion(context, parts?.occlusion);
    if (parts?.front) context.drawImage(parts.front, 0, 0);
  }

  // Hat pixels cover the rear headphone band, while the ear cups return to
  // the foreground after the hat and face layers.
  for (const slot of [...MASTER_LAYER_ORDER, 'SHOES'] as Slot[]) {
    const overlay = layerSprites[slot]?.overlay;
    if (overlay) context.drawImage(overlay, 0, 0);
  }

  if (!behindBaseSlots.has('SHOES')) {
    const shoes = layerSprites.SHOES;
    applyOcclusion(context, shoes?.occlusion);
    if (shoes?.front) context.drawImage(shoes.front, 0, 0);
  }
}

async function loadLayerSprites(requests: LayerRequest[]) {
  const layers: CharacterLayerParts = {};
  await Promise.all(
    requests.map(async (request) => {
      try {
        assignLayerPart(layers, request, await loadSprite(request.source));
      } catch {
        // The procedural item remains as a fallback when a sprite is unavailable.
      }
    }),
  );
  return layers;
}

function legacyFrontLayers(parts: CharacterLayerParts): CharacterLayerSprites {
  const layers: CharacterLayerSprites = {};
  for (const [slot, layerParts] of Object.entries(parts) as Array<
    [Slot, LayerParts]
  >) {
    const sprite = layerParts.front ?? layerParts.overlay;
    if (sprite) layers[slot] = sprite;
  }
  return layers;
}

interface CharacterPixelCanvasProps {
  equipped: EquippedMap;
  displayScale?: number;
  className?: string;
  label?: string;
}

/**
 * Every preview uses one fixed BASE body. Basic-theme products load as
 * transparent item sprites at their native 64x64 resolution. Products not
 * migrated yet retain the 48x56 procedural fallback until their layer assets
 * are ready.
 */
export const CharacterPixelCanvas = memo(function CharacterPixelCanvas({
  equipped,
  displayScale = 6,
  className = '',
  label = 'BASE 도트 캐릭터',
}: CharacterPixelCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const useMasterSprites = canRenderAtMasterResolution(equipped);
  const pixelWidth = useMasterSprites
    ? BASE_CHARACTER_PROFILE.masterWidth
    : CHARACTER_PIXEL_WIDTH;
  const pixelHeight = useMasterSprites
    ? BASE_CHARACTER_PROFILE.masterHeight
    : CHARACTER_PIXEL_HEIGHT;
  const effectiveDisplayScale = useMasterSprites
    ? Math.max(1, displayScale - 1)
    : displayScale;

  useEffect(() => {
    const context = canvasRef.current?.getContext('2d');
    if (!context) return;

    const requests = layerRequestsFor(equipped, useMasterSprites);
    const baseSource = useMasterSprites
      ? BASE_CHARACTER_PROFILE.masterSprite
      : BASE_CHARACTER_PROFILE.renderSprite;
    let cancelled = false;

    const render = (
      base: CanvasImageSource | undefined,
      layers: CharacterLayerParts,
    ) => {
      if (useMasterSprites) {
        renderMasterCharacter(context, base, layers, requests);
      } else {
        renderPixelCharacter(context, equipped, base, legacyFrontLayers(layers));
      }
    };

    render(spriteCache.get(baseSource), cachedLayerSprites(requests));

    void Promise.all([
      loadSprite(baseSource).catch(() => null),
      loadLayerSprites(requests),
    ]).then(([base, layers]) => {
      if (!cancelled) {
        render(base ?? undefined, layers);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [equipped, useMasterSprites]);

  return (
    <canvas
      aria-label={label}
      className={`character-pixel-canvas ${className}`.trim()}
      data-body={BASE_CHARACTER_PROFILE.id}
      height={pixelHeight}
      ref={canvasRef}
      role="img"
      style={{
        width: pixelWidth * effectiveDisplayScale,
        height: pixelHeight * effectiveDisplayScale,
      }}
      width={pixelWidth}
    />
  );
});

export function CharacterStage({ equipped }: { equipped: EquippedMap }) {
  return (
    <div className="character-stage" aria-label="BASE 캐릭터 착용 미리보기">
      <CharacterPixelCanvas displayScale={5} equipped={equipped} />
    </div>
  );
}
