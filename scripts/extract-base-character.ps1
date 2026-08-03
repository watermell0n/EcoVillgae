param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

$sourcePath = Join-Path $RepositoryRoot 'docs\assets\character\base-character-4-body-refined-v2.png'
$outputDirectory = Join-Path $RepositoryRoot 'student-app\public\character\base'
$masterOutputPath = Join-Path $outputDirectory 'base_char_base.png'
$runtimeOutputPath = Join-Path $outputDirectory 'base_char_base_48x56.png'

if (-not (Test-Path -LiteralPath $sourcePath)) {
    throw "BASE reference image was not found: $sourcePath"
}

New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

Add-Type -AssemblyName System.Drawing

if (-not ('BaseCharacterExtractor' -as [type])) {
    Add-Type -ReferencedAssemblies 'System.Drawing' -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;

public static class BaseCharacterExtractor
{
    // The second character in the tracked refined reference is the BASE body.
    // All master sprites share one 64x64 logical canvas. The body keeps a
    // transparent crown margin for hats and a bottom margin for footwear.
    private static readonly Rectangle SourceBounds = new Rectangle(664, 180, 336, 448);
    private static readonly Rectangle MasterTargetBounds = new Rectangle(8, 4, 48, 57);
    private static readonly Rectangle RuntimeTargetBounds = new Rectangle(4, 2, 40, 53);
    private static readonly Color[] BasePalette =
    {
        ColorTranslator.FromHtml("#262b31"),
        ColorTranslator.FromHtml("#dce2e1"),
        ColorTranslator.FromHtml("#eef1f0"),
        ColorTranslator.FromHtml("#fffdf8"),
        ColorTranslator.FromHtml("#f1a3b5"),
    };

    private static bool IsBackground(Color color)
    {
        int maximum = Math.Max(color.R, Math.Max(color.G, color.B));
        int minimum = Math.Min(color.R, Math.Min(color.G, color.B));

        return color.A == 0
            || (minimum >= 220 && maximum - minimum <= 35);
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

    private static Color Quantize(Color color)
    {
        bool cheek = color.R >= 205
            && color.G <= 205
            && color.B <= 215
            && color.R - color.G >= 20
            && color.B >= color.G;
        if (cheek)
        {
            Color cheekColor = BasePalette[BasePalette.Length - 1];
            return Color.FromArgb(255, cheekColor.R, cheekColor.G, cheekColor.B);
        }

        Color best = BasePalette[0];
        long bestDistance = long.MaxValue;
        for (int index = 0; index < BasePalette.Length - 1; index++)
        {
            Color candidate = BasePalette[index];
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

    private static void EnforceOuterOutline(Bitmap bitmap)
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

        Color outline = BasePalette[0];
        for (int y = 0; y < bitmap.Height; y++)
        {
            for (int x = 0; x < bitmap.Width; x++)
            {
                if (boundary[x, y])
                {
                    bitmap.SetPixel(x, y, Color.FromArgb(255, outline.R, outline.G, outline.B));
                }
            }
        }
    }

    private static void WriteSprite(
        Bitmap crop,
        int width,
        int height,
        Rectangle targetBounds,
        string outputPath
    )
    {
        using (Bitmap target = new Bitmap(width, height, PixelFormat.Format32bppArgb))
        {
            using (Graphics targetGraphics = Graphics.FromImage(target))
            {
                targetGraphics.Clear(Color.Transparent);
                targetGraphics.CompositingMode = CompositingMode.SourceCopy;
                targetGraphics.InterpolationMode = InterpolationMode.NearestNeighbor;
                targetGraphics.PixelOffsetMode = PixelOffsetMode.Half;
                targetGraphics.SmoothingMode = SmoothingMode.None;
                targetGraphics.DrawImage(
                    crop,
                    targetBounds,
                    new Rectangle(0, 0, crop.Width, crop.Height),
                    GraphicsUnit.Pixel
                );
            }

            for (int y = 0; y < target.Height; y++)
            {
                for (int x = 0; x < target.Width; x++)
                {
                    Color color = target.GetPixel(x, y);
                    if (color.A < 128)
                    {
                        target.SetPixel(x, y, Color.Transparent);
                    }
                    else
                    {
                        target.SetPixel(x, y, Quantize(color));
                    }
                }
            }

            EnforceOuterOutline(target);

            target.Save(outputPath, ImageFormat.Png);
        }
    }

    public static void Extract(
        string sourcePath,
        string masterOutputPath,
        string runtimeOutputPath
    )
    {
        using (Bitmap source = new Bitmap(sourcePath))
        using (Bitmap crop = new Bitmap(SourceBounds.Width, SourceBounds.Height, PixelFormat.Format32bppArgb))
        {
            using (Graphics cropGraphics = Graphics.FromImage(crop))
            {
                cropGraphics.Clear(Color.Transparent);
                cropGraphics.CompositingMode = CompositingMode.SourceCopy;
                cropGraphics.DrawImage(
                    source,
                    new Rectangle(0, 0, crop.Width, crop.Height),
                    SourceBounds,
                    GraphicsUnit.Pixel
                );
            }

            RemoveConnectedBackground(crop);
            WriteSprite(crop, 64, 64, MasterTargetBounds, masterOutputPath);
            WriteSprite(crop, 48, 56, RuntimeTargetBounds, runtimeOutputPath);
        }
    }
}
'@
}

[BaseCharacterExtractor]::Extract($sourcePath, $masterOutputPath, $runtimeOutputPath)
Write-Output $masterOutputPath
Write-Output $runtimeOutputPath
