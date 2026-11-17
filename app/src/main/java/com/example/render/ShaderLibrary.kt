package com.example.render

import android.opengl.GLES30
import android.util.Log

object ShaderLibrary {
    private const val TAG = "MaxMotionShaders"

    const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTexCoord;
uniform mat4 uMvpMatrix;
uniform mat4 uModelMatrix;
out vec2 vTexCoord;
out vec3 vWorldPos;
void main() {
    gl_Position = uMvpMatrix * aPosition;
    vWorldPos = (uModelMatrix * aPosition).xyz;
    vTexCoord = aTexCoord;
}
"""

    const val FULLSCREEN_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vTexCoord;
void main() {
    gl_Position = aPosition;
    vTexCoord = aTexCoord;
}
"""

    const val COMPOSITE_FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec2 vTexCoord;
in vec3 vWorldPos;
out vec4 fragColor;

uniform sampler2D uSourceTex;
uniform sampler2D uBackdropTex;
uniform int uBlendMode;
uniform float uOpacity;
uniform int uIsPremultiplied;
uniform int uHasMask;
uniform int uMaskShape; // 0=rect, 1=circle, 2=star
uniform int uMaskMode;  // 0=add, 1=sub, 2=intersect
uniform int uMaskInverted;
uniform vec4 uMaskRect; // cx, cy, halfW, halfH (in 0..1 layer UV)
uniform float uMaskFeather;
uniform float uMaskOpacity;

// 3D Lighting
uniform int uLightCount;
uniform vec4 uLightPos[4];   // xyz = worldPos, w = type (0=ambient, 1=point, 2=directional)
uniform vec4 uLightColor[4]; // rgb = color, a = intensity
uniform float uLightRadius[4];

// Depth of Field (DOF)
uniform float uLayerPosZ;
uniform float uFocusDistance;
uniform float uDofBlur;

// Color Grading: RGB Curves (256x1) & 3D LUT Atlas (NxN x N)
uniform int uHasCurves;
uniform sampler2D uCurvesTex;
uniform int uHasLut;
uniform sampler2D uColorLutTex;
uniform float uLutSize;
uniform float uLutIntensity;

vec3 rgb2hsl(vec3 c) {
    float maxC = max(max(c.r, c.g), c.b);
    float minC = min(min(c.r, c.g), c.b);
    float l = (maxC + minC) * 0.5;
    float s = 0.0;
    float h = 0.0;
    if (maxC != minC) {
        float d = maxC - minC;
        s = l > 0.5 ? d / (2.0 - maxC - minC) : d / (maxC + minC);
        if (maxC == c.r) h = (c.g - c.b) / d + (c.g < c.b ? 6.0 : 0.0);
        else if (maxC == c.g) h = (c.b - c.r) / d + 2.0;
        else h = (c.r - c.g) / d + 4.0;
        h /= 6.0;
    }
    return vec3(h, s, l);
}

float hue2rgb(float p, float q, float t) {
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0/6.0) return p + (q - p) * 6.0 * t;
    if (t < 1.0/2.0) return q;
    if (t < 2.0/3.0) return p + (q - p) * (2.0/3.0 - t) * 6.0;
    return p;
}

vec3 hsl2rgb(vec3 hsl) {
    if (hsl.y == 0.0) return vec3(hsl.z);
    float q = hsl.z < 0.5 ? hsl.z * (1.0 + hsl.y) : hsl.z + hsl.y - hsl.z * hsl.y;
    float p = 2.0 * hsl.z - q;
    return vec3(
        hue2rgb(p, q, hsl.x + 1.0/3.0),
        hue2rgb(p, q, hsl.x),
        hue2rgb(p, q, hsl.x - 1.0/3.0)
    );
}

