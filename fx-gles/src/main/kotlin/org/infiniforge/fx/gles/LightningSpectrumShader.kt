package org.infiniforge.fx.gles

/** Original procedural lightning spectrum shader; the host supplies normalized bands and time. */
internal object LightningSpectrumShader {
    const val LIGHTNING_SPECTRUM = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform vec4 uBands0;
        uniform vec4 uBands1;
        uniform vec4 uBands2;
        uniform vec4 uBands3;
        uniform int uBandCount;
        uniform int uAudioPresent;
        uniform float uTime;
        uniform float uSeed;
        uniform float uGain;
        uniform float uMaxHeight;
        uniform float uWidthScale;
        uniform float uBarGap;
        uniform float uGlow;
        uniform float uIdleHeight;
        uniform vec3 uPalette0;
        uniform vec3 uPalette1;
        uniform vec3 uPalette2;
        uniform vec3 uPalette3;
        uniform vec3 uPalette4;
        uniform vec3 uPalette5;
        uniform vec3 uPalette6;
        uniform vec3 uPalette7;
        uniform int uPaletteCount;
        uniform int uOutputAlphaMode;
        uniform int uOutputColorEncoding;
        out vec4 fragColor;

        float hash(vec2 p) {
            p = fract(p * vec2(123.34, 456.21) + uSeed);
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }

        float sampleBand(int index) {
            if (index < 4) return uBands0[index];
            if (index < 8) return uBands1[index - 4];
            if (index < 12) return uBands2[index - 8];
            return uBands3[index - 12];
        }

        vec3 samplePalette(int index) {
            if (index == 0) return uPalette0;
            if (index == 1) return uPalette1;
            if (index == 2) return uPalette2;
            if (index == 3) return uPalette3;
            if (index == 4) return uPalette4;
            if (index == 5) return uPalette5;
            if (index == 6) return uPalette6;
            return uPalette7;
        }

        void main() {
            const float bandsAcross = 16.0;
            float contentX = (vUv.x - 0.5) / clamp(uWidthScale, 0.5, 1.0) + 0.5;
            if (contentX < 0.0 || contentX > 1.0) {
                fragColor = vec4(0.0);
                return;
            }
            int slot = min(int(floor(contentX * bandsAcross)), 15);
            float cellX = fract(contentX * bandsAcross);
            float sourceBand = 0.0;
            if (uBandCount > 0) {
                int sourceIndex = min(int((float(slot) + 0.5) * float(uBandCount) / bandsAcross), uBandCount - 1);
                sourceBand = sampleBand(sourceIndex);
            }
            float idlePulse = 0.72 + 0.28 * sin(uTime * 2.1 + float(slot) * 1.73 + uSeed * 6.28318);
            float level = uAudioPresent == 1
                ? clamp(max(sourceBand, 0.0) * uGain, 0.0, 1.0)
                : clamp(uIdleHeight * idlePulse, 0.0, 1.0);
            float height = uMaxHeight * level;
            if (height <= 0.000001) {
                fragColor = vec4(0.0);
                return;
            }
            float y = vUv.y / height;
            float insideHeight = step(0.0, y) * step(y, 1.0);
            float edgeJitter = (hash(vec2(float(slot), floor(y * 22.0 + uTime * 8.0))) - 0.5) * 0.06;
            float halfWidth = max(0.02, 0.5 * (1.0 - uBarGap));
            float jaggedHalfWidth = max(0.015, halfWidth + edgeJitter);
            float insideColumn = step(abs(cellX - 0.5), jaggedHalfWidth) * insideHeight;

            float segment = y * 17.0 + uTime * 5.0;
            float segmentIndex = floor(segment);
            float segmentPhase = smoothstep(0.12, 0.88, fract(segment));
            float noiseA = hash(vec2(float(slot) + 7.0, segmentIndex));
            float noiseB = hash(vec2(float(slot) + 7.0, segmentIndex + 1.0));
            float zigzag = mix(noiseA, noiseB, segmentPhase) - 0.5;
            float spine = 0.5 + 0.14 * sin(y * 15.0 + uTime * 2.7 + float(slot) * 1.91) + zigzag * 0.48;
            float coreDistance = abs(cellX - spine);
            float core = (1.0 - smoothstep(0.012, 0.030, coreDistance)) * insideColumn;
            float outline = (1.0 - smoothstep(0.032, 0.068, coreDistance)) * insideColumn;
            float halo = exp(-coreDistance * 46.0) * insideColumn;

            float forkStartA = 0.24 + 0.24 * hash(vec2(float(slot), 3.0));
            float forkStartB = 0.63 + 0.18 * hash(vec2(float(slot), 9.0));
            float forkYA = y - forkStartA;
            float forkYB = y - forkStartB;
            float forkXA = spine + forkYA * (hash(vec2(float(slot), 4.0)) < 0.5 ? -1.45 : 1.45);
            float forkXB = spine + forkYB * (hash(vec2(float(slot), 8.0)) < 0.5 ? -1.2 : 1.2);
            float forkDistanceA = abs(cellX - forkXA);
            float forkDistanceB = abs(cellX - forkXB);
            float forkRangeA = step(0.0, forkYA) * step(forkYA, 0.20) * insideColumn;
            float forkRangeB = step(0.0, forkYB) * step(forkYB, 0.15) * insideColumn;
            float forkA = (1.0 - smoothstep(0.010, 0.024, forkDistanceA)) * forkRangeA;
            float forkB = (1.0 - smoothstep(0.009, 0.022, forkDistanceB)) * forkRangeB;
            float forkOutlineA = (1.0 - smoothstep(0.028, 0.060, forkDistanceA)) * forkRangeA;
            float forkOutlineB = (1.0 - smoothstep(0.026, 0.056, forkDistanceB)) * forkRangeB;
            float flicker = 0.68 + 0.32 * sin(uTime * 31.0 + float(slot) * 5.7 + noiseA * 9.0);
            float activity = max(level, 0.08) * flicker;

            float outlineShape = max(outline, max(forkOutlineA, forkOutlineB));
            float coreShape = max(core, max(forkA, forkB));
            float electric = clamp(coreShape * activity * uGlow, 0.0, 1.0);
            float outlineAlpha = clamp(outlineShape * activity * uGlow * 0.72, 0.0, 1.0);
            float alpha = clamp(outlineAlpha + electric + halo * activity * uGlow * 0.12, 0.0, 1.0);
            int paletteIndex = slot % max(uPaletteCount, 1);
            vec3 palette = fxSrgbDecode(samplePalette(paletteIndex));
            vec3 electricColor = mix(palette, vec3(0.92, 0.98, 1.0), clamp(coreShape * 0.9, 0.0, 1.0));
            vec3 premultiplied = clamp(
                palette * outlineAlpha + electricColor * electric + palette * halo * activity * uGlow * 0.12,
                vec3(0.0), vec3(alpha)
            );
            fragColor = fxFromLinearPremul(vec4(premultiplied, alpha), uOutputAlphaMode, uOutputColorEncoding);
        }
    """
}
