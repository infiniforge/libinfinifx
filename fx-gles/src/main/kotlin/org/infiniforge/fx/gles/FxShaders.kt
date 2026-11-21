package org.infiniforge.fx.gles

internal object FxShaders {
    const val COLOR_HELPERS = """
        vec3 fxSrgbDecode(vec3 c) {
            bvec3 low = lessThanEqual(c, vec3(0.04045));
            vec3 a = c / 12.92;
            vec3 b = pow((c + 0.055) / 1.055, vec3(2.4));
            return mix(b, a, low);
        }
        vec3 fxSrgbEncode(vec3 c) {
            c = max(c, vec3(0.0));
            bvec3 low = lessThanEqual(c, vec3(0.0031308));
            vec3 a = c * 12.92;
            vec3 b = 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055;
            return clamp(mix(b, a, low), 0.0, 1.0);
        }
        vec3 fxDecode(vec3 c, int encoding) {
            return encoding == 0 ? c : fxSrgbDecode(c);
        }
        vec3 fxEncode(vec3 c, int encoding) {
            return encoding == 0 ? max(c, vec3(0.0)) : fxSrgbEncode(c);
        }
        vec4 fxToLinearPremul(vec4 c, int alphaMode, int encoding) {
            if (alphaMode == 0) c.a = 1.0;
            vec3 rgb = c.rgb;
            if (alphaMode == 2 && c.a > 0.000001) rgb /= c.a;
            return vec4(fxDecode(rgb, encoding) * c.a, c.a);
        }
        vec4 fxFromLinearPremul(vec4 c, int alphaMode, int encoding) {
            float alpha = clamp(c.a, 0.0, 1.0);
            if (alphaMode == 0) return vec4(fxEncode(c.rgb, encoding), 1.0);
            vec3 rgb = alpha > 0.000001 ? c.rgb / alpha : vec3(0.0);
            rgb = fxEncode(rgb, encoding);
            if (alphaMode == 2) rgb *= alpha;
            return vec4(rgb, alpha);
        }
        vec2 fxMapUv(vec2 uv, vec4 rect, int rotation) {
            if (rotation == 90) uv = vec2(uv.y, 1.0 - uv.x);
            else if (rotation == 180) uv = vec2(1.0 - uv.x, 1.0 - uv.y);
            else if (rotation == 270) uv = vec2(1.0 - uv.y, uv.x);
            return rect.xy + uv * rect.zw;
        }
    """

    const val VERTEX = """
        #version 300 es
        precision highp float;
        out vec2 vUv;
        void main() {
            float x = float((gl_VertexID << 1) & 2);
            float y = float(gl_VertexID & 2);
            vUv = vec2(x, y);
            gl_Position = vec4(x * 2.0 - 1.0, y * 2.0 - 1.0, 0.0, 1.0);
        }
    """

    const val COPY = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uInput;
        uniform int uAlphaMode0;
        uniform int uColorEncoding0;
        uniform vec4 uUvRect0;
        uniform int uOrientation0;
        uniform int uOutputAlphaMode;
        uniform int uOutputColorEncoding;
        out vec4 fragColor;
        void main() {
            vec4 src = texture(uInput, fxMapUv(vUv, uUvRect0, uOrientation0));
            if (uAlphaMode0 == uOutputAlphaMode && uColorEncoding0 == uOutputColorEncoding) {
                fragColor = src;
            } else {
                fragColor = fxFromLinearPremul(fxToLinearPremul(src, uAlphaMode0, uColorEncoding0),
                                               uOutputAlphaMode, uOutputColorEncoding);
            }
        }
    """

    const val CRT = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uInput;
        uniform vec2 uSourceSize;
        uniform float uScanline;
        uniform float uBeamWidth;
        uniform float uMaskStrength;
        uniform float uMaskPitch;
        uniform int uMask;
        uniform float uCurvature;
        uniform float uVignette;
        uniform int uAlphaMode0;
        uniform int uColorEncoding0;
        uniform vec4 uUvRect0;
        uniform int uOrientation0;
        uniform int uOutputAlphaMode;
        uniform int uOutputColorEncoding;
        out vec4 fragColor;
        void main() {
            vec2 centered = vUv * 2.0 - 1.0;
            float r2 = dot(centered, centered);
            vec2 uv = vUv + centered * r2 * uCurvature;
            if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) {
                fragColor = vec4(0.0);
                return;
            }
            vec4 src = fxToLinearPremul(texture(uInput, fxMapUv(uv, uUvRect0, uOrientation0)), uAlphaMode0, uColorEncoding0);
            float row = fract(vUv.y * uSourceSize.y);
            float edge = abs(row - 0.5) * 2.0;
            float beam = 1.0 - uScanline * smoothstep(uBeamWidth, 1.0, edge);
            int triad = int(floor(gl_FragCoord.x / max(0.5, uMaskPitch))) % 3;
            vec3 mask = vec3(1.0 - uMaskStrength * 0.48);
            if (uMask != 0) {
                if (triad == 0) mask.r = 1.0;
                else if (triad == 1) mask.g = 1.0;
                else mask.b = 1.0;
                if (uMask == 2) mask *= 1.0 - 0.06 * sin(gl_FragCoord.y * 3.14159265);
            } else {
                mask = vec3(1.0);
            }
            float vignette = 1.0 - uVignette * smoothstep(0.15, 1.2, r2);
            fragColor = fxFromLinearPremul(vec4(src.rgb * beam * mask * vignette, src.a),
                                           uOutputAlphaMode, uOutputColorEncoding);
        }
    """

