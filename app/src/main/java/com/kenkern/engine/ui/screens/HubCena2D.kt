package com.kenkern.engine.ui.screens

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Qualidade do shader de buraco negro no fundo do Hub (C1).
 * ALTA = 3 camadas de estrelas com paralaxe + 28 passos geodésicos.
 * BAIXA = 2 camadas de estrelas + 14 passos geodésicos (mais leve na GPU).
 */
enum class QualidadeFundoHub(
    val codigo: String,
    val uQuality: Float,
    val camadasEstrelas: Int,
    val passosGeodesicos: Int
) {
    ALTA(codigo = "alta", uQuality = 1f, camadasEstrelas = 3, passosGeodesicos = 28),
    BAIXA(codigo = "baixa", uQuality = 0f, camadasEstrelas = 2, passosGeodesicos = 14);

    fun alternar(): QualidadeFundoHub = if (this == ALTA) BAIXA else ALTA

    companion object {
        fun fromCodigo(codigo: String?): QualidadeFundoHub =
            entries.firstOrNull { it.codigo == codigo } ?: ALTA
    }
}

/**
 * Matemática pura do fundo do Hub e contrato dos shaders GLSL ES 3.0 do
 * buraco negro em tela cheia (C1):
 *  - renderizado a 50% da resolução em FBO offscreen e ampliado;
 *  - 30 FPS ([FPS_ALVO], [INTERVALO_NS]);
 *  - pausa fora do Hub;
 *  - "reduzir animações" = frame estático único ([animacoesReduzidas]);
 *  - opção Qualidade do fundo Alta/Baixa ([QualidadeFundoHub]);
 *  - zero alocação por frame (saídas em arrays [out] pré-alocados).
 */
object HubCena2D {
    /** FPS alvo do fundo (30 FPS no Hub). */
    const val FPS_ALVO: Int = 30

    /** Intervalo entre frames em ns (1/30 s). */
    const val INTERVALO_NS: Long = 33_333_333L

    /** Tempo de UMA órbita de referência (s) — lento e sutil. */
    const val ORBITA_SEGUNDOS: Float = 18f

    /** Velocidade base da paralaxe (px/s por unidade de profundidade). */
    const val VELOCIDADE_PARALAXE: Float = 6f

    /** Escala de renderização offscreen: 50% da resolução nativa. */
    const val ESCALA_RESOLUCAO: Float = 0.5f

    /** Dimensão mínima segura do FBO offscreen (evita 0x0 durante transição). */
    const val TAMANHO_MINIMO_FBO: Int = 64

    /**
     * Calcula a resolução de 50% para o FBO offscreen do buraco negro sem
     * alocar objetos por frame. [out] recebe [larguraFbo, alturaFbo].
     */
    fun resolucaoFundo(larguraPx: Int, alturaPx: Int, out: IntArray) {
        val w = max(TAMANHO_MINIMO_FBO, (max(1, larguraPx) * ESCALA_RESOLUCAO).toInt())
        val h = max(TAMANHO_MINIMO_FBO, (max(1, alturaPx) * ESCALA_RESOLUCAO).toInt())
        out[0] = w
        out[1] = h
    }

    /**
     * Deslocamento HORIZONTAL de uma camada de estrelas (paralaxe):
     * camadas profundas andam menos ([profundidade] 0–1; 1 = mais perto).
     */
    fun deslocamentoCamada(tempoSegundos: Float, profundidade: Float, out: FloatArray) {
        val p = profundidade.coerceIn(0f, 1f)
        out[0] = -tempoSegundos * VELOCIDADE_PARALAXE * p
    }

    /**
     * Posição de um brilho em órbita ao redor do buraco negro. out = [x, y] normalizados.
     */
    fun pontoOrbita(tempoSegundos: Float, fase: Float, raio: Float, out: FloatArray) {
        val ang = tempoSegundos * (2f * PI.toFloat() / ORBITA_SEGUNDOS) + fase
        out[0] = cos(ang) * raio
        out[1] = sin(ang) * raio * 0.62f
    }

    /**
     * Pulso suave do disco de acreção (0,85–1,0).
     */
    fun pulsoAcrecao(tempoSegundos: Float): Float {
        val fase = sin(tempoSegundos * (2f * PI.toFloat() / (ORBITA_SEGUNDOS * 0.5f)))
        return 0.925f + 0.075f * fase
    }

    /**
     * "Reduzir animações" do sistema: escala <= 0,01 ou NaN congela o fundo
     * em uma imagem estática.
     */
    fun animacoesReduzidas(escalaDuracaoAnimator: Float): Boolean =
        !(escalaDuracaoAnimator.isFinite()) || escalaDuracaoAnimator <= 0.01f
}

/**
 * Shaders GLSL ES 3.0 do buraco negro em tela cheia (C1):
 *  - Passagem 1 (50% de resolução): traçado geodésico em espaço curvo de
 *    Schwarzschild (lente gravitacional sobre estrelas em paralaxe), horizonte
 *    de eventos, anel de fótons e disco de acreção inclinado com brilho
 *    Doppler assimétrico e rotação lenta.
 *  - Passagem 2: upscale bilinear do FBO de 50% para a tela inteira.
 */
