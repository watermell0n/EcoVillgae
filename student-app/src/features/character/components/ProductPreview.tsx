import { memo, useEffect, useRef, useState } from 'react';
import { BODY_WIDTH_RATIO, catalogArtFor, type CatalogArtFrame } from '../catalogArt';
import type { BodyState, CosmeticDTO } from '../types';

const OUTPUT_WIDTH = 320;
const OUTPUT_HEIGHT = 340;
const FRAME_CACHE_LIMIT = 96;
const NEUTRAL_BACKGROUND = '#fbfaf7';
const OUTPUT_SIDE_PADDING = 13;
const OUTPUT_TOP_PADDING = 10;
const OUTPUT_FLOOR_GAP = 6;

const imageCache = new Map<string, Promise<HTMLImageElement>>();
const frameCache = new Map<string, HTMLCanvasElement>();
const preparedFrameCache = new Map<string, Promise<PreparedCatalogFrame>>();
const fitEnvelopeCache = new Map<string, Bounds>();

interface Bounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface ForegroundComponent extends Bounds {
  area: number;
}

interface SubjectAnalysis {
  bounds: Bounds;
  anchorX: number;
  pixelCell: number;
}

interface PreparedCatalogFrame {
  canvas: HTMLCanvasElement;
  frame: CatalogArtFrame;
  bounds: Bounds;
  anchorX: number;
  pixelCell: number;
}

function loadImage(src: string): Promise<HTMLImageElement> {
  const cached = imageCache.get(src);
  if (cached) return cached;

  const request = new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image();
    image.decoding = 'async';
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(`캐릭터 참고 이미지를 불러오지 못했습니다: ${src}`));
    image.src = src;
  });
  imageCache.set(src, request);
  return request;
}

function canvas2d(canvas: HTMLCanvasElement): CanvasRenderingContext2D {
  const context = canvas.getContext('2d', { alpha: false });
  if (!context) throw new Error('2D 캔버스를 만들 수 없습니다.');
  context.imageSmoothingEnabled = false;
  return context;
}

function createCanvas(width: number, height: number): HTMLCanvasElement {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  return canvas;
}

function fillNeutral(canvas: HTMLCanvasElement): CanvasRenderingContext2D {
  const context = canvas2d(canvas);
  context.fillStyle = NEUTRAL_BACKGROUND;
  context.fillRect(0, 0, canvas.width, canvas.height);
  return context;
}

function safeCrop(image: HTMLImageElement, frame: CatalogArtFrame): HTMLCanvasElement {
  const crop = createCanvas(frame.width, frame.height);
  const context = fillNeutral(crop);
  const sourceWidth = image.naturalWidth || image.width;
  const sourceHeight = image.naturalHeight || image.height;
  const sourceLeft = Math.max(0, frame.x);
  const sourceTop = Math.max(0, frame.y);
  const sourceRight = Math.min(sourceWidth, frame.x + frame.width);
  const sourceBottom = Math.min(sourceHeight, frame.y + frame.height);

  if (sourceRight <= sourceLeft || sourceBottom <= sourceTop) {
    throw new Error(`상품 이미지 범위를 벗어난 프레임입니다: ${frame.src}`);
  }

  const width = sourceRight - sourceLeft;
  const height = sourceBottom - sourceTop;
  context.drawImage(
    image,
    sourceLeft,
    sourceTop,
    width,
    height,
    sourceLeft - frame.x,
    sourceTop - frame.y,
    width,
    height,
  );
  return crop;
}

function componentDistance(a: Bounds, b: Bounds) {
  const aRight = a.x + a.width - 1;
  const bRight = b.x + b.width - 1;
  const aBottom = a.y + a.height - 1;
  const bBottom = b.y + b.height - 1;
  const horizontal = Math.max(0, Math.max(a.x - bRight - 1, b.x - aRight - 1));
  const vertical = Math.max(0, Math.max(a.y - bBottom - 1, b.y - aBottom - 1));
  return { horizontal, vertical };
}