    const val LCD = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uInput;
        uniform vec2 uSourceSize;
        uniform int uPalette;
        uniform float uContrast;
        uniform float uGamma;
        uniform float uPixelGap;
        uniform int uAlphaMode0;
        uniform int uColorEncoding0;
        uniform vec4 uUvRect0;
        uniform int uOrientation0;
        uniform int uOutputAlphaMode;
        uniform int uOutputColorEncoding;
        out vec4 fragColor;
        void main() {
            vec2 pixel = floor(vUv * uSourceSize);
            vec2 local = fract(vUv * uSourceSize);
            vec2 uv = (pixel + vec2(0.5)) / uSourceSize;
            vec4 src = fxToLinearPremul(texture(uInput, fxMapUv(uv, uUvRect0, uOrientation0)), uAlphaMode0, uColorEncoding0);
            float alpha = src.a;
            vec3 straight = alpha > 0.000001 ? src.rgb / alpha : vec3(0.0);
            float lum = dot(straight, vec3(0.2126, 0.7152, 0.0722));
            lum = pow(clamp((lum - 0.5) * uContrast + 0.5, 0.0, 1.0), 1.0 / uGamma);
            vec3 color = straight;
            if (uPalette == 1) {
                color = vec3(lum);
            } else if (uPalette == 2) {
                float shade = floor(lum * 4.0);
                color = shade < 1.0 ? fxSrgbDecode(vec3(0.055, 0.14, 0.075))
                      : shade < 2.0 ? fxSrgbDecode(vec3(0.16, 0.31, 0.18))
                      : shade < 3.0 ? fxSrgbDecode(vec3(0.40, 0.53, 0.27))
                      : fxSrgbDecode(vec3(0.74, 0.80, 0.43));
            }
            float interior = step(uPixelGap * 0.5, local.x) * step(uPixelGap * 0.5, local.y)
                           * step(local.x, 1.0 - uPixelGap * 0.5) * step(local.y, 1.0 - uPixelGap * 0.5);
            color *= mix(1.0 - uPixelGap * 0.4, 1.0, interior);
            fragColor = fxFromLinearPremul(vec4(color * alpha, alpha), uOutputAlphaMode, uOutputColorEncoding);
        }
    """

    const val ROTOZOOM = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uInput;
        uniform float uTime;
        uniform float uZoom;
        uniform float uZoomPulse;
        uniform float uRotation;
        uniform float uAudioPulse;
        uniform int uEdgeMode;
        uniform int uAlphaMode0;
        uniform int uColorEncoding0;
        uniform vec4 uUvRect0;
        uniform int uOrientation0;
        uniform int uOutputAlphaMode;
        uniform int uOutputColorEncoding;
        out vec4 fragColor;
        vec2 wrapUv(vec2 uv) {
            if (uEdgeMode == 1) return fract(uv);
            if (uEdgeMode == 2) return 1.0 - abs(mod(uv, 2.0) - 1.0);
            return clamp(uv, vec2(0.001), vec2(0.999));
        }
        void main() {
            float breathing = 1.0 + uZoomPulse * sin(uTime * 0.73);
            float zoom = max(0.15, uZoom * breathing + uAudioPulse * 0.28);
            float angle = uRotation + uAudioPulse * 0.16;
            float c = cos(angle);
            float s = sin(angle);
            vec2 p = (vUv - vec2(0.5)) * zoom;
            vec2 uv = mat2(c, -s, s, c) * p + vec2(0.5);
            vec4 sampleColor = texture(uInput, fxMapUv(wrapUv(uv), uUvRect0, uOrientation0));
            vec4 linear = fxToLinearPremul(sampleColor, uAlphaMode0, uColorEncoding0);
            fragColor = fxFromLinearPremul(linear, uOutputAlphaMode, uOutputColorEncoding);
        }
    """

