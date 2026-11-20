# KENKERN — ROADMAP (PROMPT MESTRE v3)

Game engine 3D mobile-first, só Android nesta etapa. Kotlin + Compose (Material 3),
OpenGL ES 3.x até a Fase 12, Vulkan (NDK/JNI) na Fase 13 atrás de `RenderBackend`.
Escopo termina na Fase 13: PC, multiplayer, rede, serviços online e loja estão FORA.

Status: `FEITA` (TODOS os itens de aceite implementados + tag `fase-N-ok` publicada) ·
`EM CURSO` (fase aberta) · `PLANEJADA`.

| Fase | Nome | Entrega principal | Status |
|------|------|-------------------|--------|
| 1 | Fundação | Editor completo: viewport GLES3, grade infinita, céu, seleção/gizmo, câmera orbital/voo, undo/redo, Hub de projetos, .ck CK1, .KERN binário, autosave, crash diag + BootTrace, debug overlay | FEITA (`fase-1-ok`, app v0.1.12) — itens que dependem do aparelho estão **COMPILADO** (não VALIDADO) até o usuário confirmar: seleção por toque, Play real, paisagem automática, gizmos |
| 2 | Projeto & Assets | **Feito**: tela Diagnóstico (8 checagens, incl. `gltf`), testes JVM .ck/.KERN + 3 fixes de Fase 1 + fix crítico da rotação (quaternion), pastas `ui/`+`worlds/`, versão de fonte única, duplicar, .KERNPACK import/export SAF, **importador glTF/GLB → .KERN com meta de compressão (ImportReport) e teste de integridade (F2.7)**. **Pendente**: importador FBX (F2.8); .KIMG (ASTC/ETC2); cache; streaming progressivo anti-trave; navegador de arquivos do projeto; pré-visualização; prefabs; registro de assets com ids estáveis e detecção de referência quebrada | EM CURSO (tag `fase-2-ok` REMOVIDA — criada sem cumprir a fase; IDs F2.1–F2.15 no BACKLOG.md) |
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

## Histórico de renumeração (2026-11-21)

A ordenação antiga (3=LUA, 4=Importação, 5=.KIMG) foi corrigida por decisão do
usuário: LUA é a **Fase 5**; a Fase 3 é **LENTE, LUME, colliders e input**; o
importador glTF/GLB/FBX, .KIMG, cache, streaming, navegador, pré-visualização,
prefabs e registro de assets pertencem à **Fase 2**. Detalhes em DECISIONS.md #15.