function unionBounds(a: Bounds, b: Bounds): Bounds {
  const left = Math.min(a.x, b.x);
  const top = Math.min(a.y, b.y);
  const right = Math.max(a.x + a.width, b.x + b.width);
  const bottom = Math.max(a.y + a.height, b.y + b.height);
  return { x: left, y: top, width: right - left, height: bottom - top };
}

function foregroundComponents(canvas: HTMLCanvasElement): {
  components: ForegroundComponent[];
  pixels: Uint8ClampedArray;
} {
  const context = canvas2d(canvas);
  const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
  const size = canvas.width * canvas.height;
  const mask = new Uint8Array(size);
  const visited = new Uint8Array(size);
  const queue = new Int32Array(size);
  const insetX = Math.max(3, Math.round(canvas.width * 0.03));
  const insetY = Math.max(3, Math.round(canvas.height * 0.02));

  for (let y = insetY; y < canvas.height - insetY; y += 1) {
    for (let x = insetX; x < canvas.width - insetX; x += 1) {
      const pixelIndex = (y * canvas.width + x) * 4;
      const red = pixels[pixelIndex];
      const green = pixels[pixelIndex + 1];
      const blue = pixels[pixelIndex + 2];
      const max = Math.max(red, green, blue);
      const min = Math.min(red, green, blue);
      if (max - min > 24 || max < 185) mask[y * canvas.width + x] = 1;
    }
  }

  const components: ForegroundComponent[] = [];
  for (let start = 0; start < size; start += 1) {
    if (mask[start] === 0 || visited[start] === 1) continue;

    let head = 0;
    let tail = 1;
    queue[0] = start;
    visited[start] = 1;
    let area = 0;
    let left = canvas.width;
    let right = 0;
    let top = canvas.height;
    let bottom = 0;

    while (head < tail) {
      const index = queue[head];
      head += 1;
      const y = Math.floor(index / canvas.width);
      const x = index - y * canvas.width;
      area += 1;
      left = Math.min(left, x);
      right = Math.max(right, x);
      top = Math.min(top, y);
      bottom = Math.max(bottom, y);

      for (let neighborY = Math.max(0, y - 1); neighborY <= Math.min(canvas.height - 1, y + 1); neighborY += 1) {
        for (let neighborX = Math.max(0, x - 1); neighborX <= Math.min(canvas.width - 1, x + 1); neighborX += 1) {
          const neighbor = neighborY * canvas.width + neighborX;
          if (mask[neighbor] === 0 || visited[neighbor] === 1) continue;
          visited[neighbor] = 1;
          queue[tail] = neighbor;
          tail += 1;
        }
      }
    }

    components.push({
      area,
      x: left,
      y: top,
      width: right - left + 1,
      height: bottom - top + 1,
    });
  }

  components.sort((a, b) => b.area - a.area);
  return { components, pixels };
}

function visibleContentBounds(canvas: HTMLCanvasElement): Bounds | null {
  const pixels = canvas2d(canvas).getImageData(0, 0, canvas.width, canvas.height).data;
  let left = canvas.width;
  let right = -1;
  let top = canvas.height;
  let bottom = -1;

  for (let y = 0; y < canvas.height; y += 1) {
    for (let x = 0; x < canvas.width; x += 1) {
      const index = (y * canvas.width + x) * 4;
      const red = pixels[index];
      const green = pixels[index + 1];
      const blue = pixels[index + 2];
      const max = Math.max(red, green, blue);
      const min = Math.min(red, green, blue);
      if (max - min <= 24 && max >= 185) continue;
      left = Math.min(left, x);
      right = Math.max(right, x);
      top = Math.min(top, y);
      bottom = Math.max(bottom, y);
    }
  }

  if (right < left || bottom < top) return null;
  return {
    x: left,
    y: top,
    width: right - left + 1,
    height: bottom - top + 1,
  };
}