    const val KALEIDOSCOPE = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uInput;
        uniform int uSegments;
        uniform float uRotation;
        uniform float uZoom;
        uniform int uEdgeMode;
        uniform int uAlphaMode0;
        uniform int uColorEncoding0;
        uniform vec4 uUvRect0;
        uniform int uOrientation0;
        uniform int uOutputAlphaMode;
        uniform int uOutputColorEncoding;
        out vec4 fragColor;
        vec2 wrapUv(vec2 uv) {
            if (uEdgeMode == 1) return fract(uv);
            if (uEdgeMode == 2) return 1.0 - abs(mod(uv, 2.0) - 1.0);
            return clamp(uv, vec2(0.001), vec2(0.999));
        }
        void main() {
            const float tau = 6.28318530718;
            vec2 p = vUv - vec2(0.5);
            float radius = length(p) * uZoom;
            float sector = tau / float(max(3, uSegments));
            float angle = atan(p.y, p.x) + uRotation;
            float folded = abs(mod(angle + 0.5 * sector, sector) - 0.5 * sector);
            vec2 uv = vec2(0.5) + radius * vec2(cos(folded), sin(folded));
            vec4 sampleColor = texture(uInput, fxMapUv(wrapUv(uv), uUvRect0, uOrientation0));
            vec4 linear = fxToLinearPremul(sampleColor, uAlphaMode0, uColorEncoding0);
            fragColor = fxFromLinearPremul(linear, uOutputAlphaMode, uOutputColorEncoding);
        }
    """

    const val WATER = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uInput;
        uniform vec2 uInputSize;
        uniform float uTime;
        uniform float uDepth;
        uniform float uScale;
        uniform float uRoughness;
        uniform float uDistortion;
        uniform int uCaustics;
        uniform float uAudio;
        uniform int uAlphaMode0;
        uniform int uColorEncoding0;
        uniform vec4 uUvRect0;
        uniform int uOrientation0;
        uniform int uOutputAlphaMode;
        uniform int uOutputColorEncoding;
        out vec4 fragColor;
        float heightAt(vec2 p, float t) {
            float broad =
                0.48 * sin(dot(p, vec2(1.0, 0.42)) * uScale + t * 0.73) +
                0.31 * sin(dot(p, vec2(-0.36, 1.0)) * uScale * 0.83 - t * 0.51) +
                0.21 * sin(dot(p, vec2(0.72, 0.91)) * uScale * 0.62 + t * 0.37);
            float chop =
                0.55 * sin(dot(p, vec2(1.0, 0.18)) * uScale * 2.7 + t * 1.31) *
                      sin(dot(p, vec2(-0.24, 1.0)) * uScale * 1.8 - t * 0.89);
            return broad * (1.0 - 0.62 * uRoughness) + chop * uRoughness;
        }
        void main() {
            vec2 p = vec2(vUv.x, vUv.y * (uInputSize.y / max(1.0, uInputSize.x)));
            float t = uTime;
            vec2 eps = 1.0 / max(vec2(1.0), uInputSize);
            float hx = heightAt(p + vec2(eps.x, 0.0), t) - heightAt(p - vec2(eps.x, 0.0), t);
            float hy = heightAt(p + vec2(0.0, eps.y), t) - heightAt(p - vec2(0.0, eps.y), t);
            float audioScale = 1.0 + clamp(uAudio, 0.0, 1.0);
            vec2 offset = vec2(hx, hy) * uDepth * uDistortion * audioScale * 3.0;
            vec2 uv = clamp(vUv + offset, vec2(0.001), vec2(0.999));
            vec4 color = fxToLinearPremul(texture(uInput, fxMapUv(uv, uUvRect0, uOrientation0)), uAlphaMode0, uColorEncoding0);
            if (uCaustics == 1) {
                float glint = pow(clamp(abs(hx * hy) * 18.0, 0.0, 1.0), 2.0) * 0.08;
                color.rgb += glint * color.a;
            }
            fragColor = fxFromLinearPremul(color, uOutputAlphaMode, uOutputColorEncoding);
        }
    """