object HubBlackHoleShaders {
    const val VERTEX_SHADER: String = """#version 300 es
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aUv;
out vec2 vUv;
void main() {
    vUv = aUv;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    const val BLACK_HOLE_FRAGMENT_SHADER: String = """#version 300 es
precision highp float;

in vec2 vUv;
out vec4 fragColor;

uniform vec2 uResolution;
uniform float uTime;
uniform float uQuality;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p, int octaves) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.80, -0.60, 0.60, 0.80);
    for (int i = 0; i < 4; i++) {
        if (i >= octaves) break;
        v += a * valueNoise(p);
        p = rot * p * 2.03 + vec2(17.1, 31.7);
        a *= 0.5;
    }
    return v;
}

vec3 starLayer(vec2 dirUv, float scale, float depth, float t) {
    vec2 p = dirUv * scale + vec2(t * 0.008 * depth, depth * 7.13);
    vec2 gv = fract(p) - 0.5;
    vec2 id = floor(p);
    vec2 rnd = hash22(id + depth * 19.3);
    vec2 offset = (rnd - 0.5) * 0.72;
    float d = length(gv - offset);
    float seed = hash21(id + depth * 43.1);
    float threshold = mix(0.86, 0.74, uQuality);
    float mask = smoothstep(threshold, 0.995, seed);
    float size = mix(0.025, 0.065, rnd.x);
    float core = exp(-d * d / max(size * size * 0.14, 0.0001));
    float halo = exp(-d / max(size * 0.55, 0.001)) * 0.32;
    vec3 coolStar = vec3(0.76, 0.86, 1.05);
    vec3 warmStar = vec3(1.05, 0.88, 0.70);
    vec3 tint = mix(coolStar, warmStar, rnd.y);
    float brightness = (0.35 + 0.65 * depth) * mask;
    return tint * (core + halo) * brightness;
}

vec3 backgroundSpace(vec3 dir, float deflectionGlow) {
    // Coordenadas esféricas suaves para campo de estrelas contínuo + paralaxe
    float lon = atan(dir.x, dir.z);
    float lat = asin(clamp(dir.y, -0.999, 0.999));
    vec2 skyUv = vec2(lon / 3.14159265, lat / 1.5707963);

    vec3 stars = vec3(0.012, 0.013, 0.024);
    stars += starLayer(skyUv, 14.0, 0.35, uTime) * 0.85;
    stars += starLayer(skyUv, 26.0, 0.65, uTime) * 1.05;
    if (uQuality > 0.5) {
        stars += starLayer(skyUv, 42.0, 1.00, uTime) * 1.25;
    }

    // Nebulosa galáctica discreta esticada pela lente perto do buraco negro
    float neb = fbm(skyUv * vec2(3.5, 5.0) + vec2(uTime * 0.003, 0.0), uQuality > 0.5 ? 4 : 2);
    float band = exp(-abs(skyUv.y + 0.15 * sin(skyUv.x * 2.2)) * 4.5);
    stars += vec3(0.05, 0.055, 0.11) * neb * band;
    stars += vec3(0.08, 0.06, 0.12) * clamp(deflectionGlow * 0.25, 0.0, 0.35);
    return stars;
}