function estimateBodyAnchorX(
  pixels: Uint8ClampedArray,
  canvasWidth: number,
  bounds: Bounds,
  pixelCell: number,
): number {
  const centers: number[] = [];
  const startY = bounds.y + Math.round(bounds.height * 0.25);
  const endY = bounds.y + Math.round(bounds.height * 0.56);
  const minSpan = Math.max(pixelCell * 4, bounds.width * 0.2);
  const maxSpan = bounds.width * 0.88;

  for (let y = startY; y <= endY; y += 1) {
    let left = bounds.x + bounds.width;
    let right = bounds.x - 1;
    for (let x = bounds.x; x < bounds.x + bounds.width; x += 1) {
      const index = (y * canvasWidth + x) * 4;
      if (Math.max(pixels[index], pixels[index + 1], pixels[index + 2]) >= 145) continue;
      left = Math.min(left, x);
      right = Math.max(right, x);
    }
    const span = right - left + 1;
    if (span >= minSpan && span <= maxSpan) centers.push((left + right) / 2);
  }

  if (centers.length === 0) return Math.round(bounds.x + bounds.width / 2);
  centers.sort((a, b) => a - b);
  return Math.round(centers[Math.floor(centers.length / 2)]);
}

function estimateFaceAnchorX(
  pixels: Uint8ClampedArray,
  canvasWidth: number,
  canvasHeight: number,
  bounds: Bounds,
  pixelCell: number,
): number | null {
  const size = canvasWidth * canvasHeight;
  const mask = new Uint8Array(size);
  const visited = new Uint8Array(size);
  const queue = new Int32Array(size);
  const right = Math.min(canvasWidth, bounds.x + bounds.width);
  const bottom = Math.min(canvasHeight, bounds.y + bounds.height);

  for (let y = Math.max(0, bounds.y); y < bottom; y += 1) {
    for (let x = Math.max(0, bounds.x); x < right; x += 1) {
      const pixelIndex = (y * canvasWidth + x) * 4;
      if (Math.max(
        pixels[pixelIndex],
        pixels[pixelIndex + 1],
        pixels[pixelIndex + 2],
      ) <= 72) {
        mask[y * canvasWidth + x] = 1;
      }
    }
  }

  const components: ForegroundComponent[] = [];
  for (let startY = Math.max(0, bounds.y); startY < bottom; startY += 1) {
    for (let startX = Math.max(0, bounds.x); startX < right; startX += 1) {
      const start = startY * canvasWidth + startX;
      if (mask[start] === 0 || visited[start] === 1) continue;

      let head = 0;
      let tail = 1;
      queue[0] = start;
      visited[start] = 1;
      let area = 0;
      let left = canvasWidth;
      let componentRight = 0;
      let top = canvasHeight;
      let componentBottom = 0;

      while (head < tail) {
        const index = queue[head];
        head += 1;
        const y = Math.floor(index / canvasWidth);
        const x = index - y * canvasWidth;
        area += 1;
        left = Math.min(left, x);
        componentRight = Math.max(componentRight, x);
        top = Math.min(top, y);
        componentBottom = Math.max(componentBottom, y);

        for (let neighborY = Math.max(bounds.y, y - 1); neighborY <= Math.min(bottom - 1, y + 1); neighborY += 1) {
          for (let neighborX = Math.max(bounds.x, x - 1); neighborX <= Math.min(right - 1, x + 1); neighborX += 1) {
            const neighbor = neighborY * canvasWidth + neighborX;
            if (mask[neighbor] === 0 || visited[neighbor] === 1) continue;
            visited[neighbor] = 1;
            queue[tail] = neighbor;
            tail += 1;
          }
        }
      }

      components.push({
        area,
        x: left,
        y: top,
        width: componentRight - left + 1,
        height: componentBottom - top + 1,
      });
    }
  }

  const faceBottom = bounds.y + bounds.height * 0.66;
  const eyes = components.filter((component) => (
    component.area >= pixelCell
    && component.area <= pixelCell * pixelCell * 14
    && component.width >= Math.max(1, Math.floor(pixelCell * 0.45))
    && component.width <= pixelCell * 4
    && component.height >= pixelCell
    && component.height <= pixelCell * 7
    && component.height >= component.width * 0.75
    && component.y + component.height / 2 < faceBottom
  ));
  const mouths = components.filter((component) => (
    component.area >= pixelCell
    && component.area <= pixelCell * pixelCell * 12
    && component.width >= pixelCell * 1.4
    && component.width <= pixelCell * 8
    && component.height >= 1
    && component.height <= pixelCell * 3
    && component.width >= component.height * 1.3
    && component.y + component.height / 2 < faceBottom
  ));

  let best: { score: number; center: number } | null = null;
  for (let leftIndex = 0; leftIndex < eyes.length; leftIndex += 1) {
    for (let rightIndex = leftIndex + 1; rightIndex < eyes.length; rightIndex += 1) {
      const first = eyes[leftIndex];
      const second = eyes[rightIndex];
      const leftEye = first.x < second.x ? first : second;
      const rightEye = first.x < second.x ? second : first;
      const leftCenterX = leftEye.x + (leftEye.width - 1) / 2;
      const rightCenterX = rightEye.x + (rightEye.width - 1) / 2;
      const leftCenterY = leftEye.y + (leftEye.height - 1) / 2;
      const rightCenterY = rightEye.y + (rightEye.height - 1) / 2;
      const separation = rightCenterX - leftCenterX;
      if (separation < pixelCell * 2 || separation > pixelCell * 20) continue;
      if (Math.abs(leftCenterY - rightCenterY) > pixelCell * 1.5) continue;

      const eyeCenterX = (leftCenterX + rightCenterX) / 2;
      const eyeCenterY = (leftCenterY + rightCenterY) / 2;
      for (const mouth of mouths) {
        const mouthCenterX = mouth.x + (mouth.width - 1) / 2;
        const mouthCenterY = mouth.y + (mouth.height - 1) / 2;
        const verticalDistance = mouthCenterY - eyeCenterY;
        if (verticalDistance < pixelCell || verticalDistance > pixelCell * 10) continue;
        if (Math.abs(mouthCenterX - eyeCenterX) > pixelCell * 4) continue;

        const areaRatio = Math.max(leftEye.area, rightEye.area)
          / Math.max(1, Math.min(leftEye.area, rightEye.area));
        const score = Math.abs(leftCenterY - rightCenterY) * 5
          + Math.abs(mouthCenterX - eyeCenterX) * 3
          + Math.abs(areaRatio - 1) * 18
          + Math.abs(separation - pixelCell * 6) * 0.2;
        const center = (eyeCenterX * 2 + mouthCenterX) / 3;
        if (!best || score < best.score) best = { score, center };
      }
    }
  }

  return best ? Math.round(best.center) : null;
}