    const val HEAT_STEP = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uField;
        uniform float uDt;
        uniform float uCooling;
        uniform float uEnergy;
        uniform float uBurst;
        uniform float uTurbulence;
        uniform float uSeed;
        uniform float uTime;
        out vec4 fragColor;
        float hash(vec2 p) {
            p = fract(p * vec2(123.34, 456.21) + uSeed);
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }
        void main() {
            vec4 old = texture(uField, vUv);
            vec2 velocity = old.gb * 2.0 - 1.0;
            vec2 drift = velocity * 0.12;
            vec4 advected = texture(uField, clamp(vUv - drift, vec2(0.002), vec2(0.998)));
            float noise = hash(vUv * 97.0 + uTime * 0.7) - 0.5;
            float baseSource = 1.0 - smoothstep(0.0, 0.13, vUv.y);
            float vent = 0.35 + 0.65 * hash(vec2(floor(vUv.x * 18.0), 2.0) + uSeed);
            float source = baseSource * vent * (0.38 + uEnergy * 0.75);
            vec2 burstPos = vec2(0.5 + noise * 0.14, 0.08);
            float burst = uBurst * exp(-dot((vUv - burstPos) * vec2(1.0, 2.6), (vUv - burstPos) * vec2(1.0, 2.6)) * 180.0);
            float temperature = max(0.0, advected.r * exp(-uCooling * uDt) + (source + burst) * uDt);
            float vx = clamp((advected.g * 2.0 - 1.0) * 0.985 + noise * uTurbulence * uDt * 0.18, -0.08, 0.08);
            float vy = clamp((advected.b * 2.0 - 1.0) * 0.99 + temperature * uDt * 0.10, -0.02, 0.16);
            fragColor = vec4(clamp(temperature, 0.0, 1.0), vx * 0.5 + 0.5, vy * 0.5 + 0.5, clamp(temperature, 0.0, 1.0));
        }
    """

    const val HEAT_RIPPLE = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uInput;
        uniform sampler2D uField;
        uniform vec2 uFieldSize;
        uniform float uRefraction;
        uniform int uAlphaMode0;
        uniform int uColorEncoding0;
        uniform vec4 uUvRect0;
        uniform int uOrientation0;
        uniform int uOutputAlphaMode;
        uniform int uOutputColorEncoding;
        out vec4 fragColor;
        void main() {
            vec2 e = 1.0 / max(uFieldSize, vec2(1.0));
            float l = texture(uField, vUv - vec2(e.x, 0.0)).r;
            float r = texture(uField, vUv + vec2(e.x, 0.0)).r;
            float d = texture(uField, vUv - vec2(0.0, e.y)).r;
            float u = texture(uField, vUv + vec2(0.0, e.y)).r;
            vec2 gradient = vec2(r - l, u - d);
            vec2 uv = clamp(vUv + gradient * uRefraction * vec2(16.0, 10.0), vec2(0.001), vec2(0.999));
            vec4 image = fxToLinearPremul(texture(uInput, fxMapUv(uv, uUvRect0, uOrientation0)), uAlphaMode0, uColorEncoding0);
            fragColor = fxFromLinearPremul(image, uOutputAlphaMode, uOutputColorEncoding);
        }
    """

    const val FIRE = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uField;
        uniform float uIntensity;
        uniform int uOutputAlphaMode;
        uniform int uOutputColorEncoding;
        out vec4 fragColor;
        void main() {
            float h = texture(uField, vUv).r * uIntensity;
            float alpha = smoothstep(0.025, 0.32, h) * clamp(h * 1.45, 0.0, 1.0);
            vec3 color = mix(vec3(0.32, 0.015, 0.002), vec3(1.0, 0.12, 0.005), smoothstep(0.05, 0.55, h));
            color = mix(color, vec3(1.0, 0.76, 0.18), smoothstep(0.45, 0.95, h));
            color = mix(color, vec3(1.0), smoothstep(0.92, 1.2, h) * 0.32);
            vec4 linear = vec4(fxSrgbDecode(color) * alpha, alpha);
            fragColor = fxFromLinearPremul(linear, uOutputAlphaMode, uOutputColorEncoding);
        }
    """

    const val BLEND = """
        #version 300 es
        precision highp float;
        in vec2 vUv;
        uniform sampler2D uBase;
        uniform sampler2D uOverlay;
        uniform float uOpacity;
        uniform int uMode;
        uniform int uAlphaMode0;
        uniform int uAlphaMode1;
        uniform int uColorEncoding0;
        uniform int uColorEncoding1;
        uniform vec4 uUvRect0;
        uniform int uOrientation0;
        uniform int uOutputAlphaMode;
        uniform int uOutputColorEncoding;
        out vec4 fragColor;
        void main() {
            vec4 base = fxToLinearPremul(texture(uBase, fxMapUv(vUv, uUvRect0, uOrientation0)), uAlphaMode0, uColorEncoding0);
            vec4 overlay = fxToLinearPremul(texture(uOverlay, vUv), uAlphaMode1, uColorEncoding1) * uOpacity;
            vec4 result;
            if (uMode == 1) {
                result = vec4(base.rgb + overlay.rgb, clamp(base.a + overlay.a, 0.0, 1.0));
            } else {
                result = overlay + base * (1.0 - overlay.a);
            }
            fragColor = fxFromLinearPremul(result, uOutputAlphaMode, uOutputColorEncoding);
        }
    """
}
