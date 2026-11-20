# KENKERN — ROADMAP (PROMPT MESTRE v3)

Game engine 3D mobile-first, só Android nesta etapa. Kotlin + Compose (Material 3),
OpenGL ES 3.x até a Fase 12, Vulkan (NDK/JNI) na Fase 13 atrás de `RenderBackend`.
Escopo termina na Fase 13: PC, multiplayer, rede, serviços online e loja estão FORA.

Status: `FEITA` (tag `fase-N-ok` publicada) · `EM CURSO` · `PLANEJADA`.

| Fase | Nome | Entrega principal | Status |
|------|------|-------------------|--------|
| 1 | Fundação | Editor completo: viewport GLES3, grade infinita, céu, seleção/gizmo, câmera orbital/voo, undo/redo, Hub de projetos, .ck CK1, .KERN binário, autosave, crash diag + BootTrace, debug overlay | FEITA (`v0.1.0-fase1` … `fase-1-ok`, app v0.1.12) |
| 2 | Projeto & Diagnóstico | Tela Diagnóstico no Hub (APROVADO/FALHOU), testes JVM de serialização (.ck/.KERN), pastas `ui/`+`worlds/`, versão de engine de fonte única, duplicar projeto, SAF (pasta escolhida pelo usuário) | EM CURSO |
| 3 | LUA | `ScriptRuntime` + LuaJ atrás de interface, hooks de cena (start/update/eventos), editor de script, `LUA_API.md` oficial, scripts de exemplo no Diagnóstico | PLANEJADA |
| 4 | Importação .KERN | Importador glTF/GLB → .KERN (quantização, deduplicação, compressão de índices, LODs), teste de integridade (AABB, triângulos, erro máximo), meta 100 MB → ≤ 20 MB | PLANEJADA |
| 5 | .KIMG + TRAMA | Texturas ASTC/ETC2 com mipmaps, materiais (albedo/normal/rough/metal), cache em disco, pintura básica | PLANEJADA |
| 6 | LUME + CÉU | Luzes como Nicks (direcional/ponto/spot), sombras, dia/noite, nuvens/estrelas/clima | PLANEJADA |
| 7 | Natureza | TERRA (terreno), VERDE (vegetação instanciada), MARÉ (água), VENTO | PLANEJADA |
| 8 | OSSATURA + LINHA | Skeleton, rig, skinning, timeline de animação, importação de animações glTF | PLANEJADA |
| 9 | ZONA + LENTE + colisão | Campos/áreas de detecção, câmeras de jogo como Nicks, colisão física (AABB/esfera/raio) | PLANEJADA |
| 10 | KENA + KELU | Geometria virtualizada (GPU-driven/compute), iluminação global | PLANEJADA |
| 11 | TELAR + FALA + MENTE | Editor de UI estilo Canva, diálogos, IA de NPCs. KAI fica BLOQUEADO até o usuário fornecer chave de API própria | PLANEJADA |
| 12 | Orçamento mobile + MUNDOS + RODANTE | Classes de aparelho, controle térmico, resolução dinâmica, streaming LRU/anti-trave, multi-mundos, veículos, botão de exportação via GitHub Actions | PLANEJADA |
| 13 | Vulkan | `RenderBackend` Vulkan via NDK/JNI com paridade GLES e fallback automático. Fim do escopo: relatório final | PLANEJADA |

## Regras de fase

- Fim de fase: testes do CI verdes → tag `fase-N-ok` → APK como pré-lançamento no
  GitHub Releases (texto do CHANGELOG) → teste de aceite (5–10 passos) em
  `TESTES_PENDENTES.md` → próxima fase começa imediatamente.
- Toda fase adiciona checagens à tela Diagnóstico.
- Versão PC, multiplayer, rede, serviços online e loja: NÃO planejar, NÃO codificar.
