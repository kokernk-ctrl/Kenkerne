# Auditoria de baseline — roadmap Kenkern rumo à 1.0 estável

**Snapshot auditado:** 23/11/2026; HEAD `1ac8474` (`feat: adiciona Nicks jogáveis ao primeiro recorte do M1`), versão Android ainda `0.1.19-fase2` / `versionCode 20`.
**Limite:** leitura dos documentos de planejamento, fonte, testes, CI, dependências, scripts, exemplos/fixtures, licenças e manifesto. Nenhum arquivo rastreado do repositório foi alterado. O relatório fica em `/tmp/ken-audit-roadmap.md`. Não consultei nem imprimi valores de credenciais.

## 1. Parecer executivo

**Não é baseline de 1.0 estável ainda.** A Fase 2 continua aberta, há cinco tarefas explicitamente `TODO` (F2.11–F2.15), há uma lacuna de escopo na tarefa F2.10 (cache endereçado por conteúdo existe, mas LRU/evicção ainda não), T0.4 e T0.5 permanecem incompletas, e as Fases 3–13 seguem planejadas. O HEAD acrescenta uma primeira fatia jogável do M1, mas o M1.1–M1.8 completo ainda não foi detalhado nem aceito; seu roteiro manual está pendente no aparelho. A versão corrente é pré-1.0.

Há bastante fundação demonstrada por código/testes: app Android Kotlin/Compose, GLES 3, editor/projetos, formatos CK2/KERN v2/KIMG, importação glTF/FBX, processamento ETC2 e núcleo C++ nativo, undo/redo, diagnóstico e fluxo de importação. Porém, **preset/editor “IMPLEMENTADO” não equivale à função de jogo implementada** (regra explicitada em `NICKS.md`); não tratar nomes de tipos como prova de física, IA, luz ou input.

A política escrita continua sendo a adequada: não criar tag de fase com tarefas pendentes, não converter `COMPILADO` em `VALIDADO-NO-APARELHO` sem teste do usuário e não cortar requisitos para antecipar a 1.0. O principal trabalho imediato de baseline é sincronizar a ordem/status dos documentos e formalizar M1.1–M1.8, não reduzir o roadmap.

## 2. Baseline verificável

- `ROADMAP.md`, `BACKLOG.md`, `CHANGELOG.md`, `DECISIONS.md`, `PROGRESS.md`, `NICKS.md`, `TESTES_PENDENTES.md`, `ARCHITECTURE.md`, `REGRESSION.md`, `LICENSES.md`, `LUA_API.md`, `docs/FASE1.md`, `docs/KERN_SPEC.md` e `docs/RUNCATCHING_AUDIT.md` foram lidos; também foram inspecionados workflow, Gradle/version catalog, scripts, manifesto, fontes de diagnóstico e M1, testes, CMake e arquivos vendorizados/licenças.
- Há **21 checagens declaradas** no Diagnóstico atual: núcleo nativo, ETC2 nativo, malha nativa, CK, KERN, glTF, FBX, importar, KIMG, ETC2/GPU, textura na cena, cena=viewport, ASTC informativo, picking, toque, shaders, buffers, capacidades GL, memória, release minificado e layout. **Não existe ainda** a checagem `joystick` planejada para M1.3.
- `bash scripts/check_i18n.sh`: **OK** (sem literais UI detectados; chaves en/pt em paridade). `bash -n` dos dois scripts: OK.
- A fonte contém 246 anotações `@Test` neste snapshot. A primeira tentativa local não achou SDK por falta de `ANDROID_HOME`; com SDK explícito, o Java 21 instalado neste VM não fornece `javac`. Usando JDK 17 + SDK Android 35 + NDK/CMake fixados, a execução forçada `testReleaseUnitTest --rerun-tasks` terminou **BUILD SUCCESSFUL** em 1m26s (26 tasks executadas; host C++ compilado). O resultado confirma a suíte verde neste ambiente, mas não valida o app no aparelho nem substitui os gates de CI/release.
- `TESTES_PENDENTES.md` é explicitamente teste manual no aparelho. A documentação relata G1–G3 da v0.1.19 validados pelo usuário e E1–E6 como corrigidos/aceitos; o roteiro novo do M1 ainda não tem aceite registrado. Validação de aparelho não é inferida de JVM/CI.
- O diretório de trabalho estava limpo antes da auditoria; os comandos de build só podem atualizar artefatos ignorados. Confirmar `git status --short` limpo após a execução forçada.