vec3 applyBlend(vec3 base, vec3 top, int mode) {
    if (mode == 1) return base * top; // Multiply
    if (mode == 2) return 1.0 - (1.0 - base) * (1.0 - top); // Screen
    if (mode == 3) return min(base + top, vec3(1.0)); // Add / Linear Dodge
    if (mode == 4) { // Overlay
        return mix(2.0 * base * top, 1.0 - 2.0 * (1.0 - base) * (1.0 - top), step(0.5, base));
    }
    if (mode == 5) return min(base, top); // Darken
    if (mode == 6) return max(base, top); // Lighten
    if (mode == 7) return min(vec3(1.0), base / max(vec3(0.001), 1.0 - top)); // Color Dodge
    if (mode == 8) return 1.0 - min(vec3(1.0), (1.0 - base) / max(vec3(0.001), top)); // Color Burn
    if (mode == 9) { // Hard Light
        return mix(2.0 * base * top, 1.0 - 2.0 * (1.0 - base) * (1.0 - top), step(0.5, top));
    }
    if (mode == 10) { // Soft Light
        return (1.0 - 2.0 * top) * base * base + 2.0 * top * base;
    }
    if (mode == 11) return abs(base - top); // Difference
    if (mode == 12) return base + top - 2.0 * base * top; // Exclusion
    if (mode == 13) return max(base - top, vec3(0.0)); // Subtract
    if (mode == 14) return min(vec3(1.0), base / max(vec3(0.001), top)); // Divide
    if (mode == 15) { // Hue
        vec3 bh = rgb2hsl(base); vec3 th = rgb2hsl(top);
        return hsl2rgb(vec3(th.x, bh.y, bh.z));
    }
    if (mode == 16) { // Saturation
        vec3 bh = rgb2hsl(base); vec3 th = rgb2hsl(top);
        return hsl2rgb(vec3(bh.x, th.y, bh.z));
    }
    if (mode == 17) { // Color
        vec3 bh = rgb2hsl(base); vec3 th = rgb2hsl(top);
        return hsl2rgb(vec3(th.x, th.y, bh.z));
    }
    if (mode == 18) { // Luminosity
        vec3 bh = rgb2hsl(base); vec3 th = rgb2hsl(top);
        return hsl2rgb(vec3(bh.x, bh.y, th.z));
    }
    if (mode == 19) return max(base + top - 1.0, vec3(0.0)); // Linear Burn
    if (mode == 20) return clamp(mix(1.0 - (1.0 - base) / max(vec3(0.001), 2.0 * top), base / max(vec3(0.001), 2.0 * (1.0 - top)), step(0.5, top)), 0.0, 1.0); // Vivid Light
    if (mode == 21) return clamp(base + 2.0 * top - 1.0, 0.0, 1.0); // Linear Light
    if (mode == 22) return mix(min(base, 2.0 * top), max(base, 2.0 * (top - 0.5)), step(0.5, top)); // Pin Light
    if (mode == 23) return step(1.0, base + top); // Hard Mix
    if (mode == 24) { // Darker Color
        float lb = dot(base, vec3(0.299, 0.587, 0.114));
        float lt = dot(top, vec3(0.299, 0.587, 0.114));
        return lb < lt ? base : top;
    }
    if (mode == 25) { // Lighter Color
        float lb = dot(base, vec3(0.299, 0.587, 0.114));
        float lt = dot(top, vec3(0.299, 0.587, 0.114));
        return lb > lt ? base : top;
    }
    return top;
}

float computeMaskAlpha(vec2 uv) {
    vec2 d = abs(uv - uMaskRect.xy);
    float dist = 0.0;
    if (uMaskShape == 1) { // Circle
        vec2 normD = (uv - uMaskRect.xy) / max(uMaskRect.zw, vec2(0.001));
        dist = length(normD) - 1.0;
    } else if (uMaskShape == 2) { // Star-ish diamond/star
        vec2 p = (uv - uMaskRect.xy) / max(uMaskRect.zw, vec2(0.001));
        float a = atan(p.y, p.x);
        float r = length(p);
        float starR = 0.72 + 0.28 * cos(a * 5.0);
        dist = r - starR;
    } else { // Rect
        vec2 q = d - uMaskRect.zw;
        dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
    }
    float feather = max(0.004, uMaskFeather);
    float m = 1.0 - smoothstep(-feather, feather, dist);
    if (uMaskInverted == 1) m = 1.0 - m;
    if (uMaskMode == 1) m = 1.0 - m; // Subtract
    return mix(1.0, m, uMaskOpacity);
}