function analyzeSubject(
  canvas: HTMLCanvasElement,
  explicitAnchorX?: number,
): SubjectAnalysis {
  const pixelCell = Math.max(3, Math.round(canvas.width / 60));
  const { components, pixels } = foregroundComponents(canvas);
  const primary = components[0];

  if (!primary) {
    return {
      bounds: { x: 0, y: 0, width: canvas.width, height: canvas.height },
      anchorX: explicitAnchorX ?? Math.round(canvas.width / 2),
      pixelCell,
    };
  }

  let subjectBounds: Bounds = primary;
  const joinDistance = pixelCell * 2;
  const minimumSatelliteArea = Math.max(8, Math.round((pixelCell * pixelCell) / 2));
  let changed = true;
  while (changed) {
    changed = false;
    for (const component of components.slice(1)) {
      if (component.area < minimumSatelliteArea) continue;
      const distance = componentDistance(subjectBounds, component);
      if (distance.horizontal > joinDistance || distance.vertical > joinDistance) continue;
      const joined = unionBounds(subjectBounds, component);
      if (
        joined.x !== subjectBounds.x
        || joined.y !== subjectBounds.y
        || joined.width !== subjectBounds.width
        || joined.height !== subjectBounds.height
      ) {
        subjectBounds = joined;
        changed = true;
      }
    }
  }

  const anchorX = explicitAnchorX
    ?? estimateFaceAnchorX(pixels, canvas.width, canvas.height, subjectBounds, pixelCell)
    ?? estimateBodyAnchorX(pixels, canvas.width, subjectBounds, pixelCell);
  const padding = pixelCell;
  const x = Math.max(0, subjectBounds.x - padding);
  const y = Math.max(0, subjectBounds.y - padding);
  const right = Math.min(canvas.width, subjectBounds.x + subjectBounds.width + padding);
  const bottom = Math.min(canvas.height, subjectBounds.y + subjectBounds.height + padding);

  return {
    bounds: { x, y, width: right - x, height: bottom - y },
    anchorX,
    pixelCell,
  };
}