## 3. Mapa completo do roadmap e pendências

| Fase / bloco | Escopo original | Estado provado / restante / gate |
|---|---|---|
| **Fase 1 — Fundação** | Editor GLES3, grade/céu, seleção e gizmos, câmera orbital/voo, undo/redo, Hub, CK1 (agora migrado), KERN, autosave, Play e diagnóstico de crash. | `BACKLOG.md` marca F1.1–F1.5 e F1.10–F1.11 feitas; F1.6 seleção, F1.7 gizmo, F1.8 Play e F1.9 paisagem continuam **COMPILADO**, aguardando aparelho. Tag `fase-1-ok` mantida, com ressalva explícita de validação parcial. Aceite detalhado em `TESTES_PENDENTES.md`. |
| **Fase 2 — Projeto & Assets** | Diagnóstico, migrações/projetos, importadores, KIMG/ETC2, cache, streaming, browser, preview, prefab e registro de asset. | **EM CURSO/ABERTA**; `fase-2-ok` removida e não deve voltar até escopo completo + aceite do usuário. Mapeamento de IDs abaixo. |
| **Bloco 0 — Produto** | Idiomas, catálogo de Nicks/CK2, Adicionar Nick, importação PBR, Hub novo. | T0.1–T0.3 implementados/CI; v0.1.19 G1–G3 reportados como validados no aparelho. T0.4 está **parcial** (resolução de textura aprovada; fidelidade PBR pendente). T0.5 TODO. |
| **M1 — Marco Jogável** | Física estática/rígida/cinemática, formas de colisão, áreas/sinais, spawn, Follow Camera, Player, Enemy/NPC e jogo ponta a ponta; M1.3 também exige controle configurável e input/API. | Um primeiro recorte foi adicionado no HEAD. Ainda não há detalhamento dos M1.1–M1.8 ou status por ID em `BACKLOG.md`; teste de aparelho no topo de `TESTES_PENDENTES.md` está pendente. Não declarar M1 fechado. |
| **Fase 3 — LENTE + LUME + colisão + input** | Câmeras de jogo, luzes direcionais/ponto/spot e sombras, colisão AABB/esfera/raio e input. | PLANEJADA; IDs F3.x devem ser detalhados ao abrir a fase. Nicks de luz atualmente são auxiliares editoriais, não iluminam a cena. M1 não substitui a fase completa. |
| **Fase 4 — TRAMA** | Materiais albedo/normal/rough/metal e pintura básica. | PLANEJADA. O PBR completo de T0.4 é uma entrega anterior e dependência explícita do F2.17; não confundir os dois escopos. |
| **Fase 5 — LUA** | `ScriptRuntime`/LuaJ, API, hooks, script por Nick/Play, editor, exemplos e diagnóstico/API congelada. | F5.1 foi antecipada, CI compilado e **congelada**; não crescer Lua antes da Fase 5. F5.2–F5.5 `TODO`. `LUA_API.md` é rascunho e ainda descreve “Fase 3”, divergência documental. |
| **Fase 6 — CÉU** | Dia/noite, nuvens, estrelas e clima. | PLANEJADA; detalhar IDs e aceite na abertura. |
| **Fase 7 — Natureza** | TERRA, VERDE, MARÉ e VENTO. | PLANEJADA; detalhar IDs e aceite na abertura. |
| **Fase 8 — OSSATURA + LINHA** | Skeleton/rig/skinning, timeline e importação de animações glTF. | PLANEJADA; detalhar IDs e aceite na abertura. Importadores hoje avisam quando esqueleto/animação são ignorados; isso não é skinning. |
| **Fase 9 — ZONA** | Campos/áreas de detecção. | PLANEJADA; não confundir a sobreposição AABB limitada do recorte M1 com a fase completa. |
| **Fase 10 — KENA + KELU** | Geometria virtualizada GPU-driven/compute e iluminação global. | PLANEJADA; meshoptimizer vendorizado para remap/cache/LOD **não** implementa KENA; sol/ambiente fixos **não** implementam KELU. |
| **Fase 11 — TELAR + FALA + MENTE** | Editor de UI, diálogos e IA de NPC. | PLANEJADA. KAI/serviço de IA fica bloqueado até chave fornecida pelo usuário; manter segredo no Android Keystore, não em repo/CI. |
| **Fase 12 — Orçamento mobile + MUNDOS + RODANTE** | Perfis de aparelhos, térmico, resolução dinâmica, multi-mundos, veículos e exportação via GitHub Actions. | PLANEJADA; dependência para definir/validar a matriz de aparelhos e orçamento de release. |
| **Fase 13 — Vulkan** | Backend Vulkan NDK/JNI atrás de `RenderBackend`, paridade com GLES, fallback e relatório final. | PLANEJADA; encerra o escopo da 1.0. Interface `RenderBackend`/GLES existente não prova backend Vulkan. |

