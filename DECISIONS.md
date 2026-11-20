# KENKERN — DECISIONS

Formato: decisão · motivo · alternativa descartada.

## Arquitetura

1. **Kotlin + Compose (M3) para o editor; GLSurfaceView via AndroidView com loop
   próprio de timestep fixo** — recomposição nunca roda o render. Alternativa:
   Compose GL Canvas (sem controle de timestep e de contexto EGL).
2. **GLES 3.x até a Fase 12; Vulkan na 13, atrás de `RenderBackend`** — troca de
   backend sem reescrever a engine. Alternativa descartada: Vulkan direto (risco
   alto antes do conteúdo existir).
3. **`.ck` texto (CK1) em vez de binário** — legível, versionável, tolerante a
   corrupção linha a linha (autorecuperação). Alternativa: binário compacto
   (ilegível para o usuário; adiado até medir).
4. **`.KERN` binário com quantização (posição u16, normal octaédrica 8:8,
   índices u16/u32, LODs, clusters)** — meta de 100 MB → ≤ 20 MB. Alternativa:
   float cru (5× maior).
5. **Projetos no armazenamento interno + estrutura de pastas por projeto;
   SAF opcional (F2.6)** — app roda sem permissões; SAF para quem quiser pasta
   própria. Alternativa: só SAF (fricção alta para o dia a dia).
6. **LUA com LuaJ atrás de `ScriptRuntime`** (Fase 3) — sem NDK; troca por Lua
   5.4 nativo só se o PROFILER provar gargalo (medição ficará registrada aqui).
7. **LuaJ como dependência** — Java puro, roda na ART sem NDK. Alternativa:
   lua-native via JNI desde o início (contra a decisão 6).

## Processo

8. **Plano de fases 2–13 derivado do Prompt Mestre v3** (o texto recebido traz o
   vocabulário e as regras; a ordenação das fases é decisão do engenheiro-chefe):
   persistência/diagnóstico → script → importação → texturas → luz/céu → natureza
   → animação → física/zona → virtualização/IG → UI/diálogos/IA → orçamento →
   Vulkan. Motivo: cada fase depende só das anteriores e entrega um APK testável.
9. **CI como gate de qualidade**: `testReleaseUnitTest` + `assembleRelease`
   assinado a cada push; teste JVM vermelho bloqueia o APK.
10. **KAI fica BLOQUEADO até o usuário fornecer a própria chave de API** — a
    chave mora no Android Keystore, nunca no repo (regra de segurança do projeto).
11. **FBX adiado para depois do glTF/GLB** — glTF é o formato de intercâmbio
    moderno e cobre a meta de compressão; FBX é binário fechado e proprietário.

12. **.KERNPACK como formato portátil (F2.6), projetos moram no armazenamento
    do app** — autosave de 1,5 s não pode depender de ContentResolver por
    arquivo (lentidão/perda em provedores instáveis). Alternativa descartada:
    projeto aberto direto da árvore SAF. Import/export por intent = zero
    permissões de armazenamento.
13. **Checagem "shaders" do Diagnóstico usa shader de teste próprio** em vez
    de GlesBackend.initialize() — initialize() marca o BootTrace
    ("shader compilado") e poluiria o rastreio de crash de uma sessão real.
    Os shaders REAIS da engine são validados ao abrir o editor.
14. **.KERN v1 existente não precisa de migração** — nenhum arquivo válido foi
    jamais gravado (o escritor estourava sempre, LOD ≥1 obrigatório): não
    existem .KERN legados no mundo.

## Realinhamento ao roadmap (2026-11-21 — decisão do usuário)

15. **Ordenação das fases corrigida: LUA = Fase 5 (não 3); Fase 3 = LENTE, LUME,
    colliders e input** — o importador glTF/GLB/FBX, .KIMG (ASTC/ETC2), cache,
    streaming progressivo anti-trave, navegador de arquivos, pré-visualização,
    prefabs e registro de assets são pendências da **Fase 2** (Projeto & Assets).
    O LuaJ existente (antes F3.1) vira **F5.1, trabalho antecipado CONGELADO** —
    proibido crescer código Lua antes da Fase 5. BACKLOG.md renumerado 1:1 com o
    ROADMAP.md. Alternativa descartada: manter a ordenação antiga (contrariava o
    roadmap do Prompt Mestre v3).