function isolateAndCenterSubject(
  crop: HTMLCanvasElement,
  frame: CatalogArtFrame,
  analysis: SubjectAnalysis,
  isBackground: boolean,
): PreparedCatalogFrame {
  const canvas = createCanvas(frame.width, frame.height);
  const context = fillNeutral(canvas);
  const desiredAnchorX = isBackground ? analysis.anchorX : Math.round(frame.width / 2);
  const requestedOffset = desiredAnchorX - analysis.anchorX;
  const minimumOffset = -analysis.bounds.x;
  const maximumOffset = frame.width - (analysis.bounds.x + analysis.bounds.width);
  const offsetX = isBackground
    ? 0
    : Math.max(minimumOffset, Math.min(maximumOffset, requestedOffset));

  context.drawImage(
    crop,
    analysis.bounds.x,
    analysis.bounds.y,
    analysis.bounds.width,
    analysis.bounds.height,
    analysis.bounds.x + offsetX,
    analysis.bounds.y,
    analysis.bounds.width,
    analysis.bounds.height,
  );

  // Reference sheets have inconsistent empty padding inside their cards.
  // Re-measure the copied subject so blank card space cannot make otherwise
  // valid products (notably the dinosaur suit, wings and tail) render tiny.
  const visibleBounds = visibleContentBounds(canvas) ?? {
    ...analysis.bounds,
    x: analysis.bounds.x + offsetX,
  };

  return {
    canvas,
    frame,
    bounds: visibleBounds,
    anchorX: analysis.anchorX + offsetX,
    pixelCell: analysis.pixelCell,
  };
}

function smootherStep(value: number): number {
  const clamped = Math.max(0, Math.min(1, value));
  return clamped * clamped * clamped * (clamped * (clamped * 6 - 15) + 10);
}

function rowFitRatio(bodyState: BodyState, normalizedY: number): number {
  const targetRatio = BODY_WIDTH_RATIO[bodyState];

  if (bodyState === 'CHUBBY') {
    const progress = smootherStep((normalizedY - 0.44) / (0.64 - 0.44));
    return 1 + (targetRatio - 1) * progress;
  }

  if (bodyState === 'SLIM' || bodyState === 'VERY_SLIM') {
    // Begin the eased calculation above the shoulder, while cell snapping keeps
    // the fixed head untouched until the silhouette reaches its first body step.
    // The longer band avoids a boxy torso and keeps the slim tiers gently tapered.
    const progress = smootherStep((normalizedY - 0.35) / (0.82 - 0.35));
    return 1 + (targetRatio - 1) * progress;
  }

  return targetRatio;
}

