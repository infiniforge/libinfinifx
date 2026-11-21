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
        uniform float uGlow;
        uniform float uIdleHeight;
        uniform float uAnimationSpeed;
        uniform int uBranchCount;
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

        float randomSignal(float coordinate, float key) {
            float segment = floor(coordinate);
            float phase = fract(coordinate);
            float sampleA = hash(vec2(key, segment));
            float sampleB = hash(vec2(key, segment + 1.0));
            return mix(sampleA, sampleB, phase) - 0.5;
        }

        float spineAt(float y, float slot, float animationTime) {
            float broadJitter = randomSignal(y * 21.0 + animationTime * 1.8, slot + 17.0) * 0.82;
            float fineJitter = randomSignal(y * 47.0 + animationTime * 3.2, slot + 67.0) * 0.24;
            return clamp(0.5 + broadJitter + fineJitter, 0.08, 0.92);
        }

        void main() {
            const float bandsAcross = 16.0;
            float animationTime = uTime * clamp(uAnimationSpeed, 0.0, 40.0);
            int branchCount = clamp(uBranchCount, 1, 8);
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
            float idlePulse = 0.44 + 0.56 * (randomSignal(animationTime * 1.7 + float(slot) * 0.37, float(slot) + 101.0) + 0.5);
            float level = uAudioPresent == 1
                ? clamp(max(sourceBand, 0.0) * uGain, 0.0, 1.0)
                : clamp(uIdleHeight * idlePulse, 0.0, 1.0);
            float height = uMaxHeight * level;
            if (height <= 0.000001) {
                fragColor = vec4(0.0);
                return;
            }
            float y = vUv.y / height;
            float yAa = max(fwidth(y), 0.00001);
            float insideHeight = step(0.0, y) * step(y, 1.0);
            float noiseA = randomSignal(animationTime * 3.5 + float(slot) * 0.29, float(slot) + 211.0) + 0.5;
            float spine = spineAt(y, float(slot), animationTime);
            float spineDistance = abs(cellX - spine);
            float spineAa = max(fwidth(spineDistance), 0.00001);
            float core = 1.0 - smoothstep(spineAa * 0.05, spineAa * 0.55, spineDistance);
            float outline = 1.0 - smoothstep(spineAa * 0.45, spineAa * 0.9, spineDistance);
            float halo = exp(-spineDistance / (spineAa * 3.0));
            float forkCore = 0.0;
            float forkOutline = 0.0;
            float forkHalo = 0.0;

            // Each thin side branch gets a shorter secondary twig for a fuller, forked silhouette.
            for (int branch = 0; branch < 8; branch++) {
                if (branch < branchCount) {
                    float branchId = float(branch);
                    float start = 0.10 + 0.76 * hash(vec2(float(slot) * 13.0 + branchId, 3.0 + branchId));
                    float branchLength = 0.12 + 0.20 * hash(vec2(float(slot) * 7.0 + branchId, 5.0 + branchId));
                    float side = hash(vec2(float(slot) + branchId * 3.0, 8.0 + branchId)) < 0.5 ? -1.0 : 1.0;
                    float branchY = y - start;
                    float branchOrigin = spineAt(start, float(slot), animationTime);
                    float branchReach = (side > 0.0 ? 1.0 - branchOrigin : branchOrigin)
                        * (0.35 + 0.55 * hash(vec2(float(slot) + branchId, 11.0 + branchId)));
                    float slope = branchReach / branchLength;
                    float branchWiggle = randomSignal(branchY * 31.0 + animationTime * 2.4, float(slot) * 17.0 + branchId) * 0.08;
                    float branchX = branchOrigin + branchY * side * slope + branchY * branchWiggle;
                    float branchRange = smoothstep(-yAa, yAa, branchY)
                        * (1.0 - smoothstep(branchLength - yAa, branchLength + yAa, branchY)) * insideHeight;
                    float branchDistance = abs(cellX - branchX);
                    float branchAa = max(fwidth(branchDistance), 0.00001);
                    float branchLine = (1.0 - smoothstep(branchAa * 0.05, branchAa * 0.55, branchDistance)) * branchRange;
                    float branchEdge = (1.0 - smoothstep(branchAa * 0.45, branchAa * 0.9, branchDistance)) * branchRange;
                    float branchLight = exp(-branchDistance / (branchAa * 3.0)) * branchRange;
                    forkCore = max(forkCore, branchLine);
                    forkOutline = max(forkOutline, branchEdge);
                    forkHalo = max(forkHalo, branchLight);

                    float twigStart = branchLength * 0.42;
                    float twigY = branchY - twigStart;
                    float twigSide = side * (hash(vec2(float(slot) + branchId, 19.0 + branchId)) < 0.5 ? -1.0 : 1.0);
                    float twigLength = branchLength * (0.28 + 0.28 * hash(vec2(float(slot) * 5.0 + branchId, 23.0 + branchId)));
                    float twigWiggleAtStart = randomSignal(twigStart * 31.0 + animationTime * 2.4, float(slot) * 17.0 + branchId) * 0.08;
                    float twigOrigin = branchOrigin + twigStart * side * slope + twigStart * twigWiggleAtStart;
                    float twigReach = (twigSide > 0.0 ? 1.0 - twigOrigin : twigOrigin)
                        * (0.35 + 0.55 * hash(vec2(float(slot) + branchId, 29.0 + branchId)));
                    float twigSlope = twigReach / twigLength;
                    float twigWiggle = randomSignal(twigY * 43.0 + animationTime * 2.8, float(slot) * 23.0 + branchId) * 0.065;
                    float twigX = twigOrigin + twigY * twigSide * twigSlope + twigY * twigWiggle;
                    float twigRange = smoothstep(-yAa, yAa, twigY)
                        * (1.0 - smoothstep(twigLength - yAa, twigLength + yAa, twigY)) * branchRange;
                    float twigDistance = abs(cellX - twigX);
                    float twigAa = max(fwidth(twigDistance), 0.00001);
                    float twigLine = (1.0 - smoothstep(twigAa * 0.05, twigAa * 0.55, twigDistance)) * twigRange;
                    float twigEdge = (1.0 - smoothstep(twigAa * 0.45, twigAa * 0.9, twigDistance)) * twigRange;
                    float twigLight = exp(-twigDistance / (twigAa * 3.0)) * twigRange;
                    forkCore = max(forkCore, twigLine);
                    forkOutline = max(forkOutline, twigEdge);
                    forkHalo = max(forkHalo, twigLight);
                }
            }

            float flicker = 0.65 + 0.35 * (randomSignal(animationTime * 4.3 + float(slot) * 0.41 + noiseA, float(slot) + 307.0) + 0.5);
            float activity = max(level, 0.08) * flicker;

            float outlineShape = max(outline, forkOutline) * insideHeight;
            float coreShape = max(core, forkCore) * insideHeight;
            halo = max(halo, forkHalo) * insideHeight;
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
