param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $RepositoryRoot 'docs\assets\character\product-basic-reference.png'
$basePath = Join-Path $RepositoryRoot 'student-app\public\character\base\base_char_base.png'
$outputDirectory = Join-Path $RepositoryRoot 'student-app\public\character\layers\basic'

if (-not (Test-Path -LiteralPath $sourcePath)) {
    throw "Basic product reference was not found: $sourcePath"
}
if (-not (Test-Path -LiteralPath $basePath)) {
    throw "BASE sprite was not found: $basePath"
}

New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

Add-Type -AssemblyName System.Drawing

if (-not ('BasicLayerExtractor' -as [type])) {
    Add-Type -ReferencedAssemblies 'System.Drawing' -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

public static class BasicLayerExtractor
{
    private enum MaskMode
    {
        Color,
        DarkDifference
    }

    private sealed class ItemSpec
    {
        public string Key { get; private set; }
        public int Column { get; private set; }
        public int Row { get; private set; }
        public Rectangle Region { get; private set; }
        public MaskMode Mode { get; private set; }
        public Color[] Palette { get; private set; }
        public int OffsetX { get; private set; }
        public int OffsetY { get; private set; }

        public ItemSpec(
            string key,
            int column,
            int row,
            Rectangle region,
            MaskMode mode,
            Color[] palette,
            int offsetX,
            int offsetY
        )
        {
            Key = key;
            Column = column;
            Row = row;
            Region = region;
            Mode = mode;
            Palette = palette;
            OffsetX = offsetX;
            OffsetY = offsetY;
        }
    }

    private static readonly int[] ColumnCenters = { 185, 500, 815, 1130, 1445 };
    private static readonly int[] RowTops = { 116, 516 };

    private static Color[] Palette(params string[] values)
    {
        Color[] colors = new Color[values.Length];
        for (int index = 0; index < values.Length; index++)
        {
            colors[index] = ColorTranslator.FromHtml(values[index]);
        }
        return colors;
    }

    private static readonly ItemSpec[] Items =
    {
        new ItemSpec("hat_red_cap", 0, 0, new Rectangle(6, 0, 52, 27), MaskMode.Color,
            Palette("#262b31", "#73302f", "#a93b38", "#d94b45", "#f17168"), 0, -2),
        new ItemSpec("hat_teal_beanie", 1, 0, new Rectangle(8, 0, 48, 25), MaskMode.Color,
            Palette("#262b31", "#075a66", "#087f8c", "#09a8b2", "#63ced2", "#b6eff0"), 0, -2),
        new ItemSpec("face_sunglasses", 2, 0, new Rectangle(12, 14, 40, 18), MaskMode.DarkDifference,
            Palette("#151b20", "#262b31", "#4b535e", "#77838b"), 1, -2),
        new ItemSpec("face_headphones", 3, 0, new Rectangle(6, 0, 52, 36), MaskMode.DarkDifference,
            Palette("#262b31", "#4b535e", "#77818a", "#aeb7bd", "#e4e8e9"), 1, -2),
        new ItemSpec("top_blue_tshirt", 4, 0, new Rectangle(2, 25, 60, 38), MaskMode.Color,
            Palette("#262b31", "#30466d", "#466696", "#5f80b0", "#8ba4c8"), 2, -2),
        new ItemSpec("top_yellow_hoodie", 0, 1, new Rectangle(1, 23, 62, 43), MaskMode.Color,
            Palette("#262b31", "#a96f22", "#d69b2f", "#f4c348", "#ffdc73", "#fff3c2", "#fffdf8"), 0, 0),
        new ItemSpec("top_green_vest", 1, 1, new Rectangle(1, 23, 62, 43), MaskMode.Color,
            Palette("#262b31", "#38522b", "#527532", "#709a3f", "#91bd5b", "#c2dc86"), 0, 0),
        new ItemSpec("bottom_overalls", 2, 1, new Rectangle(2, 23, 60, 45), MaskMode.Color,
            Palette("#262b31", "#293d61", "#3d5680", "#5672a0", "#7891b8", "#a9b9d0"), 1, 0),
        new ItemSpec("shoes_sneakers", 3, 1, new Rectangle(6, 50, 52, 22), MaskMode.Color,
            Palette("#262b31", "#a33f39", "#d85a50", "#f08b7d", "#b7c0c5", "#e6e9e9", "#fffdf8"), 2, 0),
        new ItemSpec("back_backpack", 4, 1, new Rectangle(0, 20, 64, 48), MaskMode.Color,
            Palette("#262b31", "#51301e", "#704125", "#965d31", "#bc7a3e", "#dfa45f"), 3, 0),
    };

    private static bool IsBackground(Color color)
    {
        int maximum = Math.Max(color.R, Math.Max(color.G, color.B));
        int minimum = Math.Min(color.R, Math.Min(color.G, color.B));
        return color.A == 0 || (minimum >= 220 && maximum - minimum <= 18);
    }

    private static bool IsCheek(Color color)
    {
        return color.R >= 205 && color.G <= 205 && color.B <= 215
            && color.R - color.G >= 20
            && color.B >= color.G;
    }

    private static int ColorDistance(Color left, Color right)
    {
        return Math.Abs(left.R - right.R)
            + Math.Abs(left.G - right.G)
            + Math.Abs(left.B - right.B)
            + Math.Abs(left.A - right.A);
    }

    private static bool IsColorSeed(Color color, Color baseColor)
    {
        int maximum = Math.Max(color.R, Math.Max(color.G, color.B));
        int minimum = Math.Min(color.R, Math.Min(color.G, color.B));
        return color.A >= 128
            && maximum - minimum >= 16
            && !IsCheek(color)
            && ColorDistance(color, baseColor) >= 28;
    }

    private static bool IsDarkDifferenceSeed(Color color, Color baseColor)
    {
        int luminance = (color.R * 3 + color.G * 6 + color.B) / 10;
        return color.A >= 128
            && luminance <= 218
            && !IsCheek(color)
            && ColorDistance(color, baseColor) >= 48;
    }

    private static void RemoveConnectedBackground(Bitmap bitmap)
    {
        int width = bitmap.Width;
        int height = bitmap.Height;
        bool[] visited = new bool[width * height];
        Queue<int> queue = new Queue<int>();

        Action<int, int> enqueue = (x, y) =>
        {
            int index = y * width + x;
            if (!visited[index])
            {
                visited[index] = true;
                queue.Enqueue(index);
            }
        };

        for (int x = 0; x < width; x++)
        {
            enqueue(x, 0);
            enqueue(x, height - 1);
        }
        for (int y = 1; y < height - 1; y++)
        {
            enqueue(0, y);
            enqueue(width - 1, y);
        }

        while (queue.Count > 0)
        {
            int index = queue.Dequeue();
            int x = index % width;
            int y = index / width;
            Color color = bitmap.GetPixel(x, y);
            if (!IsBackground(color))
            {
                continue;
            }

            bitmap.SetPixel(x, y, Color.Transparent);
            if (x > 0) enqueue(x - 1, y);
            if (x + 1 < width) enqueue(x + 1, y);
            if (y > 0) enqueue(x, y - 1);
            if (y + 1 < height) enqueue(x, y + 1);
        }
    }

    private static Bitmap ScaleCardToMaster(Bitmap source, ItemSpec item)
    {
        int sourceLeft = ColumnCenters[item.Column] - 140;
        int sourceTop = RowTops[item.Row];
        Rectangle sourceBounds = new Rectangle(sourceLeft, sourceTop, 280, 315);

        Bitmap master = new Bitmap(64, 72, PixelFormat.Format32bppArgb);
        using (Graphics graphics = Graphics.FromImage(master))
        {
            graphics.Clear(Color.Transparent);
            graphics.CompositingMode = CompositingMode.SourceCopy;
            // The reference cards are not an integer multiple of the 64x72
            // master canvas. Nearest-neighbor sampling at that ratio drops
            // alternating outline/detail pixels. Sample the high-resolution
            // reference evenly first, then hard-quantize the extracted layer
            // back to the fixed pixel palette in ApplyMask.
            graphics.CompositingQuality = CompositingQuality.HighQuality;
            graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
            graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
            graphics.SmoothingMode = SmoothingMode.None;
            graphics.DrawImage(
                source,
                new Rectangle(item.OffsetX, item.OffsetY, 64, 72),
                sourceBounds,
                GraphicsUnit.Pixel
            );
        }
        RemoveConnectedBackground(master);
        return master;
    }

    private static bool[,] BuildMask(Bitmap itemBitmap, Bitmap baseBitmap, ItemSpec item)
    {
        bool[,] mask = new bool[itemBitmap.Width, itemBitmap.Height];

        for (int y = item.Region.Top; y < item.Region.Bottom; y++)
        {
            for (int x = item.Region.Left; x < item.Region.Right; x++)
            {
                Color color = itemBitmap.GetPixel(x, y);
                Color baseColor = baseBitmap.GetPixel(x, y);
                mask[x, y] = item.Mode == MaskMode.Color
                    ? IsColorSeed(color, baseColor)
                    : IsDarkDifferenceSeed(color, baseColor);
            }
        }

        // Grow into neighboring outline and highlight pixels. The bounded item
        // region prevents the BASE face and body outline from leaking in.
        for (int pass = 0; pass < 1; pass++)
        {
            bool[,] next = (bool[,])mask.Clone();
            for (int y = item.Region.Top; y < item.Region.Bottom; y++)
            {
                for (int x = item.Region.Left; x < item.Region.Right; x++)
                {
                    if (mask[x, y]) continue;
                    Color color = itemBitmap.GetPixel(x, y);
                    if (color.A < 128 || IsBackground(color) || IsCheek(color)) continue;

                    bool touches = false;
                    for (int offsetY = -1; offsetY <= 1 && !touches; offsetY++)
                    {
                        for (int offsetX = -1; offsetX <= 1; offsetX++)
                        {
                            if (offsetX == 0 && offsetY == 0) continue;
                            int neighborX = x + offsetX;
                            int neighborY = y + offsetY;
                            if (neighborX < 0 || neighborX >= itemBitmap.Width
                                || neighborY < 0 || neighborY >= itemBitmap.Height)
                            {
                                continue;
                            }
                            if (mask[neighborX, neighborY])
                            {
                                touches = true;
                                break;
                            }
                        }
                    }
                    if (touches) next[x, y] = true;
                }
            }
            mask = next;
        }

        if (item.Key == "face_headphones")
        {
            for (int y = item.Region.Top; y < item.Region.Bottom; y++)
            {
                for (int x = item.Region.Left; x < item.Region.Right; x++)
                {
                    bool band = y <= 7
                        || x <= 18
                        || x >= 46
                        || (y <= 12 && (x <= 25 || x >= 39));
                    if (!band || y > 31) mask[x, y] = false;
                }
            }
        }

        if (item.Key == "face_sunglasses")
        {
            for (int y = item.Region.Top; y < item.Region.Bottom; y++)
            {
                for (int x = item.Region.Left; x < item.Region.Right; x++)
                {
                    bool glassesBounds = x >= 19 && x <= 46 && y >= 19 && y <= 28;
                    if (!glassesBounds) mask[x, y] = false;
                }
            }
        }

        if (item.Key == "hat_red_cap" || item.Key == "hat_teal_beanie")
        {
            for (int y = item.Region.Top; y < item.Region.Bottom; y++)
            {
                for (int x = item.Region.Left; x < item.Region.Right; x++)
                {
                    Color color = itemBitmap.GetPixel(x, y);
                    int maximum = Math.Max(color.R, Math.Max(color.G, color.B));
                    int minimum = Math.Min(color.R, Math.Min(color.G, color.B));
                    bool neutralLight = minimum >= 205 && maximum - minimum <= 22;
                    if (neutralLight) mask[x, y] = false;
                    if (item.Key == "hat_red_cap" && y > 22) mask[x, y] = false;
                    if (item.Key == "hat_teal_beanie" && y > 20) mask[x, y] = false;
                }
            }
        }

        if (item.Key == "top_yellow_hoodie")
        {
            for (int y = item.Region.Top; y < Math.Min(27, item.Region.Bottom); y++)
            {
                for (int x = item.Region.Left; x < item.Region.Right; x++)
                {
                    mask[x, y] = false;
                }
            }
        }

        if (item.Key == "top_yellow_hoodie" || item.Key == "bottom_overalls")
        {
            // The reference cards contain the BASE mouth inside the clothing
            // crop. Keep the face center transparent so the shared BASE face
            // remains unchanged when these layers are equipped.
            for (int y = item.Region.Top; y < Math.Min(31, item.Region.Bottom); y++)
            {
                for (int x = 24; x <= 40; x++)
                {
                    mask[x, y] = false;
                }
            }
        }

        if (item.Key == "top_blue_tshirt")
        {
            for (int y = 55; y < item.Region.Bottom; y++)
            {
                for (int x = item.Region.Left; x < item.Region.Right; x++)
                {
                    mask[x, y] = false;
                }
            }
        }

        if (item.Key == "shoes_sneakers")
        {
            // White sneaker pixels are intentionally close to the BASE body
            // color, so a color-difference mask erases most of each shoe.
            // Keep the two complete shoe silhouettes from the aligned card
            // crop instead. The connected background was already made
            // transparent in ScaleCardToMaster, which lets us retain white
            // uppers, dark outlines, laces, and red accents together.
            for (int y = item.Region.Top; y < item.Region.Bottom; y++)
            {
                for (int x = item.Region.Left; x < item.Region.Right; x++)
                {
                    bool inLeftShoe = x >= 15 && x <= 32 && y >= 56 && y <= 70;
                    bool inRightShoe = x >= 35 && x <= 52 && y >= 56 && y <= 70;
                    mask[x, y] = (inLeftShoe || inRightShoe)
                        && itemBitmap.GetPixel(x, y).A >= 128;
                }
            }
        }

        if (item.Key == "back_backpack")
        {
            // The backpack reference contributes only the two shoulder straps
            // to this front layer. Remove the tiny center fragment that comes
            // from the reference character's face/body and would otherwise
            // appear as a stray brown pixel near the eye.
            for (int y = item.Region.Top; y < item.Region.Bottom; y++)
            {
                for (int x = 24; x <= 42; x++)
                {
                    mask[x, y] = false;
                }
            }
        }

        return mask;
    }

    private static void RemoveTinyComponents(bool[,] mask, int minimumPixels)
    {
        int width = mask.GetLength(0);
        int height = mask.GetLength(1);
        bool[,] visited = new bool[width, height];

        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                if (!mask[x, y] || visited[x, y]) continue;

                Queue<Point> queue = new Queue<Point>();
                List<Point> component = new List<Point>();
                queue.Enqueue(new Point(x, y));
                visited[x, y] = true;

                while (queue.Count > 0)
                {
                    Point point = queue.Dequeue();
                    component.Add(point);
                    for (int offsetY = -1; offsetY <= 1; offsetY++)
                    {
                        for (int offsetX = -1; offsetX <= 1; offsetX++)
                        {
                            if (offsetX == 0 && offsetY == 0) continue;
                            int neighborX = point.X + offsetX;
                            int neighborY = point.Y + offsetY;
                            if (neighborX < 0 || neighborX >= width
                                || neighborY < 0 || neighborY >= height
                                || visited[neighborX, neighborY]
                                || !mask[neighborX, neighborY])
                            {
                                continue;
                            }
                            visited[neighborX, neighborY] = true;
                            queue.Enqueue(new Point(neighborX, neighborY));
                        }
                    }
                }

                if (component.Count < minimumPixels)
                {
                    foreach (Point point in component)
                    {
                        mask[point.X, point.Y] = false;
                    }
                }
            }
        }
    }

    private static Color Quantize(Color color, Color[] palette)
    {
        Color best = palette[0];
        long bestDistance = long.MaxValue;
        foreach (Color candidate in palette)
        {
            long red = color.R - candidate.R;
            long green = color.G - candidate.G;
            long blue = color.B - candidate.B;
            long distance = red * red * 3 + green * green * 4 + blue * blue * 2;
            if (distance < bestDistance)
            {
                best = candidate;
                bestDistance = distance;
            }
        }
        return Color.FromArgb(255, best.R, best.G, best.B);
    }

    private static Bitmap ApplyMask(Bitmap itemBitmap, bool[,] mask, Color[] palette)
    {
        Bitmap output = new Bitmap(itemBitmap.Width, itemBitmap.Height, PixelFormat.Format32bppArgb);
        for (int y = 0; y < itemBitmap.Height; y++)
        {
            for (int x = 0; x < itemBitmap.Width; x++)
            {
                if (!mask[x, y])
                {
                    output.SetPixel(x, y, Color.Transparent);
                    continue;
                }

                Color color = itemBitmap.GetPixel(x, y);
                output.SetPixel(x, y, Quantize(color, palette));
            }
        }
        return output;
    }

    private static Rectangle AlphaBounds(Bitmap bitmap)
    {
        int left = bitmap.Width;
        int top = bitmap.Height;
        int right = -1;
        int bottom = -1;

        for (int y = 0; y < bitmap.Height; y++)
        {
            for (int x = 0; x < bitmap.Width; x++)
            {
                if (bitmap.GetPixel(x, y).A == 0) continue;
                left = Math.Min(left, x);
                top = Math.Min(top, y);
                right = Math.Max(right, x);
                bottom = Math.Max(bottom, y);
            }
        }

        return right < left
            ? Rectangle.Empty
            : Rectangle.FromLTRB(left, top, right + 1, bottom + 1);
    }

    private static Rectangle CanonicalBounds(string itemKey)
    {
        switch (itemKey)
        {
            // These five reference drawings were authored on a slightly
            // smaller character than the canonical BASE. Fit their complete
            // transparent layers to explicit BASE-rig bounds instead of
            // allowing each reference card to define its own visual scale.
            case "hat_red_cap":
                return new Rectangle(12, 1, 39, 20);
            case "face_sunglasses":
                return new Rectangle(17, 19, 31, 10);
            case "face_headphones":
                return new Rectangle(7, 0, 50, 33);
            case "top_blue_tshirt":
                return new Rectangle(9, 30, 47, 28);
            case "shoes_sneakers":
                return new Rectangle(11, 55, 43, 15);
            default:
                return Rectangle.Empty;
        }
    }

    private static Bitmap FitToCanonicalRig(Bitmap layer, ItemSpec item)
    {
        Rectangle targetBounds = CanonicalBounds(item.Key);
        Rectangle sourceBounds = AlphaBounds(layer);
        if (targetBounds.IsEmpty || sourceBounds.IsEmpty)
        {
            return new Bitmap(layer);
        }

        Bitmap fitted = new Bitmap(layer.Width, layer.Height, PixelFormat.Format32bppArgb);
        using (Graphics graphics = Graphics.FromImage(fitted))
        {
            graphics.Clear(Color.Transparent);
            graphics.CompositingMode = CompositingMode.SourceCopy;
            graphics.InterpolationMode = InterpolationMode.NearestNeighbor;
            graphics.PixelOffsetMode = PixelOffsetMode.Half;
            graphics.SmoothingMode = SmoothingMode.None;
            graphics.DrawImage(layer, targetBounds, sourceBounds, GraphicsUnit.Pixel);
        }
        return fitted;
    }

    private static Bitmap ScaleToMaster64(Bitmap source)
    {
        Bitmap master = new Bitmap(64, 64, PixelFormat.Format32bppArgb);
        using (Graphics graphics = Graphics.FromImage(master))
        {
            graphics.Clear(Color.Transparent);
            graphics.CompositingMode = CompositingMode.SourceCopy;
            graphics.InterpolationMode = InterpolationMode.NearestNeighbor;
            graphics.PixelOffsetMode = PixelOffsetMode.Half;
            graphics.SmoothingMode = SmoothingMode.None;
            graphics.DrawImage(
                source,
                new Rectangle(0, 0, 64, 64),
                new Rectangle(0, 0, source.Width, source.Height),
                GraphicsUnit.Pixel
            );
        }
        return master;
    }

    private static Bitmap SymmetrizeShoes(Bitmap source)
    {
        Bitmap symmetric = new Bitmap(source.Width, source.Height, PixelFormat.Format32bppArgb);

        // Preserve the left shoe as the canonical design, then mirror it onto
        // the right foot. Both shoes now share one size, baseline, and spacing.
        for (int y = 48; y <= 63; y++)
        {
            for (int x = 11; x <= 30; x++)
            {
                Color color = source.GetPixel(x, y);
                if (color.A == 0) continue;
                symmetric.SetPixel(x, y, color);
                symmetric.SetPixel((source.Width - 1) - x, y, color);
            }
        }

        return symmetric;
    }

    private static void FitBlueShirtToBody(Bitmap shirt)
    {
        // The reference shirt is nearly rectangular, while the canonical BASE
        // narrows again toward the feet. Trim only the lower garment rows so
        // the equipped silhouette follows the same egg-shaped rig.
        for (int y = 40; y < shirt.Height; y++)
        {
            int left;
            int right;
            if (y <= 45)
            {
                left = 10;
                right = 54;
            }
            else if (y <= 48)
            {
                left = 12;
                right = 52;
            }
            else
            {
                left = 14;
                right = 50;
            }

            for (int x = 0; x < shirt.Width; x++)
            {
                if (x < left || x > right) shirt.SetPixel(x, y, Color.Transparent);
            }
        }
    }

    private static Bitmap CreateHeadphonePart(Bitmap source, bool front)
    {
        Bitmap part = new Bitmap(source.Width, source.Height, PixelFormat.Format32bppArgb);
        for (int y = 0; y < source.Height; y++)
        {
            for (int x = 0; x < source.Width; x++)
            {
                Color color = source.GetPixel(x, y);
                if (color.A == 0) continue;

                bool earCup = y >= 14 && (x <= 16 || x >= 48);
                if (front == earCup)
                {
                    part.SetPixel(x, y, color);
                }
            }
        }
        return part;
    }

    private static Bitmap CreateOcclusionMask(Bitmap item, string itemKey)
    {
        Bitmap mask = new Bitmap(item.Width, item.Height, PixelFormat.Format32bppArgb);
        Color opaque = Color.FromArgb(255, 255, 255, 255);

        if (itemKey == "top_blue_tshirt")
        {
            // Remove the BASE torso/arm pixels that sit under the shirt. The
            // shirt itself covers this entire region, so no transparent halo
            // is introduced around the garment.
            for (int y = 27; y <= 50; y++)
            {
                int inset;
                if (y < 31) inset = 4 - (y - 27);
                else if (y <= 39) inset = 0;
                else if (y <= 45) inset = 1;
                else if (y <= 48) inset = 3;
                else inset = 5;
                int left = 10 + inset;
                int right = 54 - inset;
                for (int x = left; x <= right; x++)
                {
                    mask.SetPixel(x, y, opaque);
                }
            }
        }
        else if (itemKey == "shoes_sneakers")
        {
            // Remove both original BASE feet before the replacement shoes are
            // painted. The fixed rectangles use the BASE foot centers and
            // baseline, leaving the center gap transparent.
            for (int y = 51; y <= 61; y++)
            {
                for (int x = 14; x <= 31; x++) mask.SetPixel(x, y, opaque);
                for (int x = 32; x <= 49; x++) mask.SetPixel(x, y, opaque);
            }
        }

        return mask;
    }

    private static void EnforceOuterOutline(Bitmap bitmap, Color outline)
    {
        bool[,] boundary = new bool[bitmap.Width, bitmap.Height];
        for (int y = 0; y < bitmap.Height; y++)
        {
            for (int x = 0; x < bitmap.Width; x++)
            {
                if (bitmap.GetPixel(x, y).A == 0) continue;
                boundary[x, y] = x == 0
                    || y == 0
                    || x == bitmap.Width - 1
                    || y == bitmap.Height - 1
                    || bitmap.GetPixel(x - 1, y).A == 0
                    || bitmap.GetPixel(x + 1, y).A == 0
                    || bitmap.GetPixel(x, y - 1).A == 0
                    || bitmap.GetPixel(x, y + 1).A == 0;
            }
        }

        Color opaqueOutline = Color.FromArgb(255, outline.R, outline.G, outline.B);
        for (int y = 0; y < bitmap.Height; y++)
        {
            for (int x = 0; x < bitmap.Width; x++)
            {
                if (boundary[x, y]) bitmap.SetPixel(x, y, opaqueOutline);
            }
        }
    }

    private static bool NeedsInteriorCleanup(string itemKey)
    {
        return itemKey == "hat_red_cap"
            || itemKey == "face_headphones"
            || itemKey == "top_blue_tshirt"
            || itemKey == "shoes_sneakers";
    }

    private static void CleanInteriorPaletteNoise(Bitmap bitmap, Color outline)
    {
        int outlineKey = outline.ToArgb() & 0x00ffffff;

        // Reference cards contain high-resolution shading. After palette
        // reduction, isolated one-pixel shades read as resize debris rather
        // than intentional pixel art. Two conservative majority passes remove
        // only those specks while preserving silhouettes and larger details.
        for (int pass = 0; pass < 2; pass++)
        {
            using (Bitmap snapshot = new Bitmap(bitmap))
            {
                for (int y = 1; y < bitmap.Height - 1; y++)
                {
                    for (int x = 1; x < bitmap.Width - 1; x++)
                    {
                        Color current = snapshot.GetPixel(x, y);
                        int currentKey = current.ToArgb() & 0x00ffffff;
                        if (current.A == 0 || currentKey == outlineKey) continue;

                        Dictionary<int, int> counts = new Dictionary<int, int>();
                        int solidNeighbors = 0;
                        for (int offsetY = -1; offsetY <= 1; offsetY++)
                        {
                            for (int offsetX = -1; offsetX <= 1; offsetX++)
                            {
                                Color neighbor = snapshot.GetPixel(x + offsetX, y + offsetY);
                                int neighborKey = neighbor.ToArgb() & 0x00ffffff;
                                if (neighbor.A == 0 || neighborKey == outlineKey) continue;

                                solidNeighbors++;
                                if (!counts.ContainsKey(neighborKey)) counts[neighborKey] = 0;
                                counts[neighborKey]++;
                            }
                        }
                        if (solidNeighbors < 5) continue;

                        int currentCount = counts.ContainsKey(currentKey) ? counts[currentKey] : 0;
                        int majorityKey = currentKey;
                        int majorityCount = currentCount;
                        foreach (KeyValuePair<int, int> candidate in counts)
                        {
                            if (candidate.Value > majorityCount)
                            {
                                majorityKey = candidate.Key;
                                majorityCount = candidate.Value;
                            }
                        }

                        if (currentCount <= 2 && majorityCount >= 4)
                        {
                            bitmap.SetPixel(
                                x,
                                y,
                                Color.FromArgb(
                                    255,
                                    (majorityKey >> 16) & 255,
                                    (majorityKey >> 8) & 255,
                                    majorityKey & 255
                                )
                            );
                        }
                    }
                }
            }
        }
    }

    private static void WriteRuntimeSprite(Bitmap master, string outputPath)
    {
        using (Bitmap runtime = new Bitmap(48, 56, PixelFormat.Format32bppArgb))
        using (Graphics graphics = Graphics.FromImage(runtime))
        {
            graphics.Clear(Color.Transparent);
            graphics.CompositingMode = CompositingMode.SourceCopy;
            graphics.InterpolationMode = InterpolationMode.NearestNeighbor;
            graphics.PixelOffsetMode = PixelOffsetMode.Half;
            graphics.SmoothingMode = SmoothingMode.None;
            graphics.DrawImage(master, new Rectangle(0, 0, 48, 56));
            runtime.Save(outputPath, ImageFormat.Png);
        }
    }

    public static void Extract(string sourcePath, string basePath, string outputDirectory)
    {
        Directory.CreateDirectory(outputDirectory);
        using (Bitmap source = new Bitmap(sourcePath))
        using (Bitmap baseSource = new Bitmap(basePath))
        using (Bitmap baseBitmap = new Bitmap(64, 72, PixelFormat.Format32bppArgb))
        {
            using (Graphics baseGraphics = Graphics.FromImage(baseBitmap))
            {
                baseGraphics.Clear(Color.Transparent);
                baseGraphics.CompositingMode = CompositingMode.SourceCopy;
                baseGraphics.InterpolationMode = InterpolationMode.NearestNeighbor;
                baseGraphics.PixelOffsetMode = PixelOffsetMode.Half;
                baseGraphics.SmoothingMode = SmoothingMode.None;
                baseGraphics.DrawImage(baseSource, new Rectangle(0, 0, 64, 72));
            }

            foreach (ItemSpec item in Items)
            {
                using (Bitmap scaled = ScaleCardToMaster(source, item))
                {
                    bool[,] mask = BuildMask(scaled, baseBitmap, item);
                    RemoveTinyComponents(mask, 10);
                    using (Bitmap layer = ApplyMask(scaled, mask, item.Palette))
                    using (Bitmap canonicalLayer = FitToCanonicalRig(layer, item))
                    {
                        Bitmap master = ScaleToMaster64(canonicalLayer);
                        if (item.Key == "shoes_sneakers")
                        {
                            Bitmap symmetricShoes = SymmetrizeShoes(master);
                            master.Dispose();
                            master = symmetricShoes;
                        }
                        if (item.Key == "top_blue_tshirt")
                        {
                            FitBlueShirtToBody(master);
                        }
                        if (NeedsInteriorCleanup(item.Key))
                        {
                            CleanInteriorPaletteNoise(
                                master,
                                ColorTranslator.FromHtml("#262b31")
                            );
                        }
                        // Every BASIC item uses the same one-pixel outer ink.
                        // This keeps the set visually coherent and guarantees
                        // that no light resize residue survives on a silhouette.
                        EnforceOuterOutline(master, ColorTranslator.FromHtml("#262b31"));

                        try
                        {
                            string masterPath = Path.Combine(outputDirectory, item.Key + ".png");
                            string runtimePath = Path.Combine(outputDirectory, item.Key + "_48x56.png");
                            master.Save(masterPath, ImageFormat.Png);
                            WriteRuntimeSprite(master, runtimePath);

                            if (item.Key == "face_headphones")
                            {
                                using (Bitmap back = CreateHeadphonePart(master, false))
                                using (Bitmap front = CreateHeadphonePart(master, true))
                                {
                                    string backPath = Path.Combine(outputDirectory, "face_headphones_back.png");
                                    string frontPath = Path.Combine(outputDirectory, "face_headphones_front.png");
                                    back.Save(backPath, ImageFormat.Png);
                                    front.Save(frontPath, ImageFormat.Png);
                                    WriteRuntimeSprite(
                                        back,
                                        Path.Combine(outputDirectory, "face_headphones_back_48x56.png")
                                    );
                                    WriteRuntimeSprite(
                                        front,
                                        Path.Combine(outputDirectory, "face_headphones_front_48x56.png")
                                    );
                                }
                            }

                            if (item.Key == "top_blue_tshirt" || item.Key == "shoes_sneakers")
                            {
                                using (Bitmap occlusion = CreateOcclusionMask(master, item.Key))
                                {
                                    string occlusionPath = Path.Combine(
                                        outputDirectory,
                                        item.Key + "_occlusion.png"
                                    );
                                    occlusion.Save(occlusionPath, ImageFormat.Png);
                                    WriteRuntimeSprite(
                                        occlusion,
                                        Path.Combine(
                                            outputDirectory,
                                            item.Key + "_occlusion_48x56.png"
                                        )
                                    );
                                }
                            }
                        }
                        finally
                        {
                            master.Dispose();
                        }
                    }
                }
            }
        }
    }
}
'@
}

[BasicLayerExtractor]::Extract($sourcePath, $basePath, $outputDirectory)
Get-ChildItem -LiteralPath $outputDirectory -File |
    Sort-Object Name |
    Select-Object -ExpandProperty FullName
