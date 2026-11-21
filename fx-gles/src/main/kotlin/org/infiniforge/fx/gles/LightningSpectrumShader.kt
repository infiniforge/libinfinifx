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
        uniform float uBarGap;
        uniform float uGlow;
        uniform float uIdleHeight;
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

        vec3 bandColor(float hue) {
            return 0.42 + 0.58 * cos(6.2831853 * (hue + vec3(0.0, 0.34, 0.67)));
        }

        void main() {
            const float bandsAcross = 16.0;
            const float baseY = 0.025;
            int slot = min(int(floor(vUv.x * bandsAcross)), 15);
            float cellX = fract(vUv.x * bandsAcross);
            float sourceBand = 0.0;
            if (uBandCount > 0) {
                int sourceIndex = min(int((float(slot) + 0.5) * float(uBandCount) / bandsAcross), uBandCount - 1);
                sourceBand = sampleBand(sourceIndex);
            }
            float idlePulse = 0.72 + 0.28 * sin(uTime * 2.1 + float(slot) * 1.73 + uSeed * 6.28318);
            float level = uAudioPresent == 1
                ? clamp(pow(max(sourceBand, 0.0), 0.68) * uGain, 0.0, 1.0)
                : uIdleHeight * idlePulse;
            // Keep a readable autonomous floor even when the analyzer returns a present-but-silent band array.
            level = max(level, uIdleHeight);
            float height = uMaxHeight * (0.035 + level * 0.92);
            float y = (vUv.y - baseY) / max(height, 0.0001);
            float insideHeight = step(0.0, y) * step(y, 1.0);
            float edgeJitter = (hash(vec2(float(slot), floor(y * 22.0 + uTime * 8.0))) - 0.5) * 0.06;
            float halfWidth = max(0.02, 0.5 * (1.0 - uBarGap));
            float jaggedHalfWidth = max(0.015, halfWidth + edgeJitter);
            float body = (1.0 - smoothstep(jaggedHalfWidth - 0.012, jaggedHalfWidth + 0.012, abs(cellX - 0.5))) * insideHeight;

            float segment = y * 17.0 + uTime * 5.0;
            float segmentIndex = floor(segment);
            float segmentPhase = smoothstep(0.12, 0.88, fract(segment));
            float noiseA = hash(vec2(float(slot) + 7.0, segmentIndex));
            float noiseB = hash(vec2(float(slot) + 7.0, segmentIndex + 1.0));
            float zigzag = mix(noiseA, noiseB, segmentPhase) - 0.5;
            float spine = 0.5 + 0.14 * sin(y * 15.0 + uTime * 2.7 + float(slot) * 1.91) + zigzag * 0.48;
            float coreDistance = abs(cellX - spine);
            float core = (1.0 - smoothstep(0.018, 0.065, coreDistance)) * insideHeight;
            float halo = exp(-coreDistance * 34.0) * insideHeight;

            float forkStartA = 0.24 + 0.24 * hash(vec2(float(slot), 3.0));
            float forkStartB = 0.63 + 0.18 * hash(vec2(float(slot), 9.0));
            float forkYA = y - forkStartA;
            float forkYB = y - forkStartB;
            float forkXA = spine + forkYA * (hash(vec2(float(slot), 4.0)) < 0.5 ? -1.45 : 1.45);
            float forkXB = spine + forkYB * (hash(vec2(float(slot), 8.0)) < 0.5 ? -1.2 : 1.2);
            float forkA = (1.0 - smoothstep(0.012, 0.04, abs(cellX - forkXA))) * step(0.0, forkYA) * step(forkYA, 0.20) * insideHeight;
            float forkB = (1.0 - smoothstep(0.010, 0.034, abs(cellX - forkXB))) * step(0.0, forkYB) * step(forkYB, 0.15) * insideHeight;
            float flicker = 0.68 + 0.32 * sin(uTime * 31.0 + float(slot) * 5.7 + noiseA * 9.0);
            float activity = max(level, 0.08) * flicker;

            float hue = fract(float(slot) / bandsAcross + 0.03 * sin(uTime * 0.18));
            vec3 color = bandColor(hue);
            float electric = clamp((core + 0.62 * (forkA + forkB)) * activity * uGlow, 0.0, 1.0);
            float alpha = clamp(body * (0.28 + 0.42 * level) * uGlow + electric + halo * activity * uGlow * 0.13, 0.0, 1.0);
            vec3 electricColor = mix(color, vec3(0.84, 0.96, 1.0), clamp(core * 0.86 + (forkA + forkB) * 0.42, 0.0, 1.0));
            vec3 premultiplied = clamp(
                color * body * 0.46 + electricColor * electric + vec3(0.46, 0.76, 1.0) * halo * activity * uGlow * 0.13,
                vec3(0.0), vec3(alpha)
            );
            fragColor = fxFromLinearPremul(vec4(premultiplied, alpha), uOutputAlphaMode, uOutputColorEncoding);
        }
    """
}