vec4 sampleWithDof(vec2 uv) {
    float coc = clamp(abs(uLayerPosZ - uFocusDistance) / 380.0, 0.0, 1.0) * uDofBlur;
    if (coc <= 0.02) {
        return texture(uSourceTex, uv);
    }
    float r = coc * 0.014;
    vec4 acc = texture(uSourceTex, uv) * 2.0;
    acc += texture(uSourceTex, clamp(uv + vec2(r, 0.0), 0.0, 1.0));
    acc += texture(uSourceTex, clamp(uv - vec2(r, 0.0), 0.0, 1.0));
    acc += texture(uSourceTex, clamp(uv + vec2(0.0, r), 0.0, 1.0));
    acc += texture(uSourceTex, clamp(uv - vec2(0.0, r), 0.0, 1.0));
    acc += texture(uSourceTex, clamp(uv + vec2(r * 0.7, r * 0.7), 0.0, 1.0));
    acc += texture(uSourceTex, clamp(uv + vec2(-r * 0.7, r * 0.7), 0.0, 1.0));
    acc += texture(uSourceTex, clamp(uv + vec2(r * 0.7, -r * 0.7), 0.0, 1.0));
    acc += texture(uSourceTex, clamp(uv + vec2(-r * 0.7, -r * 0.7), 0.0, 1.0));
    return acc / 10.0;
}

