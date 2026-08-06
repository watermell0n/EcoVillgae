// Minimal PNG decode/encode (8-bit RGBA / RGB / palette, no interlace) using zlib.
const fs = require('fs');
const zlib = require('zlib');

function chunks(buf) {
  const out = [];
  let p = 8;
  while (p < buf.length) {
    const len = buf.readUInt32BE(p);
    const type = buf.slice(p + 4, p + 8).toString('latin1');
    out.push({ type, data: buf.slice(p + 8, p + 8 + len) });
    p += 12 + len;
  }
  return out;
}

function paeth(a, b, c) {
  const pp = a + b - c;
  const pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}

/** -> {width, height, rgba: Buffer(w*h*4)} */
function decode(file) {
  const buf = fs.readFileSync(file);
  const cs = chunks(buf);
  const ihdr = cs.find((c) => c.type === 'IHDR').data;
  const width = ihdr.readUInt32BE(0);
  const height = ihdr.readUInt32BE(4);
  const depth = ihdr[8];
  const colorType = ihdr[9];
  const interlace = ihdr[12];
  if (depth !== 8 || interlace !== 0) {
    throw new Error(`${file}: unsupported depth=${depth} interlace=${interlace}`);
  }

  const plte = cs.find((c) => c.type === 'PLTE');
  const trns = cs.find((c) => c.type === 'tRNS');
  const idat = zlib.inflateSync(
    Buffer.concat(cs.filter((c) => c.type === 'IDAT').map((c) => c.data)),
  );

  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colorType];
  if (!channels) throw new Error(`${file}: colorType ${colorType}`);
  const bpp = channels;
  const stride = width * bpp;

  const raw = Buffer.alloc(height * stride);
  let pos = 0;
  for (let y = 0; y < height; y++) {
    const ft = idat[pos++];
    const line = idat.slice(pos, pos + stride);
    pos += stride;
    const cur = raw.slice(y * stride, (y + 1) * stride);
    const prev = y > 0 ? raw.slice((y - 1) * stride, y * stride) : Buffer.alloc(stride);
    for (let i = 0; i < stride; i++) {
      const x = line[i];
      const a = i >= bpp ? cur[i - bpp] : 0;
      const b = prev[i];
      const c = i >= bpp ? prev[i - bpp] : 0;
      cur[i] =
        ft === 0 ? x :
        ft === 1 ? (x + a) & 255 :
        ft === 2 ? (x + b) & 255 :
        ft === 3 ? (x + ((a + b) >> 1)) & 255 :
        (x + paeth(a, b, c)) & 255;
    }
  }

  const rgba = Buffer.alloc(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    const s = i * bpp, d = i * 4;
    if (colorType === 6) { raw.copy(rgba, d, s, s + 4); }
    else if (colorType === 2) { raw.copy(rgba, d, s, s + 3); rgba[d + 3] = 255; }
    else if (colorType === 0) { const g = raw[s]; rgba[d] = rgba[d+1] = rgba[d+2] = g; rgba[d+3] = 255; }
    else if (colorType === 4) { const g = raw[s]; rgba[d] = rgba[d+1] = rgba[d+2] = g; rgba[d+3] = raw[s+1]; }
    else if (colorType === 3) {
      const idx = raw[s];
      rgba[d] = plte.data[idx * 3]; rgba[d+1] = plte.data[idx*3+1]; rgba[d+2] = plte.data[idx*3+2];
      rgba[d+3] = trns && idx < trns.data.length ? trns.data[idx] : 255;
    }
  }
  return { width, height, rgba };
}

function crc32(buf) {
  let c, crc = 0xffffffff;
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = c ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'latin1'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}

function encode({ width, height, rgba }, file) {
  const stride = width * 4;
  const raw = Buffer.alloc(height * (stride + 1));
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0;
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  fs.writeFileSync(file, Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]));
}

function blank(width, height, fill = [0, 0, 0, 0]) {
  const rgba = Buffer.alloc(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    rgba[i*4] = fill[0]; rgba[i*4+1] = fill[1]; rgba[i*4+2] = fill[2]; rgba[i*4+3] = fill[3];
  }
  return { width, height, rgba };
}

/** nearest-neighbour blit of src scaled by `s` into dst at (dx,dy), alpha-composited */
function blit(dst, src, dx, dy, s = 1) {
  for (let y = 0; y < src.height * s; y++) {
    for (let x = 0; x < src.width * s; x++) {
      const sx = (x / s) | 0, sy = (y / s) | 0;
      const si = (sy * src.width + sx) * 4;
      const a = src.rgba[si + 3];
      if (a === 0) continue;
      const tx = dx + x, ty = dy + y;
      if (tx < 0 || ty < 0 || tx >= dst.width || ty >= dst.height) continue;
      const di = (ty * dst.width + tx) * 4;
      if (a === 255) {
        src.rgba.copy(dst.rgba, di, si, si + 4);
      } else {
        const af = a / 255, ib = 1 - af;
        for (let k = 0; k < 3; k++) dst.rgba[di + k] = Math.round(src.rgba[si + k] * af + dst.rgba[di + k] * ib);
        dst.rgba[di + 3] = Math.max(dst.rgba[di + 3], a);
      }
    }
  }
}

module.exports = { decode, encode, blank, blit };
