# KENKERN — ROADMAP (PROMPT MESTRE v3 + ADENDO v4)


> **Antes de agir:** leia [HARNESS.md](HARNESS.md) — papéis, linguagem, definição de PRONTO e portões de CI.
Game engine 3D mobile-first, só Android nesta etapa. Kotlin + Compose (Material 3),
OpenGL ES 3.x até a Fase 12, Vulkan (NDK/JNI) na Fase 13 atrás de `RenderBackend`.
Escopo termina na Fase 13: PC, multiplayer, rede, serviços online e loja estão FORA.

**Adendo v4 (2026-11-22)**: NICKS (catálogo vivo em `NICKS.md`, DECISIONS
#24–#31), IDIOMAS en/pt (recursos Android), UI (árvore 2D própria do TELAR,
abas/workspaces) e MUNDO. Sem Unity/Godot/Unreal nos nomes.

Status: `FEITA` (TODOS os itens de aceite implementados + tag `fase-N-ok` publicada) ·
`EM CURSO` (fase aberta) · `PLANEJADA`.

| Fase | Nome | Entrega principal | Status |
|------|------|-------------------|--------|
| 1 | Fundação | Editor completo: viewport GLES3, grade infinita, céu, seleção/gizmo, câmera orbital/voo, undo/redo, Hub de projetos, .ck CK1, .KERN binário, autosave, crash diag + BootTrace, debug overlay | FEITA (`fase-1-ok`, app v0.1.12) — itens que dependem do aparelho estão **COMPILADO** (não VALIDADO) até o usuário confirmar: seleção por toque, Play real, paisagem automática, gizmos |
| 2 | Projeto & Assets | **Feito**: tela Diagnóstico (agora 14 checagens, incl. `gltf`, `fbx`, `importar`, `kimg`, `etc2`, `astc`, `release`), testes JVM .ck/.KERN + fixes de Fase 1 + fix da rotação (quaternion), pastas `ui/`+`worlds/`, versão de fonte única, duplicar, .KERNPACK import/export SAF, importador glTF/GLB → .KERN (F2.7), importador FBX binário+ASCII → .KERN com materiais/dedup/LODs (F2.8), **botão Importar modelo (F2.16: SAF arquivo/pasta, background com etapas e cancelar, UM Command undo/redo, ImportReport)**, **.KIMG ETC2 com mipmaps + cache por hash + GPU com fallback RGBA8 (F2.9)**, **camada de compressão em disco do .KIMG (Deflate, DECISIONS #22)**. **Pendente**: streaming progressivo anti-trave (F2.11); navegador de arquivos do projeto (F2.12); pré-visualização de assets (F2.13); prefabs (F2.14); registro de assets com ids estáveis e detecção de referência quebrada (F2.15) | EM CURSO (tag `fase-2-ok` REMOVIDA — criada sem cumprir a fase; a auditoria v0.1.15 CONFIRMOU que a fase NÃO está fechada; IDs F2.1–F2.17 no BACKLOG.md) |
| 3 | LENTE + LUME + colisão + input | Câmeras de jogo como Nicks (LENTA), luzes como Nicks (LUME: direcional/ponto/spot, sombras), colisão física (AABB/esfera/raio), input de jogo | PLANEJADA |
| 4 | TRAMA | Materiais (albedo/normal/rough/metal), pintura básica | PLANEJADA |
| 5 | LUA | `ScriptRuntime` + LuaJ atrás de interface, hooks de cena (start/update/eventos), editor de script, `LUA_API.md` oficial, scripts de exemplo no Diagnóstico | PLANEJADA — F5.1 (LuaJ/Sandbox) foi ANTECIPADO e está congelado: proibido crescer código Lua antes da Fase 5 |
| 6 | CÉU | Dia/noite, nuvens/estrelas/clima | PLANEJADA |
| 7 | Natureza | TERRA (terreno), VERDE (vegetação instanciada), MARÉ (água), VENTO | PLANEJADA |
| 8 | OSSATURA + LINHA | Skeleton, rig, skinning, timeline de animação, importação de animações glTF | PLANEJADA |
| 9 | ZONA | Campos/áreas de detecção | PLANEJADA |
| 10 | KENA + KELU | Geometria virtualizada (GPU-driven/compute), iluminação global | PLANEJADA |
| 11 | TELAR + FALA + MENTE | Editor de UI estilo Canva, diálogos, IA de NPCs. KAI fica BLOQUEADO até o usuário fornecer chave de API própria | PLANEJADA |
| 12 | Orçamento mobile + MUNDOS + RODANTE | Classes de aparelho, controle térmico, resolução dinâmica, multi-mundos, veículos, botão de exportação via GitHub Actions | PLANEJADA |
| 13 | Vulkan | `RenderBackend` Vulkan via NDK/JNI com paridade GLES e fallback automático. Fim do escopo: relatório final | PLANEJADA |

## Regras de fase

- **Tag `fase-N-ok` só fecha a fase com TODOS os itens de aceite implementados**
  (CI verde). Itens não feitos ficam pendentes no BACKLOG e a fase continua ABERTA —
  sem tag parcial.
- Fim de fase: todos os itens implementados → CI verde → tag `fase-N-ok` → APK como
  pré-lançamento no GitHub Releases (texto do CHANGELOG) → teste de aceite (5–10
  passos) em `TESTES_PENDENTES.md` → o usuário valida no aparelho → itens passam de
  `COMPILADO` para `VALIDADO-NO-APARELHO`.
- **COMPILADO ≠ VALIDADO**: item que só passou no CI/JVM é `COMPILADO`; só vira
  `VALIDADO-NO-APARELHO` após confirmação do usuário no aparelho.
- **Nenhum `runCatching` engole erro em silêncio**: todo `runCatching` em caminho de
  render, formato ou carregamento registra o erro (Log + Console `KenkernLog` +
  tela Diagnóstico). Auditoria viva: `docs/RUNCATCHING_AUDIT.md`.
- Toda fase adiciona checagens à tela Diagnóstico.
- Versão PC, multiplayer, rede, serviços online e loja: NÃO planejar, NÃO codificar.

## Ordem de execução (adendo v4 — atualizada 2026-11-22)

**Prioridade acordada depois do teste da resolução de texturas:** antecipar o
primeiro recorte jogável do M1 (jogador, surgimento, colisão e câmera). O PBR
completo de T0.4 e o Hub de T0.5 continuam no backlog.

0. **v0.1.17** — FEITA (Release `v0.1.17-fase2-teste`: bugs A/B do aparelho +
   núcleo nativo C++ com etcpak e meshoptimizer; aguardando aceite no aparelho).
1. **BLOCO 0 (produto)** — uma tarefa por commit, CI verde, paradas para o
   usuário testar: T0.1 idiomas en/pt · T0.2 NickKindRegistry + CK2 + migração
   CK1 + NICKS.md · T0.3 janela Adicionar Nick + cabeçalho de tipo no Inspetor
   + seletor de forma do collider (**PARADA 1**) · T0.4 fidelidade de importação
   (PBR completo glTF/FBX, textura "Original" até o limite do aparelho +
   presets 512/1024/2048) · T0.5 Hub novo (fundo 2.5D com paralaxe, buraco
   negro com disco de acreção, wordmark + versão, recentes, seletor de idioma,
   Diagnóstico, atalhos Modelar/Animar/Texturizar com projeto "Sandbox";
   ≤ 30 FPS, pausa quando invisível, respeita economia de bateria e "reduzir
   animações") (**PARADA 2**).
2. **MARCO JOGÁVEL (M1)** — M1.1–M1.8 com os Nicks reais (Player Body,
   Collider Body, Area, Spawn Point, Follow Camera); M1.3 inclui o Joystick
   Nick + campo "Controla" + botões de ação + API Lua `input.joystick` + modo
   "Seguir Nick" (DECISIONS #31; detalhar os M1.x na abertura do marco).
3. **DEPOIS, nesta ordem**: resto da Fase 2 (streaming F2.11, navegador F2.12,
   pré-visualização F2.13, prefabs F2.14, registro de assets F2.15) · Fase 3
   completa (LENTE, LUME, colliders editáveis) · animação, ossatura e animação
   por partes · Lua completo, LINKERF, editor de código · TELAR, vídeo e
   Diálogos (FALA) · Mundo (terreno e pincel 3D, vegetação, ambiente, clima,
   água, vento, partículas) · FORJA, TRAMA e materiais · áudio e música ·
   KENA · KELU · Vulkan e exportação.

Cada parada tem relatório de no máximo 15 linhas e o passo a passo em
TESTES_PENDENTES.md. Nenhum item vira VALIDADO sem teste do usuário no aparelho.

## Histórico de renumeração (2026-11-21)

A ordenação antiga (3=LUA, 4=Importação, 5=.KIMG) foi corrigida por decisão do
usuário: LUA é a **Fase 5**; a Fase 3 é **LENTE, LUME, colliders e input**; o
importador glTF/GLB/FBX, .KIMG, cache, streaming, navegador, pré-visualização,
prefabs e registro de assets pertencem à **Fase 2**. Detalhes em DECISIONS.md #15.