Escopo excluído por decisão: PC, multiplayer, rede, serviços online e loja. Não adicionar nem reclassificar isso como requisito da 1.0 sem nova decisão do usuário.

### Fase 2 — IDs e aceite exato

| ID | Situação no BACKLOG / evidência | Falta / dependência |
|---|---|---|
| F2.1–F2.2 | Testes roundtrip CK/KERN, fixes de escape e layout/LOD; COMPILADO+CI. | Aceite Diagnóstico/aparelho pendente conforme checklist. |
| F2.3 | Diagnóstico do Hub; hoje cresceu para 21 checks. | Aceite de aparelho por versão; manter `COMPILADO` distinto de `VALIDADO`. |
| F2.4–F2.6 | Pastas/versionamento, duplicação, KERNPACK SAF/zip-slip; COMPILADO+CI. | Aceites manuais ainda listados. |
| F2.7 | glTF/GLB → KERN; parser, transformações, integridade e testes. | Limitações de extensões e rig/animação devem permanecer avisadas; validar importação real no aparelho. |
| F2.8 | FBX binário 7.1–7.7 → KERN. | FBX ASCII passou a ser tratado em F2.17; ainda há limites de versão/transformações e aviso de conteúdo externo. |
| F2.9 | KIMG, ETC2 e mipmaps, cache/hash, GPU/fallback; camada Deflate em disco. | ASTC é **só detecção**; encoder ASTC explicitamente inexistente. ETC2 é o formato base GLES 3.0. A meta de compressão de malha/origem 5× não foi atingida: medição reportada 0,62×–4,25×. |
| F2.10 | Marcado COMPILADO: cache por conteúdo/hash e variante. | O nome original exige **LRU**; o backlog admite que evicção/LRU ficou para fechamento. Implementar e testar ou pedir decisão explícita; não considerar requisito satisfeito por hash apenas. |
| **F2.11** | **TODO** streaming progressivo fora do frame/sem bloquear GL. | A fila AssetMeshLibrary com orçamento de 6 ms é mitigação parcial, não o streaming completo. Medir frame-time, fila, cancelamento, retomada e memória. Depende de F2.10. |
| **F2.12** | **TODO** browser de arquivos de projeto no editor. | Importar via SAF (F2.16) não substitui navegador interno. Depende da organização de assets/F2.7. |
| **F2.13** | **TODO** preview de malha/textura. | Depende do browser F2.12; definir comportamento de seleção e assets corrompidos. |
| **F2.14** | **TODO** criar/inserir/instanciar prefabs. | Depende do browser F2.12; variantes aparecem como lacuna posterior e não devem ser confundidas com prefab mínimo nem silenciosamente excluídas. |
| **F2.15** | **TODO** registro de assets com IDs estáveis e referência quebrada. | `ReferenceId` de Nick e `ReferenceSystem` existentes não provam registry persistente de assets nem diagnóstico de referências quebradas. Depende de F2.7. |
| F2.16 | Importar modelo por SAF/arquivo/pasta, background, progresso/cancelar, undo/redo, relatório, asset render/AABB; COMPILADO+CI. | Teste manual de importação real, cancelar e undo/redo no aparelho. |
| F2.17 | FBX ASCII, materiais/submalhas KERN v2, dedup/LODs, descarte de canais sem uso e regressões quaternion; COMPILADO+CI. | **PBR completo é T0.4, ainda pendente**; testes em fixtures não encerram fidelidade em assets reais/aparelho. |