function morphBody(
  source: HTMLCanvasElement,
  prepared: PreparedCatalogFrame,
  bodyState: BodyState,
  slot: CosmeticDTO['slot'],
): HTMLCanvasElement {
  if (bodyState === 'BASE' || slot === 'BACKGROUND') return source;

  const { frame, bounds, anchorX, pixelCell } = prepared;
  const result = createCanvas(frame.width, frame.height);
  const context = fillNeutral(result);
  const bandX = bounds.x;
  const bandWidth = bounds.width;

  for (let y = 0; y < frame.height; y += 1) {
    context.drawImage(source, 0, y, frame.width, 1, 0, y, frame.width, 1);

    const snappedY = Math.floor(y / pixelCell) * pixelCell;
    const normalizedY = (snappedY - bounds.y) / Math.max(1, bounds.height - 1);
    const rowRatio = rowFitRatio(bodyState, normalizedY);
    if (rowRatio === 1) continue;
    const rawDelta = bandWidth * rowRatio - bandWidth;
    let fittedWidth = Math.max(
      1,
      bandWidth + Math.round(rawDelta / (pixelCell * 2)) * pixelCell * 2,
    );
    const leftShare = Math.max(0.01, (anchorX - bandX) / bandWidth);
    const rightShare = Math.max(0.01, (bandX + bandWidth - anchorX) / bandWidth);
    const maximumWidth = Math.min(anchorX / leftShare, (frame.width - anchorX) / rightShare);
    fittedWidth = Math.min(fittedWidth, Math.max(1, Math.floor(maximumWidth)));
    const fittedRatio = fittedWidth / bandWidth;
    const fittedX = Math.round(anchorX - (anchorX - bandX) * fittedRatio);
    const clearX = Math.max(0, Math.min(bandX, fittedX));
    const clearRight = Math.min(
      frame.width,
      Math.max(bandX + bandWidth, fittedX + fittedWidth),
    );

    // Clear both the old and new silhouettes before drawing the fitted row once.
    // This prevents doubled outlines on wider tiers and leaves OOB areas neutral.
    context.fillStyle = NEUTRAL_BACKGROUND;
    context.fillRect(clearX, y, clearRight - clearX, 1);
    context.drawImage(source, bandX, y, bandWidth, 1, fittedX, y, fittedWidth, 1);
  }
  return result;
}

async function prepareCatalogFrame(item: CosmeticDTO): Promise<PreparedCatalogFrame> {
  const cached = preparedFrameCache.get(item.spriteKey);
  if (cached) return cached;

  const request = (async () => {
    const frame = catalogArtFor(item);
    if (!frame) throw new Error(`${item.spriteKey} 상품 이미지가 없습니다.`);
    const image = await loadImage(frame.src);
    const crop = safeCrop(image, frame);
    const analysis = analyzeSubject(crop, frame.morphCenterX);
    return isolateAndCenterSubject(crop, frame, analysis, item.slot === 'BACKGROUND');
  })();

  preparedFrameCache.set(item.spriteKey, request);
  return request;
}

function rememberFrame(key: string, canvas: HTMLCanvasElement) {
  frameCache.set(key, canvas);
  if (frameCache.size <= FRAME_CACHE_LIMIT) return;
  const oldestKey = frameCache.keys().next().value;
  if (typeof oldestKey === 'string') frameCache.delete(oldestKey);
}

function widestTierBounds(
  item: CosmeticDTO,
  prepared: PreparedCatalogFrame,
): Bounds {
  if (item.slot === 'BACKGROUND') return prepared.bounds;
  const cached = fitEnvelopeCache.get(item.spriteKey);
  if (cached) return cached;

  const chubby = morphBody(prepared.canvas, prepared, 'CHUBBY', item.slot);
  const bounds = visibleContentBounds(chubby) ?? prepared.bounds;
  fitEnvelopeCache.set(item.spriteKey, bounds);
  return bounds;
}