void main() {
    vec4 src = sampleWithDof(vTexCoord);
    vec4 bg = texture(uBackdropTex, vTexCoord);
    float maskA = (uHasMask == 1) ? computeMaskAlpha(vTexCoord) : 1.0;
    float alpha = clamp(src.a * uOpacity * maskA, 0.0, 1.0);
    if (uBlendMode == 26) { // Stencil Alpha
        fragColor = vec4(bg.rgb, bg.a * alpha);
        return;
    }

    vec3 graded = (uIsPremultiplied == 1 && src.a > 0.002)
        ? clamp(src.rgb / src.a, 0.0, 1.0)
        : src.rgb;

    // 1. RGB Curves (256x1 LUT)
    if (uHasCurves == 1) {
        graded.r = texture(uCurvesTex, vec2(clamp(graded.r, 0.0, 1.0), 0.5)).r;
        graded.g = texture(uCurvesTex, vec2(clamp(graded.g, 0.0, 1.0), 0.5)).g;
        graded.b = texture(uCurvesTex, vec2(clamp(graded.b, 0.0, 1.0), 0.5)).b;
    }

    // 2. 3D LUT Atlas (.cube)
    if (uHasLut == 1 && uLutSize > 1.5) {
        float n = uLutSize;
        float blueColor = clamp(graded.b, 0.0, 1.0) * (n - 1.0);
        float b0 = floor(blueColor);
        float b1 = min(n - 1.0, b0 + 1.0);
        float fracB = blueColor - b0;
        float cellW = 1.0 / n;
        float uOff = (clamp(graded.r, 0.0, 1.0) * (n - 1.0) + 0.5) / (n * n);
        float vCoord = (clamp(graded.g, 0.0, 1.0) * (n - 1.0) + 0.5) / n;
        vec3 c0 = texture(uColorLutTex, vec2(b0 * cellW + uOff, vCoord)).rgb;
        vec3 c1 = texture(uColorLutTex, vec2(b1 * cellW + uOff, vCoord)).rgb;
        vec3 lutCol = mix(c0, c1, fracB);
        graded = mix(graded, lutCol, uLutIntensity);
    }

    // 3. 3D Scene Lighting
    if (uLightCount > 0) {
        vec3 litAccum = vec3(0.18);
        for (int i = 0; i < 4; i++) {
            if (i >= uLightCount) break;
            int lType = int(uLightPos[i].w + 0.5);
            vec3 lCol = uLightColor[i].rgb * uLightColor[i].a;
            if (lType == 0) { // Ambient
                litAccum += lCol * 0.65;
            } else if (lType == 2) { // Directional
                vec3 dir = normalize(uLightPos[i].xyz - vec3(0.0, 0.0, -300.0));
                float ndotl = max(0.25, abs(dir.z) * 0.8 + 0.35);
                litAccum += lCol * ndotl;
            } else { // Point
                float d = length(uLightPos[i].xyz - vWorldPos);
                float att = clamp(1.0 - d / max(80.0, uLightRadius[i]), 0.0, 1.0);
                litAccum += lCol * (att * att * 1.45);
            }
        }
        graded = clamp(graded * litAccum, 0.0, 1.0);
    }

    vec3 blended = (uBlendMode == 0) ? graded : applyBlend(bg.rgb, graded, uBlendMode);
    vec3 outRgb = mix(bg.rgb, blended, alpha);
    float outA = max(bg.a, alpha);
    fragColor = vec4(outRgb, outA);
}
"""

    const val EFFECT_FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform int uEffectType;
uniform float uTimeSec;
uniform vec2 uResolution;
uniform float uP0;
uniform float uP1;
uniform float uP2;
uniform float uP3;
uniform float uP4;
uniform float uP5;
uniform float uP6;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 mirrorTile(vec2 uv) {
    vec2 m = mod(uv, 2.0);
    return vec2(
        m.x > 1.0 ? 2.0 - m.x : m.x,
        m.y > 1.0 ? 2.0 - m.y : m.y
    );
}

void main() {
    vec2 uv = vTexCoord;

    // 0: MOTION_TILE (tileW, tileH, mirrorEdges, phase)
    if (uEffectType == 0) {
        vec2 tiled = (uv - 0.5) * vec2(max(0.2, uP0), max(0.2, uP1)) + 0.5 + vec2(uP3, 0.0);
        vec2 sampleUv = (uP2 > 0.5) ? mirrorTile(abs(tiled)) : fract(tiled);
        fragColor = texture(uTexture, sampleUv);
        return;
    }

    // 1: S_SHAKE (amp, freq, rotAmp, seed)
    if (uEffectType == 1) {
        float t = uTimeSec * uP1 + uP3;
        float dx = sin(t * 2.3 + cos(t * 1.1)) * uP0;
        float dy = cos(t * 1.9 + sin(t * 1.4)) * uP0;
        float ang = sin(t * 1.7) * radians(uP2);
        vec2 c = uv - 0.5;
        float cs = cos(ang); float sn = sin(ang);
        vec2 rotUv = vec2(c.x * cs - c.y * sn, c.x * sn + c.y * cs) + 0.5 + vec2(dx, dy);
        fragColor = texture(uTexture, mirrorTile(abs(rotUv)));
        return;
    }

    // 2: WAVE_WARP (height, width, speed)
    if (uEffectType == 2) {
        float wave = sin(uv.y * uP1 + uTimeSec * uP2) * uP0;
        fragColor = texture(uTexture, clamp(vec2(uv.x + wave, uv.y), 0.0, 1.0));
        return;
    }

    // 3: TURBULENT_DISPLACE (amount, size, evolution)
    if (uEffectType == 3) {
        float nx = sin(uv.x * uP1 + uTimeSec * uP2) * cos(uv.y * uP1 * 0.8);
        float ny = cos(uv.y * uP1 - uTimeSec * uP2) * sin(uv.x * uP1 * 0.9);
        fragColor = texture(uTexture, clamp(uv + vec2(nx, ny) * uP0, 0.0, 1.0));
        return;
    }

    // 4: TWIRL (angle, radius)
    if (uEffectType == 4) {
        vec2 d = uv - 0.5;
        float dist = length(d);
        if (dist < uP1) {
            float pct = (uP1 - dist) / max(0.001, uP1);
            float theta = pct * pct * uP0;
            float s = sin(theta); float c = cos(theta);
            d = vec2(dot(d, vec2(c, -s)), dot(d, vec2(s, c)));
        }
        fragColor = texture(uTexture, clamp(d + 0.5, 0.0, 1.0));
        return;
    }

    // 5: SPHERIZE (strength, radius)
    if (uEffectType == 5) {
        vec2 d = uv - 0.5;
        float dist = length(d);
        if (dist < uP1) {
            float k = dist / max(0.001, uP1);
            d *= mix(1.0, k * k, uP0);
        }
        fragColor = texture(uTexture, clamp(d + 0.5, 0.0, 1.0));
        return;
    }

    // 6: OPTICS_COMPENSATION (fov)
    if (uEffectType == 6) {
        vec2 d = uv - 0.5;
        float r2 = dot(d, d);
        vec2 warped = 0.5 + d * (1.0 + uP0 * r2 * 2.5);
        fragColor = texture(uTexture, clamp(warped, 0.0, 1.0));
        return;
    }

    // 7: DEEP_GLOW (intensity, radius, threshold)
    if (uEffectType == 7) {
        vec4 base = texture(uTexture, uv);
        vec3 bloom = vec3(0.0);
        vec2 texel = (1.0 / max(uResolution, vec2(1.0))) * (uP1 * 28.0);
        float totalW = 0.0;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                float w = 1.0 / (1.0 + float(x*x + y*y));
                vec3 s = texture(uTexture, clamp(uv + vec2(float(x), float(y)) * texel, 0.0, 1.0)).rgb;
                float lum = dot(s, vec3(0.299, 0.587, 0.114));
                bloom += max(vec3(0.0), s - vec3(uP2)) * step(uP2 * 0.5, lum) * w;
                totalW += w;
            }
        }
        bloom = (bloom / max(totalW, 0.001)) * uP0 * 2.2;
        float bloomA = max(base.a, clamp(max(bloom.r, max(bloom.g, bloom.b)), 0.0, 1.0));
        fragColor = vec4(min(vec3(1.0), base.rgb + bloom), bloomA);
        return;
    }

    // 8: MOTION_BLUR / DIRECTIONAL_BLUR (shutterAngle, angleDeg, strength)
    if (uEffectType == 8) {
        float rad = radians(uP1);
        vec2 dir = vec2(cos(rad), sin(rad)) * uP2 * 0.03 * (uP0 / 180.0);
        vec4 acc = vec4(0.0);
        for (int i = -4; i <= 4; i++) {
            acc += texture(uTexture, clamp(uv + dir * float(i) * 0.25, 0.0, 1.0));
        }
        fragColor = acc / 9.0;
        return;
    }

    // 9: GAUSSIAN_BLUR (radius)
    if (uEffectType == 9) {
        vec2 stepUv = (1.0 / max(uResolution, vec2(1.0))) * (uP0 * 20.0);
        vec4 acc = vec4(0.0);
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                acc += texture(uTexture, clamp(uv + vec2(float(x), float(y)) * stepUv, 0.0, 1.0));
            }
        }
        fragColor = acc / 25.0;
        return;
    }

    // 10: ZOOM_BLUR / 12: LIGHT_RAYS
    if (uEffectType == 10 || uEffectType == 12) {
        vec2 dir = (uv - 0.5) * uP0 * 0.08;
        vec4 acc = vec4(0.0);
        float weight = 1.0;
        float sumW = 0.0;
        for (int i = 0; i < 8; i++) {
            acc += texture(uTexture, clamp(uv - dir * float(i) * 0.14, 0.0, 1.0)) * weight;
            sumW += weight;
            if (uEffectType == 12) weight *= max(0.5, uP1);
        }
        fragColor = acc / max(sumW, 0.001);
        return;
    }

    // 11: RADIAL_BLUR (angle)
    if (uEffectType == 11) {
        vec2 d = uv - 0.5;
        vec4 acc = vec4(0.0);
        for (int i = -3; i <= 3; i++) {
            float a = float(i) * uP0 * 0.04;
            float cs = cos(a); float sn = sin(a);
            vec2 ruv = vec2(d.x * cs - d.y * sn, d.x * sn + d.y * cs) + 0.5;
            acc += texture(uTexture, clamp(ruv, 0.0, 1.0));
        }
        fragColor = acc / 7.0;
        return;
    }

    // 13: CHROMATIC_ABERRATION (intensity, angleDeg)
    if (uEffectType == 13) {
        float rad = radians(uP1);
        vec2 offset = vec2(cos(rad), sin(rad)) * uP0;
        float r = texture(uTexture, clamp(uv + offset, 0.0, 1.0)).r;
        vec4 g = texture(uTexture, uv);
        float b = texture(uTexture, clamp(uv - offset, 0.0, 1.0)).b;
        fragColor = vec4(r, g.g, b, g.a);
        return;
    }

    // 14: VHS_SCANLINES (scanDensity, jitter, chromaShift)
    if (uEffectType == 14) {
        float j = (hash12(vec2(floor(uv.y * uP0), floor(uTimeSec * 24.0))) - 0.5) * uP1 * 0.015;
        vec2 suv = clamp(vec2(uv.x + j, uv.y), 0.0, 1.0);
        float r = texture(uTexture, clamp(suv + vec2(uP2 * 0.008, 0.0), 0.0, 1.0)).r;
        vec4 c = texture(uTexture, suv);
        float b = texture(uTexture, clamp(suv - vec2(uP2 * 0.008, 0.0), 0.0, 1.0)).b;
        float scan = 0.86 + 0.14 * sin(uv.y * uP0 * 3.14159);
        fragColor = vec4(vec3(r, c.g, b) * scan, c.a);
        return;
    }

    // 15: HALFTONE (dotScale, contrast)
    if (uEffectType == 15) {
        vec4 c = texture(uTexture, uv);
        vec2 grid = fract(uv * uP0) - 0.5;
        float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
        float dotMask = smoothstep(lum * 0.72, lum * 0.72 - 0.12, length(grid));
        fragColor = vec4(mix(c.rgb, c.rgb * dotMask, uP1), c.a);
        return;
    }

    // 16: PIXELATE (pixelSize)
    if (uEffectType == 16) {
        float cells = max(4.0, 300.0 / max(1.0, uP0) * 6.0);
        vec2 pUv = (floor(uv * cells) + 0.5) / cells;
        fragColor = texture(uTexture, clamp(pUv, 0.0, 1.0));
        return;
    }

    // 18: EDGE_DETECT (threshold, glowMix)
    if (uEffectType == 18) {
        vec2 tx = 1.0 / max(uResolution, vec2(1.0));
        vec4 c = texture(uTexture, uv);
        vec3 cx = texture(uTexture, clamp(uv + vec2(tx.x, 0.0), 0.0, 1.0)).rgb - texture(uTexture, clamp(uv - vec2(tx.x, 0.0), 0.0, 1.0)).rgb;
        vec3 cy = texture(uTexture, clamp(uv + vec2(0.0, tx.y), 0.0, 1.0)).rgb - texture(uTexture, clamp(uv - vec2(0.0, tx.y), 0.0, 1.0)).rgb;
        float edge = smoothstep(uP0 * 0.4, uP0, length(cx) + length(cy));
        vec3 neon = vec3(0.0, 0.9, 1.0) * edge;
        fragColor = vec4(mix(c.rgb, neon, uP1), c.a);
        return;
    }

    // 19: VIGNETTE (amount, roundness)
    if (uEffectType == 19) {
        vec4 c = texture(uTexture, uv);
        vec2 d = (uv - 0.5) * mix(vec2(1.4, 1.4), vec2(1.8, 1.2), uP1);
        float vig = smoothstep(0.9, 0.25, length(d));
        fragColor = vec4(c.rgb * mix(1.0, vig, uP0), c.a);
        return;
    }

    // 20: CHROMA_KEY (hueTarget, tolerance, spillSuppression)
    if (uEffectType == 20) {
        vec4 c = texture(uTexture, uv);
        float gExcess = c.g - max(c.r, c.b);
        float keyMask = 1.0 - smoothstep(0.02, max(0.04, uP1), gExcess);
        vec3 despilled = vec3(c.r, min(c.g, mix(c.g, max(c.r, c.b), uP2)), c.b);
        fragColor = vec4(despilled, c.a * keyMask);
        return;
    }

    // 21: COLOR_GRADING (exposure, contrast, saturation, temp, lift, gamma, gain)
    if (uEffectType == 21) {
        vec4 c = texture(uTexture, uv);
        vec3 col = c.rgb * pow(2.0, uP0);
        col = (col - 0.5) * uP1 + 0.5;
        float luma = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(vec3(luma), col, uP2);
        col.r += uP3 * 0.12;
        col.b -= uP3 * 0.12;
        col = clamp(col + uP4 * 0.2, 0.0, 1.0);
        col = pow(max(col, vec3(0.0001)), vec3(1.0 / max(0.2, uP5))) * uP6;
        fragColor = vec4(clamp(col, 0.0, 1.0), c.a);
        return;
    }

    fragColor = texture(uTexture, uv);
}
"""

    fun compileProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        if (vs == 0 || fs == 0) {
            if (vs != 0) GLES30.glDeleteShader(vs)
            if (fs != 0) GLES30.glDeleteShader(fs)
            return 0
        }
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Program link error: ${GLES30.glGetProgramInfoLog(program)}")
            GLES30.glDeleteProgram(program)
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            return 0
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile error: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
