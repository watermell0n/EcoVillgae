import { Application, Container, Graphics, RenderTexture, Sprite, Texture } from 'pixi.js';
import {
  assetProfileFor,
  frameKeyFor,
  placementFor,
  validateRig,
} from '../assetProfile';
import { CATALOG_BY_ID } from '../catalog';
import type { BodyState, EquippedMap, Slot } from '../types';
import { SLOT_Z_ORDER } from '../types';
import { CELL, DISPLAY_SCALE, GRID_H, GRID_W, drawBase, drawCosmetic } from './art';

const TEXTURE_WIDTH = GRID_W * CELL;
const TEXTURE_HEIGHT = GRID_H * CELL;
const BASE_AFTER: Slot = 'BACK';

/**
 * 모든 텍스처는 동일한 논리 캔버스에서 1배율로 합성한다.
 * 슬롯 스프라이트에는 scale/rotation을 적용하지 않고 정수 위치만 허용한다.
 */
export class PaperDoll {
  private app!: Application;
  private baseSprite!: Sprite;
  private slotSprites!: Record<Slot, Sprite>;
  private textureCache = new Map<string, Texture>();

  async init(parent: HTMLElement) {
    const rigErrors = validateRig();
    if (rigErrors.length > 0) {
      throw new Error(`캐릭터 리그 검증 실패: ${rigErrors.join(', ')}`);
    }

    const integerResolution = Math.max(1, Math.ceil(window.devicePixelRatio || 1));
    this.app = new Application();
    await this.app.init({
      width: TEXTURE_WIDTH * DISPLAY_SCALE,
      height: TEXTURE_HEIGHT * DISPLAY_SCALE,
      backgroundAlpha: 0,
      antialias: false,
      resolution: integerResolution,
      autoDensity: true,
    });

    this.app.stage.position.set(0, 0);
    this.app.stage.scale.set(DISPLAY_SCALE);
    this.app.canvas.style.width = `${TEXTURE_WIDTH * DISPLAY_SCALE}px`;
    this.app.canvas.style.height = `${TEXTURE_HEIGHT * DISPLAY_SCALE}px`;
    this.app.canvas.style.imageRendering = 'pixelated';

    const root = new Container();
    root.position.set(0, 0);
    this.app.stage.addChild(root);

    this.baseSprite = new Sprite();
    this.prepareSprite(this.baseSprite);
    this.slotSprites = {} as Record<Slot, Sprite>;

    for (const slot of SLOT_Z_ORDER) {
      const sprite = new Sprite();
      this.prepareSprite(sprite);
      this.slotSprites[slot] = sprite;
      root.addChild(sprite);
      if (slot === BASE_AFTER) root.addChild(this.baseSprite);
    }

    parent.appendChild(this.app.canvas);
  }

  private prepareSprite(sprite: Sprite) {
    sprite.position.set(0, 0);
    sprite.scale.set(1, 1);
    sprite.rotation = 0;
    sprite.roundPixels = true;
  }

  private bake(cacheKey: string, draw: (graphics: Graphics) => void): Texture {
    const cached = this.textureCache.get(cacheKey);
    if (cached) return cached;

    const graphics = new Graphics();
    draw(graphics);
    const texture = RenderTexture.create({
      width: TEXTURE_WIDTH,
      height: TEXTURE_HEIGHT,
      antialias: false,
    });
    texture.source.scaleMode = 'nearest';
    (texture.source as typeof texture.source & { autoGenerateMipmaps?: boolean }).autoGenerateMipmaps = false;
    this.app.renderer.render({ container: graphics, target: texture, clear: true });
    graphics.destroy();
    this.textureCache.set(cacheKey, texture);
    return texture;
  }

  private baseTexture(body: BodyState): Texture {
    return this.bake(`base_${body}`, (graphics) => drawBase(graphics, body));
  }

  private cosmeticTexture(slot: Slot, cosmeticId: string, body: BodyState): Texture {
    const item = CATALOG_BY_ID[cosmeticId];
    const profile = assetProfileFor(item);
    const frameKey = frameKeyFor(item, body);
    const cacheKey = profile.fitMode === 'NINE_SLICE'
      ? `cos_${frameKey}_${body}`
      : `cos_${frameKey}`;
    return this.bake(cacheKey, (graphics) => drawCosmetic(graphics, slot, item.spriteKey, body));
  }

  update(equipped: EquippedMap, body: BodyState) {
    if (!this.app) return;
    this.baseSprite.texture = this.baseTexture(body);

    const topId = equipped.TOP;
    const topItem = topId ? CATALOG_BY_ID[topId] : null;
    const topCoversBottom = !!topItem && assetProfileFor(topItem).coversSlots.includes('BOTTOM');

    for (const slot of Object.keys(this.slotSprites) as Slot[]) {
      const sprite = this.slotSprites[slot];
      let cosmeticId = equipped[slot];
      if (slot === 'BOTTOM' && topCoversBottom) cosmeticId = null;

      if (!cosmeticId) {
        sprite.visible = false;
        sprite.texture = Texture.EMPTY;
        continue;
      }

      const item = CATALOG_BY_ID[cosmeticId];
      const placement = placementFor(item);
      sprite.position.set(Math.round(placement.x), Math.round(placement.y));
      sprite.scale.set(1, 1);
      sprite.rotation = 0;
      sprite.texture = this.cosmeticTexture(slot, cosmeticId, body);
      sprite.visible = true;
    }
  }

  destroy() {
    for (const texture of this.textureCache.values()) texture.destroy(true);
    this.textureCache.clear();
    if (this.app) this.app.destroy(true, { children: true, texture: true });
  }
}