function annotateRenderQuality(canvas: HTMLCanvasElement, item: CosmeticDTO) {
  const pixels = canvas2d(canvas).getImageData(0, 0, canvas.width, canvas.height).data;
  let left = canvas.width;
  let right = -1;
  let top = canvas.height;
  let bottom = -1;
  let edgeBlackPixels = 0;
  const blackPixelsByColumn = new Uint16Array(canvas.width);

  for (let y = 0; y < canvas.height; y += 1) {
    for (let x = 0; x < canvas.width; x += 1) {
      const index = (y * canvas.width + x) * 4;
      const red = pixels[index];
      const green = pixels[index + 1];
      const blue = pixels[index + 2];
      const max = Math.max(red, green, blue);
      const min = Math.min(red, green, blue);
      if (max - min > 24 || max < 185) {
        left = Math.min(left, x);
        right = Math.max(right, x);
        top = Math.min(top, y);
        bottom = Math.max(bottom, y);
      }
      if (max <= 24) {
        blackPixelsByColumn[x] += 1;
        if (x === 0 || y === 0 || x === canvas.width - 1 || y === canvas.height - 1) {
          edgeBlackPixels += 1;
        }
      }
    }
  }

  const issues: string[] = [];
  if (right < left || bottom < top) issues.push('blank');
  const renderBounds = right >= left && bottom >= top
    ? { x: left, y: top, width: right - left + 1, height: bottom - top + 1 }
    : null;
  const faceAxisX = renderBounds
    ? estimateFaceAnchorX(
      pixels,
      canvas.width,
      canvas.height,
      renderBounds,
      Math.max(3, Math.round(canvas.width / 60)),
    )
    : null;
  if (item.slot !== 'BACKGROUND') {
    if (edgeBlackPixels > 0) issues.push('black-edge');
    if (bottom >= canvas.height - Math.max(2, OUTPUT_FLOOR_GAP - 1)) issues.push('floor-overflow');
    if (faceAxisX !== null && Math.abs(faceAxisX - canvas.width / 2) > 7) {
      issues.push('off-center');
    }
  }
  if (Array.from(blackPixelsByColumn).some((count) => count > canvas.height * 0.7)) {
    issues.push('black-bar');
  }

  let headHash = 2166136261;
  if (right >= left && bottom >= top) {
    const headBottom = Math.min(
      canvas.height - 1,
      top + Math.floor((bottom - top + 1) * 0.28),
    );
    for (let y = top; y <= headBottom; y += 1) {
      for (let x = 0; x < canvas.width; x += 1) {
        const index = (y * canvas.width + x) * 4;
        for (let channel = 0; channel < 3; channel += 1) {
          headHash ^= pixels[index + channel];
          headHash = Math.imul(headHash, 16777619);
        }
      }
    }
  }

  canvas.dataset.renderQuality = issues.length === 0 ? 'ok' : 'invalid';
  canvas.dataset.renderIssues = issues.join(',');
  canvas.dataset.faceCenterOffset = faceAxisX === null
    ? ''
    : String(Math.round((faceAxisX - canvas.width / 2) * 10) / 10);
  canvas.dataset.renderBounds = right >= left
    ? `${left},${top},${right},${bottom}`
    : '';
  canvas.dataset.headHash = String(headHash >>> 0);
  if (right >= left && bottom >= top) {
    const sampleY = Math.round(top + (bottom - top) * 0.72);
    let sampleLeft = canvas.width;
    let sampleRight = -1;
    for (let x = 0; x < canvas.width; x += 1) {
      const index = (sampleY * canvas.width + x) * 4;
      const max = Math.max(pixels[index], pixels[index + 1], pixels[index + 2]);
      const min = Math.min(pixels[index], pixels[index + 1], pixels[index + 2]);
      if (max - min <= 24 && max >= 185) continue;
      sampleLeft = Math.min(sampleLeft, x);
      sampleRight = Math.max(sampleRight, x);
    }
    canvas.dataset.bodyWidth = String(
      sampleRight >= sampleLeft ? sampleRight - sampleLeft + 1 : 0,
    );
  } else {
    canvas.dataset.bodyWidth = '0';
  }
}