void main() {
    float aspect = uResolution.x / max(uResolution.y, 1.0);
    vec2 screen = (vUv - 0.5) * vec2(aspect, 1.0);

    // Câmera inclinada para revelar arco superior/inferior e assimetria Doppler
    vec3 ro = vec3(0.0, 0.48, -3.15);
    vec3 target = vec3(-0.18, -0.02, 0.0);
    vec3 fwd = normalize(target - ro);
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), fwd));
    vec3 up = cross(fwd, right);

    float fov = 0.85;
    vec3 rd = normalize(fwd + screen.x * right * fov + screen.y * up * fov);

    float rs = 0.50;          // raio de Schwarzschild (buraco negro GRANDE no Hub)
    float rPhoton = rs * 1.48;
    float diskInner = rs * 1.62;
    float diskOuter = rs * 4.65;

    int steps = uQuality > 0.5 ? 44 : 22;
    float baseDt = uQuality > 0.5 ? 0.12 : 0.22;

    vec3 pos = ro;
    vec3 vel = rd;
    vec3 accumDisk = vec3(0.0);
    float transmittance = 1.0;
    bool hitHorizon = false;
    float minR = 100.0;
    float totalBend = 0.0;
    int noiseOctaves = uQuality > 0.5 ? 4 : 2;

    for (int i = 0; i < 48; i++) {
        if (i >= steps) break;
        float r = length(pos);
        minR = min(minR, r);

        if (r < rs * 1.01) {
            hitHorizon = true;
            break;
        }
        if (r > 7.5 && dot(pos, vel) > 0.0) {
            break;
        }

        // Passo adaptativo menor perto do anel de fótons e do plano do disco
        float dt = baseDt * clamp(r / (rs * 2.2), 0.30, 1.85);
        vec3 prevPos = pos;

        // Geodésica relativística de fóton: d2p/ds2 = -1.5 * rs * h^2 * p / r^5
        vec3 h = cross(pos, vel);
        float h2 = dot(h, h);
        vec3 accel = -1.5 * rs * h2 * pos / pow(max(r, 0.05), 5.0);
        vel = normalize(vel + accel * dt);
        totalBend += length(accel) * dt;
        pos += vel * dt;

        // Amostragem do disco de acreção fino ao redor de y = 0 (cruzamento ou proximidade)
        float distPlane = abs(pos.y);
        bool crossed = (prevPos.y * pos.y <= 0.0);
        if (crossed || distPlane < 0.085) {
            vec3 samplePos = pos;
            if (crossed && abs(pos.y - prevPos.y) > 0.0001) {
                float tPlane = -prevPos.y / (pos.y - prevPos.y);
                samplePos = mix(prevPos, pos, clamp(tPlane, 0.0, 1.0));
            }
            float rDisk = length(samplePos.xz);
            if (rDisk >= diskInner && rDisk <= diskOuter) {
                float radialT = (rDisk - diskInner) / (diskOuter - diskInner);
                float edgeFade = smoothstep(0.0, 0.10, radialT) * (1.0 - smoothstep(0.45, 1.0, radialT));

                // Rotação kepleriana lenta do gás
                float ang = atan(samplePos.z, samplePos.x);
                float omega = 0.34 / pow(max(rDisk, 0.4), 0.72);
                float spinAng = ang - uTime * omega;

                vec2 swirlUv = vec2(rDisk * 3.8, spinAng * 1.6 + rDisk * 1.9);
                float turb = fbm(swirlUv, noiseOctaves);
                float bands = 0.55 + 0.45 * sin(rDisk * 14.0 - uTime * 0.4 + turb * 3.8);
                float density = edgeFade * (0.45 + 0.55 * turb) * bands;

                // Brilho Doppler relativístico: lado que se aproxima (+vel) muito mais claro e quente
                vec3 diskVel = normalize(vec3(-samplePos.z, 0.0, samplePos.x));
                float vAlongView = dot(diskVel, -rd);
                float beta = 0.46 * sqrt(rs / max(rDisk, rs));
                float doppler = clamp(1.0 + vAlongView * beta * 1.45, 0.32, 1.95);
                float beaming = pow(doppler, 2.5);

                vec3 hotInner = vec3(1.18, 0.94, 0.74);
                vec3 midAmber = vec3(1.00, 0.58, 0.22);
                vec3 coolRed = vec3(0.55, 0.14, 0.07);
                vec3 baseColor = mix(hotInner, midAmber, smoothstep(0.0, 0.55, radialT));
                baseColor = mix(baseColor, coolRed, smoothstep(0.55, 1.0, radialT));
                // Desvio para o azul no lado que se aproxima, vermelho no que se afasta
                baseColor = mix(baseColor * vec3(1.08, 0.62, 0.42), baseColor * vec3(1.02, 1.08, 1.24), clamp((doppler - 0.65) * 0.85, 0.0, 1.0));

                float slabWeight = crossed ? 0.85 : exp(-abs(pos.y) * 28.0) * 0.28;
                float alpha = clamp(density * slabWeight * 1.35, 0.0, 0.85);
                accumDisk += transmittance * baseColor * beaming * alpha * 1.65;
                transmittance *= (1.0 - alpha * 0.72);
            }
        }
    }

    vec3 color = accumDisk;

    if (!hitHorizon) {
        vec3 bg = backgroundSpace(vel, totalBend);
        color += transmittance * bg;
    }

    // Anel de fótons fino contornando a sombra gravitacional
    float photonDist = abs(minR - rPhoton);
    float photonRing = exp(-photonDist * 30.0) * (hitHorizon ? 0.55 : 0.95);
    float innerEdge = exp(-abs(minR - rs * 1.05) * 18.0) * 0.18;
    // Assimetria sutil no anel de fótons também por efeito Doppler
    float ringDoppler = clamp(1.0 - screen.x * 0.45, 0.55, 1.45);
    color += vec3(1.15, 0.86, 0.56) * photonRing * ringDoppler * 0.82;
    color += vec3(0.85, 0.50, 0.25) * innerEdge;

    // Tone mapping cinematográfico + preservação de contraste para legibilidade do Hub
    color = color / (1.0 + color * 0.68);
    color = pow(max(color, vec3(0.0)), vec3(0.95));
    fragColor = vec4(color, 1.0);
}
"""

    const val UPSCALE_FRAGMENT_SHADER: String = """#version 300 es
precision mediump float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTexture;
void main() {
    fragColor = texture(uTexture, vUv);
}
"""
}
