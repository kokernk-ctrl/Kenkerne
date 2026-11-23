# Auditoria estática — render, lifecycle/GL, UI e input

**Projeto:** Kenkerne — branch `dev/1.0-estabilizacao`
**Escopo:** render, ownership de contexto/recursos GL, ciclo de vida Android/Compose, input e testes correspondentes.
**Resultado:** revisão somente leitura; working tree estava limpa. Nenhum arquivo do repositório foi alterado e nenhum cenário de exploração foi reproduzido. Os testes existentes foram inspecionados, mas não executados.

## Achados

### 1. [P1] Cena mutável é compartilhada sem sincronização entre UI e thread GL

**Referências**
- `app/src/main/java/com/kenkern/engine/render/KenkernSurfaceView.kt:1036-1047` — `onDrawFrame` obtém `sceneProvider()`, chama `loop.tick`, executa funções e recalcula `scene.updateWorldTransforms()` no thread GL.
- `app/src/main/java/com/kenkern/engine/render/EngineRenderer.kt:428-430, 512-520` — o renderer percorre `roots`/`children` e submete geometria enquanto o frame está em andamento.
- `app/src/main/java/com/kenkern/engine/editor/EditorViewModel.kt:393-397, 423-488, 496-498` e `commands/Command.kt:21-27` — comandos que alteram a cena são executados sincronicamente no chamador; os métodos de editor chamam `run()` sem encaminhar a mutação ao GL.
- `render/KenkernSurfaceView.kt:662-670` e `render/TransformGizmo.kt:382-456` (atualização do drag) — o gizmo altera o transform durante eventos de toque da UI; os `Vec3`, quaternion e matrizes em `core/Transform.kt:18-49` são mutáveis e não sincronizados.
- `ARCHITECTURE.md:35-43` descreve threads separadas, mas não documenta lock/snapshot para a árvore mutável.

**Problema/impacto:** a mesma `Scene`, listas `ArrayList` e transforms são lidos/escritos concorrentemente. Uma exclusão/reparent/reorder pode coincidir com a travessia GL; mesmo os edits de posição podem ser vistos a meio de uma escrita ou durante a recomputação das matrizes. O `catch` do frame evita alguns crashes fatais, mas pode descartar frames e não torna os dados consistentes. Também há publicação/leitura de `sceneRef` entre threads sem `@Volatile` ou mecanismo equivalente (`EditorViewModel.kt:109, 182-200`). É um risco estrutural para edição/Play, não apenas ausência de teste.

**Correção recomendada:** definir ownership explícito da cena. Opção preferida: serializar todas as mutações (comandos, undo/redo, gizmo e alterações de campos) no executor/thread proprietário do engine e publicar snapshots imutáveis para Compose. Alternativa menor para estabilização: lock de cena comum a mutações e a todas as leituras/travessias GL, incluindo os acessos da UI a dados vivos; não basta proteger apenas `updateWorldTransforms`.

**Testes para o fix:** teste de integração JVM com barreiras para intercalar mutação de árvore/transform e atualização/travessia; verificar que a operação é serializada e que não há frame com estado parcial. Cobrir add/delete/reparent/undo/transform e Play/update. Adicionar uma asserção de thread/ownership nos pontos de mutação para prevenir regressões.

### 2. [P1] Cache global de texturas retém handles inválidos após perda/recriação de contexto EGL

**Referências**
- `render/KenkernSurfaceView.kt:978-999` — em `onSurfaceCreated`, o cache de malhas é limpo e o backend é destruído/reinicializado, mas não há `TextureLibrary.limpar()`.
- `render/GlesBackend.kt:1100-1126` — `destroy()` limpa os objetos GL e o mapa `texturas` do backend.
- `render/TextureLibrary.kt:26-40, 59-82` — o mapa global `path -> handle` só é removido por `limpar()`; se o path já tem handle, não é re-enfileirado.
- `render/EngineRenderer.kt:459-462` — o renderer continua usando `TextureLibrary.handleDe(path)` e só enfileira quando o handle é zero.
- `render/GlesBackend.kt:852-860` — handle antigo não encontrado em `texturas` vira desenho sem textura (cor sólida), sem provocar novo upload.

**Problema/impacto:** se o Android/driver recriar a EGL surface/contexto, o backend elimina os handles GPU, mas `TextureLibrary` continua afirmando que os paths estão carregados. O renderer então não reenvia as texturas; os materiais passam a renderizar sem a textura até a cache ser limpa (por exemplo, ao recarregar projeto). `preserveEGLContextOnPause = true` não elimina perda de contexto em situações de pressão de memória/driver.

**Correção recomendada:** tornar o cache de recursos dependente de uma geração de contexto e invalidá-lo atomicamente no caminho de `onSurfaceCreated`/perda de contexto. Re-enfileirar paths ainda usados pela cena após a invalidação; evitar limpar handles de GPU de uma geração contra outra. Aplicar a mesma política aos caches de mesh/textura e estado de renderer.

**Testes para o fix:** usar backend fake e simular duas gerações: carregar path, invalidar contexto, garantir que o handle antigo deixa de ser retornado e o path é carregado uma vez no backend novo. Incluir teste Android/instrumentado para pausa + perda/recriação real do contexto, confirmando a textura visível sem reabrir projeto.

### 3. [P1/P2] Upload .KIMG usa filtro mipmapped mesmo quando só existe nível 0

**Referências**
- `render/GlesBackend.kt:896-940` — o upload normal percorre os mips; quando o fallback ETC2 dispara, decodifica e envia apenas `imagem.mips[0]` via `glTexImage2D`.
- `render/GlesBackend.kt:947-950` — independentemente do número de níveis carregados, escolhe `GL_LINEAR_MIPMAP_LINEAR` como min filter; não chama `glGenerateMipmap`.
- `assets/image/KimgPipeline.kt:185-198` — o caminho `converterRgba8Raw` produz deliberadamente um `.KIMG` com um único mip.