Fase 2 só fecha após F2.1–F2.15 completos (incluindo LRU e streaming), CI verde, testes do usuário e “aprovado”, conforme ROADMAP. F2.16/F2.17 são entregas de fechamento adicionadas ao backlog e não anulam os itens anteriores.

### Bloco 0 e sequência de produto

- **T0.1** idiomas en/pt: implementado; seletor no Hub. Configurações é lacuna futura documentada.
- **T0.2** registry/CK2/migração: implementado. Registry atual tem 18 tipos segundo NICKS (3 base, 3 luz, 2 câmera, 7 corpos, 3 áreas); BACKLOG ainda diz 17 e não contabiliza `camera.follow` adicionado ao M1.
- **T0.3** Adicionar Nick, Inspetor, classes e collider: implementado/CI; aceite E1–E6 da v0.1.18 relatado como feito, G1–G3 da v0.1.19 reportados validados.
- **T0.4** PBR completo glTF/FBX (base color, metal/rough, normal, occlusion, emissive, alpha, double-sided) e presets/original: **não completo**. A redução por resolução Original/512/1024/2048 e cache por perfil está implementada e o teste 512×Original foi aprovado conforme PROGRESS/TESTES.
- **T0.5** Hub 2.5D, acessibilidade a recentes/Diagnóstico/idioma, atalhos workspaces e Sandbox, limite de animação/energia: **TODO**. Manter critérios de bateria, pausa invisível e “reduzir animações”.

**M1 existente no HEAD:** `NickPlayRuntime` clona/usa cena descartável de Play, escolhe primeiro player e spawn ativos, gravidade, movimento em X/Z normalizado, colisão por varredura de eixos com `body.static` e `body.collider` apenas em caixa, consulta AABB `isPlayerInside`, camera básica fixa/follow e joystick de teste na tela; Stop descarta cópia e mantém cena/câmera do editor. Há testes JVM para movimento/colisão/spawn/área/câmera.

**Limites contra o escopo M1/M1.3 aprovado:** sem pulo/rampas; sem física de rigid/kinematic; sem sphere/capsule/cylinder/convex/mesh collision no runtime; sem eventos de área/sinais; sem múltiplos jogadores, Enemy/NPC, combate ou jogo de ponta a ponta; joystick é overlay fixo de teste, não `ui.joystick` editável, não tem “Controla”, modos fixo/flutuante/dinâmico, botões de ação, ações nomeadas, multitoque por controle, modo Seguir Nick, ponte `input.joystick()` nem checagem Diagnóstico `joystick`. O catálogo registrar `body.rigid`, `body.enemy`, `body.npc` etc. é somente preset de editor.

## 4. Plataforma, build, bibliotecas, licenças, exemplos e manifesto

- **Produto/plataforma:** Android-only; Kotlin + Compose Material 3, GLSurfaceView e OpenGL ES 3.x até Fase 12; Vulkan NDK/JNI só na 13. `minSdk 26`, `compileSdk/targetSdk 35`; `sensorLandscape`; ABI nativa somente `arm64-v8a` por decisão; C++17. Isso não cobre ainda a matriz de aparelhos/limites térmicos da Fase 12.
- **Build:** Gradle wrapper 8.10.2; AGP 8.7.3; Kotlin 2.0.21; Java 17 alvo; Compose BOM 2024.10.01; NDK 27.3.13750724 + CMake 3.22.1 fixados no app e workflow. Release tem R8 e shrink de recursos; há `-keep` para engine e LuaJ por reflexão; a assinatura cai para debug se configuração de release não estiver disponível.
- **Dependências diretas catalogadas:** AndroidX core/lifecycle/activity Compose, Compose UI/Material3/icons, coroutines Android, LuaJ 3.0.1; testes JUnit 4.13.2, Mockito 5.14.2 e `org.json` 20240303 só em testes. `app/build.gradle.kts` compila também a mesma fonte C++ para host via g++ para comparar ETC2/mesh nativos com Kotlin nos testes JVM.
- **Código fonte de terceiros vendorizado:** etcpak commit `3d716e…` (BSD-3-Clause) para ETC2 e meshoptimizer `8547fc…` (MIT) para remap/cache/LOD; fontes e avisos correspondentes em `third_party/` e `LICENSES.md`. O inventário não é um SBOM/aviso completo das dependências transitivas Gradle; gerar e revisar notices/SBOM antes da distribuição 1.0, sem presumir violação.
- **Scripts:** `scripts/check_i18n.sh` verifica literais UI e paridade de chaves en/pt; `scripts/check_jni_symbols.sh` confronta cada `external fun` com símbolos definidos na `.so` do APK. Ambos têm checks shell; i18n passou nesta auditoria.
- **Exemplos/fixtures:** não há diretório de exemplos de usuário nem assets de demonstração empacotados em `app/src/main`; `GltfAmostras.kt`/`FbxAmostras.kt` e outras fixtures são sintéticas de teste. O harness `ModeloRealMedicaoTest` aceita modelos Khronos por caminho externo (`kenkern.modelos.dir`); não são exemplos distribuídos. Scripts Lua de exemplo permanecem F5.4 futuro.
- **Manifesto:** uma Activity launcher exportada, aceleração por hardware, backups habilitados, orientação sensorLandscape; não declara permissões INTERNET/armazenamento. SAF é utilizado para import/export. Validar política de backup/dados e requisitos de distribuição na fase de release, sem pedir permissões fora do escopo.

