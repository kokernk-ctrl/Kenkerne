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

## Correções históricas (resumo; detalhes no CHANGELOG)

- v0.1.12: yaw do voo lia `yawDegrees` do orbital em `computeBasis` (causa (b)).
- v0.1.11: zoom preso = dedo morto reancorado no `ACTION_POINTER_UP`.
- v0.1.8: NPE de inicialização do TransformGizmo (ordem de propriedades Kotlin).
