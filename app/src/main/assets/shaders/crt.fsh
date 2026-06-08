#version 300 es

// CRT post-processing pass used in place of the plain blit. It does NOT try to be a pixel accurate CRT emulator
// (that looks bad on high res games like Undertale's 640x480 upscaled to a phone), it just evokes the CRT feeling
precision highp float;

in vec2 vTexCoord;
uniform sampler2D uTexture;
uniform vec2 uResolution;
out vec4 fragColor;

// Gentle barrel distortion so the image bows out like the curved glass of a CRT
vec2 curveRemap(vec2 uv) {
    uv = uv * 2.0 - 1.0;
    vec2 offset = abs(uv.yx) / vec2(7.0, 5.0);
    uv += uv * offset * offset;
    return uv * 0.5 + 0.5;
}

void main() {
    vec2 uv = curveRemap(vTexCoord);

    // The curvature pushes the corners past the panel edge, paint that area as the black bezel
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // Subtle horizontal chromatic aberration, stronger toward the edges where a CRT lens fringes the most
    float edge = distance(uv, vec2(0.5));
    float aberration = edge / uResolution.x * 4.0;
    vec3 color;
    color.r = texture(uTexture, uv + vec2(aberration, 0.0)).r;
    color.g = texture(uTexture, uv).g;
    color.b = texture(uTexture, uv - vec2(aberration, 0.0)).b;

    // Scanlines locked to PHYSICAL pixels (not game pixels) so a 640x480 game and an HD game look equally CRT-ish
    // fract keeps the sin argument tiny, dodging mobile GPU large argument precision loss on tall screens
    // A wider period plus a sharpened (pow) dark band keeps the lines visible on dense phone panels instead of averaging out
    float scanPhase = fract(gl_FragCoord.y / 5.0);
    float scan = 0.5 + 0.5 * sin(scanPhase * 6.2831853);
    scan = pow(scan, 2.0);
    color *= 1.0 - 0.30 * scan;

    // Faint aperture grille columns for a hint of phosphor structure
    float maskPhase = fract(gl_FragCoord.x / 3.0);
    float mask = 0.5 + 0.5 * sin(maskPhase * 6.2831853);
    color *= 1.0 - 0.05 * mask;

    // Vignette so the brightness falls off toward the corners
    float vignette = uv.x * (1.0 - uv.x) * uv.y * (1.0 - uv.y);
    vignette = clamp(pow(vignette * 16.0, 0.20), 0.0, 1.0);
    color *= vignette;

    // Compensate for the darkening the scanlines, mask and vignette introduce
    color *= 1.18;

    fragColor = vec4(color, 1.0);
}