## 5. CI: situação e correção recomendada

`.github/workflows/build.yml` roda em `push` para `main` e `workflow_dispatch`, usa JDK 17, instala NDK/CMake fixos, roda i18n, `testReleaseUnitTest`, `assembleRelease`, valida JNI com `llvm-nm` sobre a `.so` extraída do APK e publica artifact. Não roda em `pull_request`; também não executa lint nem teste instrumentado/aparelho. `docs/FASE1.md` promete APK assinado, mas o build permite fallback silencioso para chave debug se assinatura ausente: artifact não deve ser apresentado invariavelmente como “release assinado”. Não inspecionei presença/valores dos secrets.

**Recomendação principal:** adicionar `pull_request` como gate sem secrets para i18n + testes JVM + lint com baseline explícito + build/JNI; separar a publicação do artifact de release assinado num job condicionado a push/tag confiável, que falha se assinatura não estiver configurada. Em PR, se publicar APK auxiliar, rotulá-lo claramente como debug-signed/não-release. Fixar imagem de runner (`ubuntu-24.04` ou equivalente deliberadamente versionado), manter JDK 17 documentado e SDK/NDK/CMake explícitos; definir `permissions: contents: read`. Isso fecha o maior buraco: alterações podem chegar à main sem CI pré-merge e artifact pode parecer assinado embora use chave debug. Manter lint fora do caminho de release só enquanto os avisos conhecidos tiverem baseline rastreável; a propriedade atual `abortOnError=false` isoladamente não é gate forte.

## 6. Divergências de baseline a resolver antes de iniciar trabalho novo

1. **Status/ordem:** PROGRESS diz G1–G3 v0.1.19 validados e M1 prioridade; em outro trecho ainda relata v0.1.18 aguardando usuário e ordem T0.4→T0.5→M1. ROADMAP adicionou prioridade de M1 após teste de textura, mas sua lista numerada ainda coloca T0.4/T0.5 antes de M1. Resolver a ordem com decisão explícita e editar os três docs juntos; não interpretar como autorização para remover T0.4/T0.5.
2. **HEAD mais novo que docs:** HEAD implementa a primeira fatia M1, mas o changelog mais recente continua v0.1.19 e `versionName` 0.1.19; registrar o que foi entregue e versão de teste antes de alegar release.
3. **Backlog M1:** exige detalhar M1.1–M1.8 ao abrir o marco, mas a implementação começou e não existe tabela de IDs/status/aceite. O próprio roteiro de TESTES agora descreve o recorte, não substitui decomposição do M1 completo.
4. **Contagem de Nicks:** BACKLOG T0.2 diz 17, NICKS/registry atual diz 18 por `camera.follow` registrado no M1. Atualizar contagem e delimitar se o tipo pertence ao M1 ou T0.2.
5. **F2.10:** o status “COMPILADO” contradiz o requisito LRU do título; registrar evicção/limites ou obter decisão de escopo explícita.
6. **F2.16 e instruções antigas:** `TESTES_PENDENTES.md` ainda diz que painel real virá em F2.12, mas o botão de importação já está em F2.16. Corrigir o roteiro para testar UI existente e reservar F2.12 ao browser de projeto.
7. **Formato/documentação:** código já escreve/lê KERN v2 com tabela de materiais; `docs/KERN_SPEC.md` ainda documenta apenas KERN v1/80-byte header. Arquitetura ainda diz CK1, embora CK2 seja formato atual. Atualizar specs antes de pedir implementações de F2/F4.
8. **Lua/arquitetura:** `LUA_API.md`, comentário da decisão #6 e alguns comentários do ProGuard dizem Fase 3; roadmap/backlog vigente é Fase 5. `ARCHITECTURE.md` não mapeia registry de Nicks, importação/KIMG, runtime M1 e núcleo nativo. `docs/FASE1.md` lista pendências já implementadas e usa formulação antiga.
9. **Aceite:** PROGRESS relata G1–G3 validados, mas deve continuar distinguindo o restante dos testes do aparelho; o aceite M1 ainda está pendente e não há aceite final da Fase 2.

