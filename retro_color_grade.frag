precision highp float;
uniform sampler2D uTexture;
uniform float uIntensity;
uniform float uSaturation;
uniform float uContrast;
uniform float uWarmth;
uniform float uGrain;
uniform float uVignette;
uniform float uFade;
uniform float uScanlines;
uniform float uChroma;
uniform vec2 uResolution;
varying vec2 vTexCoord;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
void main() {
    vec2 uv = vTexCoord;
    float ca = uChroma * uIntensity;
    vec3 c = vec3(texture2D(uTexture, uv + vec2(ca, 0.0)).r,
                  texture2D(uTexture, uv).g,
                  texture2D(uTexture, uv - vec2(ca, 0.0)).b);
    float gray = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(gray), c, mix(1.0, uSaturation, uIntensity));
    c = (c - 0.5) * mix(1.0, uContrast, uIntensity) + 0.5;
    c += vec3(0.055, 0.018, -0.045) * uWarmth * uIntensity;
    vec2 centered = uv - 0.5;
    float vignette = 1.0 - uVignette * uIntensity * clamp(dot(centered, centered) * 2.0, 0.0, 1.0);
    float grain = (hash(floor(uv * uResolution) + vec2(17.0, 31.0)) - 0.5) * uGrain * uIntensity;
    float scan = mod(floor(uv.y * uResolution.y), 3.0) < 1.0 ? 1.0 - uScanlines * uIntensity : 1.0;
    c = c * vignette * scan + grain + uFade * uIntensity;
    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