16. **Tag `fase-N-ok` exige TODOS os itens de aceite da fase implementados**
    (CI verde) — itens não feitos ficam pendentes e a fase continua ABERTA, sem
    tag. Motivo: a tag `fase-2-ok` foi criada sem cumprir a Fase 2 e foi removida
    (a Release v0.1.13 foi re-titulada "fase ABERTA"; o APK fica, pois o build
    existe). Alternativa descartada: tag parcial + itens pendentes.
17. **Nenhum `runCatching` engole erro em silêncio** — todo `runCatching` em
    caminho de render, formato ou carregamento registra o erro em 3 destinos:
    Log (logcat) + Console (`KenkernLog`, anel em memória) + tela Diagnóstico.
    Motivo: a Fase 1 quase perdeu o torus .KERN para um `runCatching` silencioso
    (LOD_ENTRY_SIZE) — erro invisível = bug invisível. Varredura inicial (62
    ocorrências; 32 silenciosas nas classes críticas): docs/RUNCATCHING_AUDIT.md.
18. **Console de erros = `KenkernLog` (objeto, anel de 200 entradas) exibido na
    tela Diagnóstico e no overlay do editor** — sem dependência nova; JVM-safe
    (log de Android pode ser desligado nos testes). Alternativas descartadas:
    Timber (dependência externa), só logcat (não aparece no aparelho do usuário).
19. **Importador glTF/GLB antes do FBX** (reafirma #11): GLB é o formato de
    intercâmbio moderno, binário aberto e documentado; FBX é proprietário e fica
    para F2.8. Parser JSON do glTF usa `org.json` (nativo no Android;
    `testImplementation org.json:json` só nos testes JVM) — sem dependência nova
    no APK. Alternativa descartada: parser JSON artesanal (risco de bug).
20. **Convenção de UV normalizada para a glTF (origem TOPO-esquerda) e FBX só
    binário (F2.8)** — (a) o importador FBX converte v' = 1 − v na entrada, para
    que TODOS os importadores entreguem UV na mesma convenção ao .KERN (texturas
    da TRAMA/Fase 4 não terão um "modo FBX"); (b) FBX ASCII (texto) e FBX 6.x/5.x
    dão ERRO CLARO pedindo binário 7.x — só o caminho binário 7.1–7.7 é
    implementado (é o que Blender/Maya/Assimp exportam por padrão);
    (c) PreRotation/PostRotation presentes = AVISO no `KenkernLog` e ignorados
    (malha estática não os usa; rigs/animações são da Fase 8). Alternativa
    descartada: manter a convenção nativa de cada formato (misturaria convenções
    no mesmo formato .KERN).

21. **ETC2 em Kotlin puro é o encoder do .KIMG (F2.9); ASTC reservado no formato; encoder ASTC fora do escopo** — (a) ETC2 é OBRIGATÓRIO no OpenGL ES 3.0, então roda em TODO aparelho-alvo; ASTC fica no cabeçalho (ETC2_ASTC_4x4/6x6/8x8 reservados) e a checagem `astc` do Diagnóstico só informa a extensão GL_KHR_texture_compression_astc_ldr (nunca reprova); (b) o encoder implementa os 5 modos da spec (individual, diferencial, T, H, planar) com os stuffs EXATOS do ETCPACK (Ericsson) — incluindo o padding de overflow que força o hardware a escolher o modo certo; (c) qualidade MEDIDA (PSNR, ida e volta encoder→decoder, mip 0): cor sólida 52.9 dB · gradiente 64×64 38.5 dB · senoides 128×128 (canais independentes) 33.5 dB · alfa EAC 52.9 dB — razão de compressão 5.8× (RGB) e 2.9× (RGBA); (d) fallback AUTOMÁTICO: driver recusou ETC2 → decodifica na CPU (Etc2 decoder) e sobe RGBA8 — a textura nunca some; (e) bugs achados e corrigidos no caminho: ordem dos blocos é LINHA-MAJOR (hardware), quantização com arredondamento (truncar colapsava médias baixas), planar quantiza o código 6/7/6 (clampar quebrava gradientes). Alternativa descartada: ASTC encoder em Kotlin (fora do escopo desta etapa; segue para fase futura com a extensão detectada).

## Correções históricas (resumo; detalhes no CHANGELOG)

- v0.1.12: yaw do voo lia `yawDegrees` do orbital em `computeBasis` (causa (b)).
- v0.1.11: zoom preso = dedo morto reancorado no `ACTION_POINTER_UP`.
- v0.1.8: NPE de inicialização do TransformGizmo (ordem de propriedades Kotlin).