async function renderCatalogFrame(item: CosmeticDTO, bodyState: BodyState) {
  const cacheKey = `${item.spriteKey}:${bodyState}`;
  const cached = frameCache.get(cacheKey);
  if (cached) return cached;

  const prepared = await prepareCatalogFrame(item);
  const fitted = morphBody(prepared.canvas, prepared, bodyState, item.slot);
  const output = createCanvas(OUTPUT_WIDTH, OUTPUT_HEIGHT);
  const outputContext = fillNeutral(output);

  const availableHeight = item.slot === 'BACKGROUND'
    ? OUTPUT_HEIGHT
    : OUTPUT_HEIGHT - OUTPUT_TOP_PADDING - OUTPUT_FLOOR_GAP;
  const fitEnvelope = widestTierBounds(item, prepared);
  const maximumHorizontalExtent = Math.max(
    prepared.anchorX - fitEnvelope.x,
    fitEnvelope.x + fitEnvelope.width - prepared.anchorX,
  );
  // Scale from the prepared BASE frame, not the fitted tier. This keeps the
  // head and every fixed anchor exactly the same size across all four tiers.
  const rawScale = item.slot === 'BACKGROUND'
    ? Math.min(
      OUTPUT_WIDTH / prepared.bounds.width,
      availableHeight / prepared.bounds.height,
    )
    : Math.min(
      (OUTPUT_WIDTH / 2 - OUTPUT_SIDE_PADDING) / maximumHorizontalExtent,
      availableHeight / prepared.bounds.height,
    );
  // Make each source logical cell land on an integer number of output pixels.
  const outputCell = Math.max(1, Math.floor(rawScale * prepared.pixelCell));
  const scale = Math.min(rawScale, outputCell / prepared.pixelCell);
  const width = Math.max(1, Math.round(prepared.frame.width * scale));
  const height = Math.max(1, Math.round(prepared.frame.height * scale));
  const scaleX = width / prepared.frame.width;
  const scaleY = height / prepared.frame.height;
  const x = item.slot === 'BACKGROUND'
    ? Math.round(
      OUTPUT_WIDTH / 2
      - (prepared.bounds.x + prepared.bounds.width / 2) * scaleX,
    )
    : Math.round(OUTPUT_WIDTH / 2 - prepared.anchorX * scaleX);
  const y = item.slot === 'BACKGROUND'
    ? Math.round(
      OUTPUT_HEIGHT / 2
      - (prepared.bounds.y + prepared.bounds.height / 2) * scaleY,
    )
    : Math.round(
      OUTPUT_HEIGHT
      - OUTPUT_FLOOR_GAP
      - (prepared.bounds.y + prepared.bounds.height) * scaleY,
    );

  outputContext.drawImage(
    fitted,
    0,
    0,
    prepared.frame.width,
    prepared.frame.height,
    x,
    y,
    width,
    height,
  );
  rememberFrame(cacheKey, output);
  return output;
}

interface CatalogPortraitProps {
  item: CosmeticDTO;
  bodyState: BodyState;
  className?: string;
}

const CatalogPortrait = memo(function CatalogPortrait({
  item,
  bodyState,
  className = '',
}: CatalogPortraitProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    setFailed(false);
    void renderCatalogFrame(item, bodyState)
      .then((frame) => {
        if (!active || !canvasRef.current) return;
        const canvas = canvasRef.current;
        canvas.width = OUTPUT_WIDTH;
        canvas.height = OUTPUT_HEIGHT;
        canvas2d(canvas).drawImage(frame, 0, 0);
        if (import.meta.env.DEV) annotateRenderQuality(canvas, item);
      })
      .catch((error: unknown) => {
        console.error(error);
        if (active) setFailed(true);
      });
    return () => {
      active = false;
    };
  }, [bodyState, item]);

  if (failed) {
    return <span className="catalog-portrait-fallback" aria-hidden="true">?</span>;
  }

  return (
    <canvas
      ref={canvasRef}
      className={`catalog-portrait ${className}`.trim()}
      width={OUTPUT_WIDTH}
      height={OUTPUT_HEIGHT}
      data-sprite-key={item.spriteKey}
      data-body-state={bodyState}
      data-slot={item.slot}
      aria-hidden="true"
    />
  );
});

export function ProductPreview({
  item,
  bodyState = 'BASE',
}: {
  item: CosmeticDTO;
  bodyState?: BodyState;
}) {
  return (
    <div className="product-visual" aria-label={`${item.name} ${bodyState} 체형 착용 미리보기`}>
      <CatalogPortrait item={item} bodyState={bodyState} />
    </div>
  );
}

export function CatalogCharacterPreview({
  item,
  bodyState,
}: {
  item: CosmeticDTO;
  bodyState: BodyState;
}) {
  return (
    <div className="catalog-character-stage" aria-label={`${item.name} ${bodyState} 체형 착용 미리보기`}>
      <CatalogPortrait item={item} bodyState={bodyState} className="catalog-portrait--stage" />
    </div>
  );
}