**Problema/impacto:** em texturas com dimensão maior que 1, uma textura GL com apenas o nível 0 é incompleta sob um min filter que exige mipmaps. Portanto, `.KIMG` `RGBA8_RAW` desse pipeline não é amostrada corretamente; o fallback ETC2 também fica incompleto justamente quando o driver recusou a compressão. O código aceita/cria o handle, então o defeito se manifesta como textura ausente/indefinida sem erro de upload conclusivo.

**Correção recomendada:** configurar `GL_LINEAR` quando só o mip 0 foi carregado, ou gerar uma cadeia de mipmaps RGBA8 após upload do nível base (`glGenerateMipmap`). No fallback ETC2, escolher explicitamente uma dessas estratégias e verificar erro GL depois da geração. Preservar o caminho de mips ETC2 já fornecidos.

**Testes para o fix:** teste de backend em contexto GLES (instrumentado/offscreen) com `RGBA8_RAW` 4×4/um mip e com ETC2 forçado a fallback; consultar `glGetTexParameter`/status de completude e amostrar pixels em minificação. Adicionar teste com cadeia ETC2 completa para garantir que ela continua sendo usada sem geração desnecessária.

### 4. [P2] Estado do wireframe do collider sobrevive à recriação do backend com handle antigo

**Referências**
- `render/EngineRenderer.kt:174-192` — `initialize()` zera handles de cubo/gizmos/helpers e chaves de pirâmide, mas não zera `colliderWireHandle`, `colliderWireCount` nem `colliderWireKey`.
- `render/KenkernSurfaceView.kt:985-999` — recria o backend no callback de nova surface e chama `engineRenderer.initialize()`.
- `render/EngineRenderer.kt:545-559` — buffer do collider só é criado se a chave mudou; mantendo a mesma seleção/configuração, o handle antigo é usado após o mapa do backend ter sido destruído.
- `render/GlesBackend.kt:1108-1114` — `destroy()` limpa os buffers de linha.

**Problema/impacto:** após perda/recriação de contexto, quando o collider selecionado mantém a mesma chave, `colliderWireKey` faz o renderer pular o upload; `colliderWireHandle` aponta para um buffer que não existe mais. O wireframe do collider desaparece permanentemente até alterar seleção ou propriedades do collider.

**Correção recomendada:** invalidar todo estado de handles context-bound no reset do renderer, inclusive collider (handle/count/key), antes de reinitializar; preferível encapsular em uma rotina única `onContextLost/resetGpuResources` e aplicar a todos os handles guardados pelo renderer.

**Testes para o fix:** backend fake que invalida os handles entre duas chamadas `initialize()`; renderizar o mesmo collider antes/depois e afirmar que `createColoredLineBuffer` é chamado novamente e que o buffer é submetido na segunda geração.

### 5. [P3] Diagnóstico de movimento de toque é zerado no UP depois de um drag

**Referências**
- `render/TouchController.kt:129-155` — o ramo normal de drag usa `x/y`, mas não atualiza `lastX/lastY` (ao contrário do ramo de gizmo, linhas 119-125).
- `render/TouchController.kt:249-250, 275-287` — no UP calcula `movedPx` usando `downX - lastX` e publica esse valor.
- `render/KenkernSurfaceView.kt:157-162` e `ui/screens/EditorScreen.kt:1236-1241` — o overlay exibe `touchMovedPx` como “ΔDOWN”. `publishMove` informa corretamente enquanto o dedo está movendo, mas `publishUp` substitui a medida pelo valor baseado em `lastX/lastY`.

**Problema/impacto:** ao terminar um drag normal de câmera, o painel de diagnóstico mostra deslocamento zero (ou desatualizado), apesar de o gesto ter movido a câmera. A classificação do gesto e o movimento da câmera continuam corretos; o problema é a telemetria da UI, que pode induzir diagnóstico errado.

**Correção recomendada:** calcular a distância diretamente do ponto de `ACTION_UP` para `downX/downY`, ou manter `lastX/lastY` em todos os movimentos. Garantir também que `ACTION_CANCEL` não reporte um tap/medida enganosa.

**Testes para o fix:** estender teste de `TouchController` com mock de `MotionEvent`: drag de 100 px deve publicar `touchMovedPx ≈ 100` após UP; teste separado de jitter/tap e CANCEL.

## Cobertura existente observada

Os testes render/input já cobrem raycast/projeção/picking (`OrbitCameraRayTest`, `ToqueCuboPickingTest`), pinça e transições de dedos (`PinchZoomRegressionTest`), olhar no voo (`FlightLookRegressionTest`), câmera de Play (`NickPlayCameraTest`) e geometria auxiliar (`EditorHelpersTest`). Não encontrei testes correspondentes para recriação do contexto/handles do backend, completude de textura GL, nem exclusão concorrente da cena entre UI e thread GL. O contrato de ciclo de vida é implementado em `EditorScreen.kt:784-808` e os callbacks GL em `KenkernSurfaceView.kt:978-1112`; esses caminhos não têm teste de unidade dedicado no conjunto atual.

## Ordem sugerida para a estabilização 1.0

1. Resolver ownership/concorrência da cena (maior risco de inconsistência de edição/Play).
2. Corrigir reset de recursos context-bound e adicionar testes de duas gerações para textura e collider.
3. Corrigir completude do upload de `.KIMG` e validar com contexto GLES real.
4. Corrigir apenas a métrica de diagnóstico de toque.