## 7. Gates propostos, preservando todo o escopo

1. **Gate de baseline (antes de nova funcionalidade):** pai/usuário confirma este retrato; alinhar status, ordem e versões nos documentos indicados; reconciliar M1.1–M1.8 e LRU, sem eliminar requisitos. CI reproduzível e PR gate passam. Registrar hashes/versões de toolchain e resultado real de testes.
2. **Gate M1 completo:** formalizar os oito IDs/aceites antes de continuar; concluir física e formas exigidas, áreas/sinais, spawn, câmeras, Player/Enemy/NPC, input configurável M1.3 e cenário de jogo ponta a ponta conforme BACKLOG/DECISIONS/NICKS. Cada ID com testes JVM, diagnóstico planejado e roteiro de aparelho; só então aceite M1. O recorte já presente é etapa inicial, não substituto do escopo.
3. **Gate T0.4:** implementar fidelidade PBR completa glTF/FBX e os perfis Original/512/1024/2048 respeitando limite do aparelho; fixtures + modelos reais, cache/relatório, regressões e aceite no aparelho. Manter a parte de resolução já validada como evidência, não chamar T0.4 inteiro de feito.
4. **Gate T0.5:** entregar Hub inteiro (paralaxe/buraco negro/wordmark/recentes/idioma/Diagnóstico/atalhos/Sandbox), respeitando ≤30 FPS, pausa invisível, bateria e redução de animação; teste na orientação/dispositivos alvo e parada de usuário.
5. **Gate de fechamento da Fase 2:** concluir F2.10 LRU explicitamente e F2.11–F2.15; integração import→asset registry→browser→preview→prefab, falhas/referências quebradas, streaming sem bloquear GL, orçamento de memória e recuperação/cancelamento. Executar todos os aceites F2.1–F2.17, CI e aparelho; tag só depois de todos os itens + “aprovado”. Preservar meta de compressão/limitações registradas; qualquer alteração dessa meta é decisão, não corte silencioso.
6. **Gate para cada Fase 3–13:** ao abrir fase, decompor todos os requisitos originais em IDs, dependências, não-objetivos explícitos, testes JVM/diagnóstico e roteiro de aparelho. Implementar em commits pequenos, não iniciar fase seguinte para contornar TODO, executar regressões e CI, publicar APK de pré-lançamento, validação do usuário e tag apenas com fase fechada. Registrar decisão do KAI e demais bloqueios sem segredo.
7. **Gate stable 1.0:** só após Fases 1–13 e blocos associados fechados, nenhum ID TODO/DOING/BLOQUEADO sem decisão, CI completo e limpo, dispositivos/limites mobile da Fase 12 testados, parity GLES↔Vulkan e fallback da Fase 13 demonstrados, notices/licenças/SBOM revisados, migrações de projetos antigas testadas e aprovação final do usuário. Emitir relatório final previsto no roadmap. Não chamar uma release de teste de “1.0” nem trocar escopo por uma lista reduzida.

## 8. Conclusão

Baseline tecnicamente promissor, mas ainda de desenvolvimento: Fase 2 aberta e M1 em primeira fatia; T0.4/T0.5 e Fases 3–13 não concluídos. Próxima ação segura é aceitar/fechar o baseline documental, rodar e registrar a execução fresca de CI-equivalente e formalizar M1.1–M1.8 com os limites atuais explícitos. A recomendação é manter todos os requisitos e gates de fase; nenhum corte de escopo está proposto.
