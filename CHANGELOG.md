# KENKERN — CHANGELOG

## 0.1.20-estabilizacao — homologação; não é a release 1.0

- Projetos: persistência atômica sem apagar previamente a cena; recuperação pela cópia válida; undo/redo preserva filhos, ordem, IDs, links e histórico em falhas de I/O. KERNPACK preserva pastas e cronologia das cópias.
- Editor/runtime: edição e frame compartilham uma regra de acesso à cena; Play roda numa cópia. Novos controles configuráveis de toque, diagnóstico e ações por nome. Exemplo de jogo versionado em `exemplos/teste-m1/`.
- Hub e assets: atalhos Modelar/Animar/Texturizar no Sandbox, projetos recentes, navegador com busca/filtro, miniaturas de texturas e detalhes de malhas. A aba de animação não é uma timeline e miniatura de textura não é preview de malha 3D.
- Import/render: normais sob escala não uniforme e accessors normalizados em glTF; leitura defensiva de KIMG; seleção dos mipmaps e invalidação de recursos por contexto. Validações dos formatos, GLES no host e modelos reais documentadas na auditoria.
- Distribuição: gate de PR sem credenciais, tags exigem assinatura, lint bloqueante, checagem das bibliotecas nativas no APK. Este build local **não substitui** a versão pros usuários da chave de distribuição.

## v0.1.19 — 3 BUGS DE LAYOUT (G1–G3): PAISAGEM 800×360, NENHUMA FUNCIONALIDADE NOVA

> Tarefa: 3 bugs de layout da v0.1.18, edições pontuais, CI verde por etapa
> (G1, G2, G3, F). Referência de tela do usuário: paisagem 800dp × 360dp —
> viewport com ~240dp de altura útil (TopBar 48 + StatusBar 24 + barras do
> sistema). TODO layout novo funciona com 240dp de altura útil.

### G1 — TopBar estoura e o botão do Inspetor some

- **Causa raiz**: a Row única da `EditorTopBar` (EditorScreen.kt:775-976,
  v0.1.18) tinha `Spacer(Modifier.weight(1f))` entre as ferramentas e os
  botões de painel — com botões de voo e auxiliares ativos o espaço que
  sobrava ia a zero e a Row transbordava a largura: a Hierarquia ficava
  espremida na borda e o Inspetor NÃO aparecia (~20 itens fixos > 800dp).
- **Correção**: layout em TRÊS seções — [INÍCIO FIXO] Voltar + separador;
  [ROLÁVEL] ferramentas (Undo/Redo · Play/Pause/Stop · Importar · Gizmo ·
  Câmera · Auxiliares · Diagnóstico de toque · Resolução) em
  `Row(horizontalScroll(rememberScrollState))` com FADE de 16dp nas bordas
  quando há conteúdo fora da vista (`canScrollBackward`/`canScrollForward`,
  `TopBarEdgeFade` novo); [FIM FIXO] separador + Hierarquia + Inspetor +
  Resetar layout — FORA da rolagem, SEMPRE visíveis. Botões com tamanho FIXO
  de 40dp (sem weight, sem encolher). Matemática pura em `TopBarLayout`
  (novo, sem Compose): início fixo 49dp + fim fixo 129dp + padding 8dp =
  186dp fixos; `larguraRolavelDp` nunca negativa.
- **Provas**: (1) `TopBarLayoutTest` — 5 testes JVM: soma dos botões fixos
  (4×40dp = 160dp) + separadores/padding (186dp) ≤ 800dp; rolável em 800dp =
  614dp ≥ mínimo 120dp; retrato 360dp rolável 174dp suficiente; rolável nunca
  negativa em telas estreitas; (2) checagem "layout" NOVA no Diagnóstico —
  a matemática da TopBar contra a largura REAL do aparelho (displayMetrics),
  APROVADO com tela×dp + fixa + rolável + orientação, FALHOU com cabe=/suficiente=
  se abaixo do mínimo da engine; (3) 227 testes JVM verdes (222 + 5).

### G2 — janela "Adicionar Nick" sem rolagem; só aparecem as 2 opções filho/raiz

- **Causa raiz**: cabeçalho em 3 camadas empilhadas (Row título 44dp +
  `OutlinedTextField` 64dp + `TabRow` 48dp) + rodapé com 2 `RadioButton` de
  48dp cada consumiam os ~240dp de viewport útil em paisagem — o LazyColumn
  de tipos ficava com altura ZERO e as abas do `TabRow` encolhiam para
  "R…", "B…", "C…" (largura dividida igualmente entre 7 abas).
- **Correção**: (1) CABEÇALHO FIXO de UMA linha de 48dp (título + busca
  compacta em `BasicTextField` de 36dp + fechar 40dp); (2) TODO o resto em
  UM único `LazyColumn` que rola junto: seletor filho/raiz = 2 CHIPS
  compactos de 36dp (FilterChip; os RadioButtons gastavam 96dp), abas de
  categoria = CHIPS em linha com rolagem horizontal e NOME COMPLETO, e os
  cartões de tipo; (3) a janela é MODAL NA RAIZ do EditorScreen (fora da Row
  central): usa a ALTURA TODA do viewport (TopBar e StatusBar continuam
  visíveis) e pode PASSAR POR CIMA do Inspetor acoplado se o usuário quiser
  — largura 60% da tela (era 40%, máx 640dp); (4) contentPadding inferior
  COM INSETS (`WindowInsets.navigationBars` + base 16dp): o último tipo do
  catálogo é alcançável com 240dp mesmo com barra de gestos; (5) BARREIRA DE
  TOQUE mantida na raiz do painel — a rolagem da janela NUNCA move a câmera.
- **Provas**: (1) `AddNickLayout` (novo) + `AddNickLayoutTest` — 5 testes
  JVM: cabeçalho 48dp ≤ 20% da altura útil (exatamente 20% de 240dp); altura
  mínima da lista (chips 36+8 ×2 + cartão 52 + padding 16 = 156dp) com
  cabeçalho = 204dp ≤ 240dp; último tipo alcançável com inset 24dp
  (228 ≤ 240); padding inferior 16+inset; chips de 36dp; (2) checagem
  "layout" do Diagnóstico ESTENDIDA (G1+G2): cabeçalho ≤ 20% + lista mínima +
  último tipo contra a altura útil REAL; (3) 232 testes JVM verdes (227 + 5).

### G3 — controles de voo por cima do painel

- **Causas raiz**: (1) o `FlightControls` (EditorScreen.kt:~389, v0.1.18)
  era composto SEMPRE que `cameraMode == FLIGHT` — com a janela Adicionar
  Nick aberta o joystick (108dp), os botões subir/descer (52dp) e o slider de
  velocidade ficavam por BAIXO da janela modal, reagindo a toques que eram
  para ela; (2) o Box raiz do FlightControls ignorava INSETS — o slider de
  velocidade ficava cortado embaixo pela barra de gestos do sistema.
- **Correção**: (1) condição `!addNickVisivel` — com a janela (painel modal)
  aberta o FlightControls INTEIRO não existe; ao fechar, volta (modo FLIGHT
  continua ativo, câmera não muda); (2) Box raiz com
  `windowInsetsPadding(WindowInsets.safeDrawing)` (consumption-aware: os
  insets já consumidos pela Column raiz entram como 0; barra de gestos/notch
  são aplicados) — o controle de velocidade INTEIRO dentro da ÁREA SEGURA;
  padding inferior do slider 12→16dp.
- **Provas**: 232 testes JVM verdes (condição de composição não roda em JVM;
  aceite no aparelho em TESTES_PENDENTES), check_i18n OK, lint SEM ERROS
  NOVOS (os mesmos 2 pré-existentes: themes.xml NewApi + EditorScreen
  RememberReturnType).

### F — fechamento

- Bump versionCode 20 / versionName **0.1.19-fase2**; CHANGELOG e
  TESTES_PENDENTES atualizados; 232 testes verdes; lint sem erros novos;
  Release de pré-lançamento **v0.1.19-fase2-teste** com o APK assinado.

## v0.1.18 — 6 BUGS DO APARELHO (E1–E6) + T0.3: CORREÇÕES DO TESTE DE CAMPO

> Tarefa: 6 bugs do aparelho, edições pontuais, CI verde por etapa. Fase 2
> CONTINUA ABERTA (F2.11–F2.15); NENHUMA tag `fase-2-ok`.

### E1 — handler nativo não instalava ("No implementation found")

- **Causa raiz**: kenkern_native.cpp:205/236 exportavam
  `Java_…_NativeCore_instalarHandlerSinais`/`…_handlerInstalado`, mas o Kotlin
  declara `instalarHandlerSinaisNativo`/`handlerInstaladoNativo` — a .so
  carregava (versão 1.0.0) e o JNI não encontrava os SÍMBOLOS (nome sem o
  sufixo "Nativo").
- **Correção**: símbolos renomeados com o sufixo da declaração Kotlin (mesma
  regra das outras 5 externas).
- **Provas**: (1) `nm -D --defined-only` na .so do host (mesma fonte do APK)
  antes = exatamente as 2 faltando, depois = 8 external fun com símbolo;
  (2) novo `scripts/check_jni_symbols.sh` deriva TODA `external fun` × os
  símbolos da .so e FALHA o build se qualquer uma faltar;
  (3) CI confere a libkenkern.so arm64 EXTRAÍDA DO APK RELEASE com o
  llvm-nm do NDK;
  (4) `NativeCore.testarExternas()` testa cada função JNI individualmente e a
  checagem "nativo" lista cada uma com ✓ ou com o motivo literal;
  (5) teste JVM carrega a .so do HOST e valida instalar/consultar (regressão).
- **Correção do próprio E1 no CI** (bug encontrado pelo CI): o teste instalava
  handlers de SIGSEGV na JVM de testes — o HotSpot usa SIGSEGV para
  NullPointer implícito e o executor morria com exit 139. Nova externa
  `restaurarSinaisNativo` devolve as ações originais (salvas no install); o
  teste RESTAURA em finally. No app o handler continua ativo até o processo
  morrer (B3 preservado).

### E2 — Diagnóstico não podia travar ("ETC2 nativo: executando…" infinito)

- **Causa raiz**: `Diagnostico.executar` era BLOQUEANTE numa thread de fundo,
  SEM timeout e SEM botão cancelar; a checagem "etc2 nativo" rodava o ENCODER
  KOTLIN em 1024² (o custo dominante: o nativo leva milissegundos) + 2
  decodificações 1024² + PSNR — dezenas de segundos num aparelho médio.
- **Correção**: `executar` é SUSPENSE; cada checagem roda em background com
  `withTimeout(30 s)` + `runInterruptible` (laços pesados consultam
  `diag.cancelado`); tempo de cada checagem na tela (ms/s); botão CANCELAR
  (Job + flag; só o que NÃO finalizou vira CANCELADA); checagem que estoura =
  FALHOU "tempo limite" e a rodada PARA; bloco GL numa ÚNICA thread dedicada
  (EGL é current na thread criadora) com `Thread.interrupted()` limpo a cada
  checagem; "etc2 nativo" mede o NATIVO em 1024² (meta B5 < 1 s, conferida na
  aprovação) e compara nativo×Kotlin (PSNR/tamanho) em 256².
- **Provas**: 5 testes JVM novos (ciclo PENDENTE→RODANDO→APROVADO/FALHOU/
  CANCELADA com tempo; timeout fixado em 30 s).

### E3 — janela "Adicionar Nick" cortada no fim da lista

- **Causa raiz**: `LazyColumn(Modifier.fillMaxSize())` DENTRO da Column —
  fillMaxSize consome TODA a altura restante: o rodapé media 0 e o fim da
  lista ficava sob a borda/insets (último tipo inalcançável em paisagem).
- **Correção**: `weight(1f)` (altura = espaço que sobra depois de cabeçalho +
  busca + abas + rodapé), `contentPadding` inferior 16dp, sem verticalScroll
  aninhado; BUSCA e ABAS continuam fixas acima.
- **Barreira de toque** (E3/E4): `pointerInput + detectTapGestures` na raiz do
  painel — TODO toque no painel mora no painel: ROLAGEM E TAPS NUNCA MOVEM A
  CÂMERA (antes o toque em área vazia atravessava para o viewport).

### E4 — painéis flutuantes não moviam nem redimensionavam

- **Causa raiz**: `PanelUiState.floatX/floatY/floatW/floatH` eram `var Float`
  COMUNS (PanelStateStore.kt:28-31) — o gesto escrevia nelas, mas
  `Modifier.offset{}`/`width()`/`height()` só reagem a estado OBSERVÁVEL:
  nada recompunha e o painel ficava parado.
- **Correção**: campos viram `MutableState<Float>`; mover pelo CABEÇALHO com
  clamp que mantém o painel INTEIRO dentro do viewport (a cada evento e no
  fim); redimensionar pela alça do CANTO INFERIOR DIREITO com alvo de toque
  48dp (era 44×14dp), mínimo 220×200dp e máximo = viewport; TRAZ PARA FRENTE
  ao tocar (zIndex); botão ACOPLAR no cabeçalho (ícone alfinete); botão
  RESETAR LAYOUT na TopBar (visíveis + acoplados + tamanho padrão); posição e
  tamanho SALVOS POR PAINEL E POR ORIENTAÇÃO (chave "painel#land/port");
  barreira de toque no card e nos painéis acoplados-overlap.
- **Provas**: 8 testes JVM (clamp nas 4 bordas + painel maior que o bounds,
  parse/format ida e volta, corrompidos caem no padrão, chave por orientação,
  mínimos).

### E5 — Nicks sem malha invisíveis/misleading: AUXILIARES DE EDITOR

- **Fatos**: luz/câmera/área/sinal/surgimento/nick.empty eram "folha sem
  malha" e desenhavam o CUBO cinza de 1 m como se fossem geometria (criados
  como filho dentro de outra malha ficavam ocultos).
- **Correção**: `MalhaNick.ehAuxiliar` (puro): luz/câmera/área/vazio SEM malha
  são AUXILIARES; Nick COM malha é SEMPRE geometria (migração CK1 e
  importados preservados). `EditorHelpers` (geometria pura em GL_LINES):
  direcional=SETA · pontual=ESFERA com anel de alcance (simbólico) ·
  foco=CONE · câmera=PIRÂMIDE COM O FOV REAL (reconstruída por chave
  fov|aspect) · área=CAIXA 1×1×1 em arame · demais=LOSANGO; cor por grupo
  (luz #FFD54F, câmera #4FC3F7, áreas #66BB6A, base #B0BEC5), ÂMBAR #FFA940
  quando selecionado; TAMANHO CONSTANTE na tela; desenhados SÓ no editor
  (nunca em Play, nunca no jogo exportado); seleção por toque com a MESMA
  tolerância de 24dp (AABB de toque 0,4 m); botão "Mostrar auxiliares" na
  TopBar (padrão ligado). body.* segue com o cubo placeholder (até M1/FORJA).
- **Novo `RenderBackend.drawWorldLines`** (linhas no espaço do mundo, viewport
  completo + depth) — e o WIREFRAME DO COLISOR migrou para ele: o `drawGizmo`
  antigo desenhava o collider no MINI-VIEWPORT do canto (gizmo de eixos) —
  defeito herdado do T0.3 corrigido.
- **NICKS.md**: **luz Nick AINDA NÃO AFETA a cena** (DefaultLightingPipeline =
  sol fixo; iluminação por Nick entra no LUME); FOV próprio da câmera entra
  no LENTE; corpo placeholder até M1/FORJA. NÃO implementamos iluminação
  nesta tarefa (pedido explícito).
- **Provas**: 12 testes JVM atualizados/novos (classificação por tipo, malha
  vence o tipo, auxiliar não conta em M, template M=1) + 5 de geometria
  (forma por tipo, cor por grupo, pares GL_LINES válidos, pirâmide com
  tan(fov/2)×aspect, 12 arestas da caixa).

### E6 — StatusBar "N nicks · M malhas"

- O contador do renderer JÁ media grupos de material (+1 por submitGroup do
  .KERN; +1 por submit = grupo implícito do cubo/malha sem grupos), mas o
  TEXTO dizia "desenhados" (ambíguo com N da Hierarquia).
- Renomeado `nicksDesenhados`→`malhasDesenhadas` (renderer/view/StatusBar);
  string `editor_nicks_meshes` ("%d nicks · %d malhas"); checagem "cena =
  tela" reescrita ("M malhas medido", 1 grupo por malha na cena de
  referência, auxiliares não contam); teste equivalente atualizado
  (`auxiliarNaoContaEmM`, cena padrão N5/M2).

### F — fechamento

- versionCode 19 · versionName 0.1.18-fase2; Release de PRÉ-LANÇAMENTO
  `v0.1.18-fase2-teste` com o APK assinado.
- CI: suíte JVM (222 testes) + assembleRelease + **E1: llvm-nm confere TODA
  external fun × os símbolos da libkenkern.so do APK** + i18n en/pt.
- Testes de aceite no aparelho: TESTES_PENDENTES.md (seção v0.1.18).

## v0.1.17 — 2 BUGS DO APARELHO + NÚCLEO NATIVO C++ (etcpak + meshoptimizer)

> Bug A: viewport desenhava objetos fora da cena (torus + cubo do "Mundo").
> Bug B: importação lenta (modelo de 2 MB cancelado por demora). B1 mede TODA
> etapa; B2 acelera o Kotlin; B3/B4/B6 levam o pesado para C++17 via NDK.
> Fase 2 CONTINUA ABERTA (faltam F2.11–F2.15); NENHUMA tag `fase-2-ok`.

### BUG A — viewport desenhava geometria fora da cena

- **Auditoria A1** (todas as chamadas de draw/submit do caminho de render):
  drawSky EngineRenderer:323 (overlay) · **submit do torus EngineRenderer:345
  (buildTestMeshViaKern:212-252 — FORA da cena)** · submitGroup/submit/outline
  :384-416 (Nicks) · **submit(cubeHandle) EngineRenderer:408 — TODO Nick sem
  malha virava cubo, INCLUSIVE a raiz "Mundo"** · flush:430 · grade:434 ·
  gizmos:475-494 · clear emergencial KenkernSurfaceView:966.
- **Causas raiz**: (1) torus de teste submetido TODO frame girando em
  (0,1.4,0), sem Nick = impossível selecionar; (2) "Mundo" (container) desenhado
  como cubo na origem — 2 cubos sobrepostos + torus = 3 objetos, 1 na
  Hierarquia selecionável.
- **Correção (regra "cena = tela")**: `MalhaNick.desenhavel()` é a FONTE ÚNICA
  (tem malha OU é folha) usada pelo render, picking e foco; containers não
  desenham nem são pegáveis; torus removido do render (validarKernRoundtrip sem
  GPU) e passa a renderizar SÓ na checagem "cena = tela" do Diagnóstico
  (offscreen). StatusBar "N nicks · M desenhados" (M = nicksDesenhados do
  renderer); checagem "cena = tela" exige M medido == visíveis com malha +
  pixel A/B com visible + torus offscreen. 9 testes JVM (MalhaNickDesenhavelTest).

### BUG B — importação lenta (B1 medição + B2 Kotlin + B4/B6 nativo)

- **B1 MEDIR PRIMEIRO**: `CronometroImport` mede leitura, parse, dedup, LODs,
  decodificação/redimensionamento/mipmaps/ETC2/gravação POR TEXTURA, .KERN,
  criação do Nick — tabela com ms e % do total no ImportReport E no Console;
  resolução e formato de cada textura (origem→final + PNG/JPEG) no relatório.
- **B2 Kotlin**: limite de textura 1024 padrão (512/1024/2048 no diálogo,
  reduz ANTES dos mipmaps); ETC2 rápido por padrão (individual+diferencial+
  planar; T/H só em "Alta qualidade"); blocos em N threads (cores−1) + texturas
  em paralelo; cancelamento dentro dos laços (< 1 s, testado); progresso
  "textura 2/5 · 43% · ~12s". **Correção incluída**: codificarRgba gravava
  blocos em COLUNA-major contra o decoder/GL linha-major (texturas com alfa
  transpostas na GPU).
- **B3 núcleo C++**: NDK 27.3.13750724 + CMake 3.22.1 FIXADOS (Gradle e CI),
  arm64-v8a; `NativeCore` (isAvailable/versão/ABI, carga registrada, fallback
  Kotlin automático em TODO módulo); handler de sinais SIGSEGV/SIGABRT/SIGBUS/
  SIGFPE gravando `crash_logs/native_crash.txt` (sinal, endereço, backtrace) —
  o diálogo de crash do Hub mostra esse arquivo também; checagem "nativo"
  (biblioteca, versão, ABI, eco(21)=43, handler instalado — NUNCA crash de
  propósito); -keep R8 para JNI/NativeCore.
- **B4 ETC2 nativo (etcpak BSD-3-Clause, third_party/etcpak + LICENSES.md)**:
  JNI com ByteBuffer DIRETO, multithread em C++, TODOS os limites validados
  (256 Mpx/512 MB, buffers conferidos), conversão RGBA→BGRA + padding de borda
  no C++; fallback Kotlin com motivo no Console; checagem "etc2 nativo"
  (1024²: ms · MP/s · PSNR × Kotlin). **Medido no host (mesma fonte do APK):
  nativo 17 ms × Kotlin 5.508 ms = 324×**; o número do APARELHO vem da checagem.
- **B6 meshoptimizer (MIT, third_party/meshoptimizer + LICENSES.md)**: dedup
  (generateVertexRemap — MESMA contagem de únicos que o Kotlin, registro de
  32 B = os 8 floats da ChaveVertice), cache de vértices POR GRUPO
  (optimizeVertexCache) e LODs por simplificação (simplify com ERRO reportado);
  ByteBuffer direto + limites validados; PosProcessamento usa o nativo com
  fallback Kotlin; checagem "malha nativa" (grade 20.000 tris: ms × Kotlin).
- **Testes nativo × Kotlin SEM aparelho**: a MESMA fonte C++ compila para o
  HOST (task compilarNativoHost, g++) e os testes JVM carregam via seam
  documentado — PSNR nativo ≥ Kotlin−1 dB, tamanhos idênticos, 4×4, 6×6/7×3,
  layout RGBA, determinismo, contagem de únicos, LODs coerentes, entradas
  inválidas → null.
- **20 checagens no Diagnóstico** (novas: nativo, etc2-nativo, malha-nativa,
  cena). APK: 1.663.853 B (v0.1.16) → 2.094.813 B (+431 KB = libkenkern.so
  463 KB com etcpak+meshoptimizer+handler). 184 testes JVM verdes.

# KENKERN — CHANGELOG

## v0.1.16 — CORREÇÃO das 4 falhas do Diagnóstico no aparelho (nenhuma funcionalidade nova)

> Aparelho do usuário: Mali-G57, GLES 3.2, v0.1.15 — 9 aprovadas, 4 falharam
> (picking, release, importar, etc2). Fatos do aparelho valem mais que testes
> de JVM. 4 checagens novas (16 no total). Fase 2 CONTINUA ABERTA
> (faltam F2.11–F2.15); NENHUMA tag `fase-2-ok`.

### D1 — picking "divergência: origem orbital" (prioridade máxima)

- **Causa raiz** (Diagnostico.kt, checarPicking): a CHECAGEM comparava a origem
  do raio com `target + fwd*dist` usando `fwd = forwardVector` (DIREÇÃO DE
  OLHAR) — sinal INVERTIDO: o olho fica em `target − fwd*dist`. O raio de
  `OrbitCamera.screenRay` SEMPRE nasceu no olho (os testes JVM de raio comparam
  com `eyePoint` desde a v0.1.x e passavam). O aparelho divergiu por um bug na
  checagem, não por float/densidade/escala de resolução.
- **Correção**: a checagem usa `eyePoint()` (fonte única); mensagem de falha
  mostra olho, origem do raio, diferença, modo e near dinâmico (nunca genérica).
- **Fonte única do hit-test**: `PickingMath` NOVO (render) — `rayAabb`,
  `worldAabb` e `projectedAabbDistance` saíram do KenkernSurfaceView (privados)
  e agora são o MESMO código usado pelo viewport e pelo Diagnóstico; olho/raio
  continuam com UMA função (`OrbitCamera.screenRay`), usada por picking, gizmo
  e checagens (o foco usa `focusOn`, mesma base interna).
- **Checagem NOVA "toque no cubo"**: cena padrão (Mundo > Cubo), tap sintético
  pela API REAL do TouchController (MotionEvent DOWN→UP em 150 ms, slop 12 dp×
  density 3), raio pelo pixel PROJETADO do centro do cubo, hit obrigatório.
- **Testes JVM**: ToqueCuboPickingTest reproduz as entradas EXATAS da checagem
  e varre 5 views (paisagem/retrato) × 7 distâncias (0,1–2000 m) × 3 escalas
  (100/75/50%) — origem = olho e hit em TODAS as combinações.

### D2 — release minificado: LuaJ quebrado pelo R8

- **Causa raiz comprovada no bytecode** (javap do luaj-jse 3.0.1):
  `Bit32Lib.call()` resolve `Bit32Lib$Bit32LibV` por `Class.forName` e
  `LibFunction.bind()` instancia por `Class.newInstance()` — REFLEXÃO em todas
  as libs (bit32/math/string/table). No release o R8 renomeava org.luaj.*
  (141 classes → a2.*) e o newInstance quebrou no aparelho.
- **A conclusão V1 ("zero reflexão, R8 refutado") valia SÓ para a engine e
  estava ERRADA para o LuaJ** — reconhecido em proguard-rules.pro e DECISIONS #23.
- **Correção**: `-keep class org.luaj.** { *; }` com motivo + auditoria dos
  terceiros (org.json e mockito são testImplementation; androidx/kotlinx têm
  consumer-rules próprios — LuaJ era o único reflexivo no APK). O primeiro push
  desta regra quebrou o R8 (luajc → Apache BCEL ausente no Android) — corrigido
  com `-dontwarn org.apache.bcel.**` (a engine usa LuaC, nunca o luajc).
- **Checagem "release" agora roda script Lua REAL**: print + função com math,
  bit32 e string; o VALOR é conferido (28 = floor(abs(-2.5)+4.5) +
  bor(lshift(1,4),3) + len(format("%x",19)) = 7+19+2; +1 no on_update = 29).
  Um script que só soma floats passaria e esconderia o bug.
- **Por que o CI não roda a suíte contra o dex minificado**: R8 produz ART/dex,
  unit tests JVM rodam sobre classes compiladas; exigiria emulator
  (connectedAndroidTest) — a prova definitiva é a checagem "release" NO APARELHO.

### D3 — importar: "undo não recolheu o .KERN para a lixeira de importação"

- **Causa raiz** (Diagnostico.kt, checarImportar): a checagem procurava o
  arquivo em `assets/.lixeira-import/<rel>` — SEM o `<importId>` que o Command
  usa (`assets/.lixeira-import/imp-<id>/<rel>`). **O undo FUNCIONAVA**; a
  checagem olhava a pasta errada e falhava no aparelho.
- **Correções no Command** (robustez, não funcionalidade nova): lixeira fica
  DENTRO do projeto (mesmo volume por construção); `Files.move(REPLACE_EXISTING)`
  com fallback copiar+apagar verificado por tamanho; nomes já existentes
  substituídos com registro; falha final = `ImportException` com origem,
  destino, existe/canRead/canWrite e mesmoVolume (FileStore) + KenkernLog.
- **Testes JVM** (ImportModelCommandTest, 4): undo→lixeira→redo; volume
  diferente SIMULADO no ponto exato do Files.move com o fallback rodando por
  dentro; colisão de nome; falha total com os caminhos na mensagem.

### D4 — etc2: pixel lido da GPU era (0,0,0), esperado (117,117,0)

- **Causa raiz** (EglDiag.kt): o pbuffer offscreen era **1×1** e a checagem
  desenhava num viewport 4×4 e lia o pixel (1,2) — **leitura FORA da
  superfície** = GL_INVALID_OPERATION e buffer zerado. Upload e draw estavam
  corretos; a LEITURA que falhava no Mali-G57.
- **Correção**: pbuffer 64×64 + framebuffer offscreen PRÓPRIO com os TRÊS casos
  separados na mensagem: (a) `glGetError` do upload (valor + nome da constante);
  (b) `glCheckFramebufferStatus` do FBO (nome); (c) pixel após draw real com
  GL_NEAREST, só mip 0, comparado com a DECODIFICAÇÃO EM CPU do MESMO bloco.
  Caso de CONTROLE RGBA8 (o mesmo upload do fallback do GlesBackend) separa
  "driver recusou ETC2" de "caminho GL quebrado". Verificações registradas no
  código: internalformat 0x9274/0x9278 (valores crus, sem variante sRGB —
  comparação direta com a CPU), tamanho de bytes = mip.dados.size,
  GL_UNPACK_ALIGNMENT irrelevante em upload comprimido (RGBA8 = 4 B/px =
  alinhamento padrão), min filter GL_NEAREST sem mipmap incompleto, uniform +
  unidade 0, draw antes do glReadPixels no mesmo contexto, blocos linha-major.
- **PSNR 10,7 dB explicado e corrigido**: o padrão 8×8 da checagem kimg era
  ADVERSARIAL (alfa alternada por pixel = pior caso do EAC). Medição JVM dos
  dois padrões: **10,71 dB adversarial — exatamente o número do aparelho** —
  vs **24,66 dB realista** (gradiente suave + ruído ±3; textura 8×8 é minúscula,
  cada bloco 4×4 quantiza ¼ do gradiente; texturas reais 2048× ficam em
  30,6–39,2 dB, auditoria V2). Os DOIS PSNRs agora vão na mensagem.
- **Checagem NOVA "tex-cena"**: quad texturizado pela textura criada no caminho
  REAL da engine (`GlesBackend.createTextureFromKimg`, com o fallback RGBA8
  automático — que registra no Console) e o pixel central NÃO pode ser preto.

### D5 — paisagem automática não funcionava

- **Causa raiz** (AndroidManifest.xml): `android:screenOrientation="fullUser"`
  na ÚNICA Activity do app — o Hub, o Diagnóstico e o editor vivem nela via
  KenkernAppRoot; nenhum tema sobrescreve e não existe `setRequestedOrientation`
  no código (verificado por grep). Correção: **sensorLandscape** (paisagem
  automática com o sensor, as duas laterais).
- **Cabeçalho do Diagnóstico**: o subtítulo "checagens da engine neste aparelho"
  cortava porque a Row tinha altura FIXA de 56 dp (não cresce com a fonte do
  sistema) — agora `heightIn(min = 48.dp)`, título e subtítulo em 1 linha cada.

### Verificação

- 142 testes JVM verdes (4 novos na D1/D3, 1 novo na D4); assembleRelease verde
  local (R8 com as regras novas); lint sem erros novos; APK release
  1,7 MB (0,15 MB a mais = LuaJ inteiro preservado pelo -keep do D2).
- Correção da correção: push D2/D3/D4/D5 falharam no CI em
  minifyReleaseWithR8 (Missing class org.apache.bcel…) — corrigido no commit
  seguinte (dontwarn bcel); o CI do fechamento D6 ficou verde com as regras
  finais.

## v0.1.15 — AUDITORIA E CORREÇÃO da v0.1.14 (nenhuma funcionalidade nova)

### V1 — APK release de 1,5 MB é LEGÍTIMO (hipótese de R8 removendo classes REFUTADA)

| APK | classes.dex (descomprimido) | res/ | resources.arsc | APK (zip) |
|-----|------------------------------|------|----------------|-----------|
| v0.1.1-hotfix (fase 1) | 40,7 MB — **11.400 classes só de ícones Compose** | 24 KB | 415 KB | 10,8 MB |
| v0.1.13-fase2 | 2,50 MB | 1 KB | 91 KB | 1,4 MB |
| v0.1.14-fase2-teste | 2,85 MB | 1 KB | 91 KB | 1,5 MB |

- **Causa da queda 10,8 → 1,5 MB**: R8 + shrinkResources ATIVADOS na v0.1.3
  (commit `perf(v0.1.3)`, proguard-rules.pro) — o dex cru de 40,7 MB era
  biblioteca inteira não minificada (o pacote `androidx.compose.material.icons`
  sozinho tinha 11.400 classes). A queda ACONTECEU na v0.1.3, não na v0.1.14;
  o crescimento v0.1.13→v0.1.14 (+165 KB zip) é o código novo dos importadores
  e do .KIMG — legítimo.
- **Auditoria do dex da v0.1.14** (dexdump/apkanalyzer): 3.441 classes; TODAS
  as 22 subpackages `com.kenkern.engine.*` presentes, por nome (GltfImporter,
  FbxImporter, ModelImporter, KimgReader/Writer, Etc2, ScriptRuntime,
  LuaJScriptRuntime, KenkernLog, telas Compose). LuaJ está no dex como `a2.*`
  (141 classes — mapping.txt). ZERO reflexão na engine (grep: Class.forName,
  getMethod, newInstance, ServiceLoader, Parcelable). **Nenhuma regra -keep
  nova era necessária** — provas registradas no cabeçalho de proguard-rules.pro.
- **Guarda contínua**: checagem "release" NOVA no Diagnóstico (14 checagens) —
  `Class.forName` das 9 classes críticas + operação mínima de cada subsistema
  (glTF, FBX, .KIMG, ETC2, Lua). A mesma lógica é teste JVM
  (ChecagemReleaseTest). Se uma regra R8 futura remover classe que o release
  usa, aparece FALHOU no aparelho.
- Testes JVM: 3 novos (total 126 na V1).

### V2 — Compressão medida em MODELOS REAIS (Khronos glTF-Sample-Assets; nada sintético)

| Modelo (real, livre) | Origem | .KERN | .KIMG cru | .KIMG+Deflate | Total cru | Total+Deflate | Razão cru | Razão+Deflate | PSNR médio |
|---|---|---|---|---|---|---|---|---|---|
| DamagedHelmet (GLB) | 3,6 MB | 0,44 MB | 5,46 MB | 2,47 MB (−54,7%) | 5,8 MB | 2,8 MB | 0,62× | 1,26× | 39,2 dB |
| FlightHelmet (glTF+bin+15 PNG) | 46,2 MB | 1,87 MB | 23,9 MB | 9,26 MB (−61,2%) | 25,2 MB | 10,9 MB | 1,83× | 4,25× | 35,3 dB |
| Sponza (glTF+bin+68 JPEG/PNG) | 50,2 MB | 7,43 MB | 32,1 MB | 20,0 MB (−37,7%) | 38,6 MB | 26,8 MB | 1,30× | 1,88× | 30,6 dB |
| BrainStem (GLB, esqueleto+animação) | 3,0 MB | 1,14 MB | 0 | 0 | 1,1 MB | 1,1 MB | 2,74× | 2,74× | — |

- Geometria: DamagedHelmet 14.556→14.356 vértices (dedup), FlightHelmet
  55.392→55.392, Sponza 192.496→184.402 (dedup). Triângulos: a contagem
  "depois" do .KERN inclui os LODs 1 e 2 no MESMO buffer de índices
  (DamagedHelmet 15.452→18.109, FlightHelmet 94.722→97.109, Sponza
  262.267→264.856 — LOD0 fica fiel à origem). Erro máximo de quantização =
  0,50 passo em TODOS (tolerância ≤ 1 passo — OK). Importação: 211–1.313 ms;
  pico de memória 45–267 MB (medido pela própria pipeline).
- **MEMÓRIA DE GPU (medido, é onde a compressão realmente melhora)**: ETC2
  5,3 / 23,3 / 31,3 MB vs RGBA8 equivalente 42,7 / 181,3 / 234,7 MB —
  **economia de 87% em todos** (soma da cadeia de mips). A compressão EM DISCO
  é uma segunda vantagem, menor.
- **Camada de compressão em disco do .KIMG ADOTADA por padrão** (V2.4,
  DECISIONS #22): formato versionado (v1 sem camada, byte-idêntico; v2 com
  flag bit3 no header), Deflate do JDK por ARQUIVO (só grava v2 se o total
  ficar menor), nível 6 (nível 9 custa 1,6–2,7× o tempo por ≤0,5 pp; medido
  nas texturas reais), inflar na carga = 8–16 ms por textura 2048², limite
  HARD de inflação e erro claro em truncado/corrompido. 4 testes JVM novos.
- **A meta 5× NÃO foi atingida** (razões reais: 1,26× / 4,25× / 1,88× / 2,74×).
  A origem JPEG/PNG já é comprimida e o .KIMG guarda mais dados (mipmaps
  completos + fallback RGBA8 + LODs). Alavancas que faltam (estimativa sobre
  os números medidos): (1) manter JPEG de origem em disco e comprimir só na
  carga — Sponza voltaria a >3×; (2) decimação do LOD0 — .KERN cai 40–60%;
  (3) índices u16 com remap por parte — Sponza (184 mil vértices → u32 hoje)
  economiza ~50% dos índices; (4) não guardar o fallback RGBA8 no arquivo
  (regenerar na carga) — −33% no .KIMG quando presente.
- Harness REPRODUTÍVEL: `ModeloRealMedicaoTest` (JVM) — roda com
  `-Dkenkern.modelos.dir=<pasta>`; sem a pasta, o teste é IGNORADO no CI
  (os modelos reais não vão para o git).

### V3 — Honestidade do estado (Fase 2 CONTINUA ABERTA)

- **A Fase 2 NÃO está fechada.** Faltam (BACKLOG): F2.11 streaming
  progressivo anti-trave, F2.12 navegador de arquivos do projeto, F2.13
  pré-visualização de assets, F2.14 prefabs, F2.15 registro de assets com ids
  estáveis + referência quebrada. ROADMAP/PROGRESS/BACKLOG corrigidos; os
  itens que dependem do aparelho permanecem **COMPILADO** (não VALIDADO).
- **ASTC**: o Diagnóstico SÓ detecta a extensão (`astc`, informativo). O
  encoder ASTC NÃO EXISTE — pendência registrada no BACKLOG (fase futura);
  nenhum texto promete ASTC.
- **Limitações dos importadores agora aparecem no ImportReport** (antes só no
  logcat): glTF com `skins`/`animations` = aviso "ignoradas (Fase 8)";
  `extensionsUsed` declaradas = aviso listando os nomes; `extensionsRequired`
  = ERRO CLARO com o nome (spec glTF: leitor sem suporte não pode fingir
  sucesso); texturas emissive/occlusion/metallicRoughness do glTF = aviso
  (o .KERN v2 só leva baseColor + normal); FBX com Video sem Content
  (procedural/externa) = aviso. Reconhecido: baseColorTexture e
  normalTexture (glTF), DiffuseColor + Video embutido (FBX binário e ASCII).
  3 testes JVM novos (total 134).
- **Inconsistências corrigidas nos docs**: PROGRESS dizia "fechamento
  CONCLUÍDO" e ainda listava F2.9 como "TAREFA ATUAL"; contagens de testes
  divergentes (82 vs 123); v0.1.13 versionCode errado.

## ETAPA C — F2.9 formato .KIMG (imagem leve com compressão de GPU)

- **.KIMG** (`assets/image/KimgFormat.kt`): magic "KIMG", versão, formato
  (ETC2_RGB, ETC2_RGBA, ASTC_4x4/6x6/8x8 reservado, RGBA8_RAW), largura/altura,
  contagem de mipmaps, flags (sRGB, alfa, normal map) e TABELA de mipmaps
  (offset+tamanho) — sem metadados desnecessários. Leitor valida TODOS os
  limites (truncado/magic/versão/dimensões/mip fora do arquivo = ERRO CLARO,
  nunca estoura buffer).
- **ETC2 em Kotlin puro** (`assets/image/Etc2.kt`): encoder com os 5 modos da
  spec (individual, diferencial, T, H, planar — stuffs exatos do ETCPACK) e
  DECODER completo (usado no fallback RGBA8, nos testes e na checagem `etc2`).
  Qualidade medida: 52.9 dB (cor sólida) · 38.5 dB (gradiente) · 33.5 dB
  (senoides) · 52.9 dB (EAC alfa) — razão 5.8× (RGB) / 2.9× (RGBA).
  Encoder SEMPRE em background com cancelamento e progresso por mipmap.
- **Pipeline** (`KimgPipeline.kt`): RGBA8888 → (redimensiona para POT ≤
  GL_MAX_TEXTURE_SIZE quando excede) → mipmaps por CAIXA → ETC2 por mip →
  .KIMG + relatório (tamanho origem, tamanho .KIMG, razão, PSNR, tempo).
  Fallback RGBA8_RAW sem compressão quando a GPU recusa ETC2.
- **GPU** (`GlesBackend`): vertex format ganhou UV (stride 24→32, location 2);
  shader principal amostra a textura de cor base (fallback = cor sólida);
  `glCompressedTexImage2D` (GL_COMPRESSED_RGB8_ETC2 / RGBA8_EAC) por mip com
  filtragem TRILINEAR, wrap REPEAT e anisotropia (GL_EXT quando disponível);
  driver recusou ETC2 → decodifica na CPU e sobe RGBA8 (textura nunca some).
- **Render por grupos de material**: Nicks com classe "Malha" cujo .KERN v2 tem
  grupos desenham UM sub-range por grupo com a textura do material
  (`TextureLibrary` — carga .KIMG fora do flush, orçamento 6 ms/frame, erro
  claro 1× por caminho); geometria sem material continua cor sólida.
- **Importação**: texturas de glTF (embutidas no GLB, data URI, pasta SAF,
  .zip) e FBX (Video com Content embutido) são coletadas com hash SHA-1 e
  convertidas para `assets/textures/<sha1>.kimg` durante a importação
  (android.graphics decodifica PNG/JPEG em background). CACHE por hash +
  versão do formato: reabrir o projeto NÃO reconverte (C.5).
- **Diagnóstico**: 3 checagens novas — `kimg` (ida e volta + mipmaps + erros
  claros), `etc2` (upload GL cru + LEITURA DE PIXEL e comparação com o decoder
  de referência), `astc` (detecta a extensão, SUPORTADO/NÃO SUPORTADO, nunca
  reprova) — 12 checagens no total.
- **DECISIONS #21** registra as decisões e a qualidade medida.
- Testes JVM: 13 novos (KimgTest) — total 123, verde.

## ETAPA B — Correção dos importadores (fechamento da Fase 2)

- **FBX ASCII (B.2)**: parser de TEXTO com a mesma saída do binário — mesma
  árvore de nodes, mesmos arrays (contagem `*N` + bloco `a:`), mappings
  Direct/IndexToDirect, TRS em graus, hierarquia por Connections. Nomes
  "Tipo::Nome" viram o nome amigável. Erro de versão com a versão ENCONTRADA.
- **Materiais no .KERN v2 (B.3)**: formato .KERN passa a 2 (leitor aceita 1 e 2;
  flag HAS_MATERIALS) com tabela de materiais (nome, baseColor RGBA, metallic,
  roughness, flags, caminhos de textura base/normal) e GRUPOS por submalha
  (offset/count/índice de material) — glTF por primitiva, FBX por Model.
  Reserva de skinning intacta; esqueleto = aviso no relatório.
- **Dedup (B.4)**: vértices com posição+normal+UV idênticos fundidos (chave
  exata por bits, 2 passadas SEM mutação — correção de bug de aliasing que
  corrompia as chaves); materiais iguais dedupados; erro pós-quantização ≤ 1 passo.
- **LODs (B.5)**: 3 níveis por agrupamento de vértices em grade (células
  maiores por nível, triângulos degenerados descartados), anexados ao mesmo
  buffer de índices com offset/count e AABB própria por LOD; KernIntegrity valida.
- **B.6**: tangentes só são lidas quando há normal map (ou opção "manter");
  sem uso = zeradas. UV2/cores de vértice nunca entram (não são lidas).
- **B.7**: regressão de rotação do gizmo/Spin/Bob — quaternion e Mat4.setTRS
  preservam o comprimento dos vetores em 0/30/90/180/270°, composição
  pai×filho correta, euler ida e volta nos ângulos-chave.
- Diagnóstico: checagem `fbx` agora valida FBX ASCII completo + material.
- Testes JVM: 18 novos (FbxImporterTest +3, PosProcessamentoTest 8,
  GizmoSpinBobRegressionTest 7) — total 111, verde.

## ETAPA A — Botão "Importar modelo" na TopBar (fechamento da Fase 2)

- **Importar modelo (SAF)**: botão novo na TopBar (ícone Importar, 40dp,
  contentDescription) com menu "Arquivo (.glb, .gltf, .fbx, .zip)" e
  "Pasta (glTF com .bin e texturas)" (ACTION_OPEN_DOCUMENT_TREE). Importação
  RODA EM BACKGROUND (Dispatchers.Default/IO) com progresso por etapas
  ("lendo", "triangulando", "gravando .KERN", "validando") e botão CANCELAR —
  nunca trava a UI nem a thread GL.
- **Pipeline**: origem copiada em STREAMING (nunca 2× na memória; limite duro
  500 MB) para `assets/models/origem/`; .KERN gravado em `assets/models/`
  (nome único — colisão nunca sobrescreve); Nick criado com a classe "Malha"
  (path + AABB + escalaFit), filho do selecionado ou da raiz do Mundo; opção
  "Ajustar ao tamanho (~2 m)" aplica a escala NO Nick (o .KERN fica fiel).
- **UM Command no Undo/Redo** (`ImportModelCommand`): desfazer remove o Nick e
  recolhe os assets para `assets/.lixeira-import/`; refazer devolve tudo.
- **Relatório** (diálogo + "Copiar relatório"): tamanho antes/depois e razão,
  vértices/triângulos/LODs, materiais, avisos (skipping de esqueleto),
  tempo, pico de memória e resultado do KernIntegrity. Arquivo > 200 MB pede
  confirmação; erro sempre CLARO (nome + causa + o que fazer) na tela e no Console.
- **Render da malha importada**: `AssetMeshLibrary` carrega os .KERN usados
  pela cena FORA do flush (fila com orçamento de 6 ms por frame — anti-trave);
  picking e Focar passam a usar a AABB REAL da malha (classe "Malha"), não só
  o cubo de 1 m.
- **glTF externo**: .bin e texturas resolvidos contra a pasta SAF ou o conteúdo
  do .zip (resolver com bloqueio de zip-slip e caminhos com ".." recusados com
  erro claro).
- **Diagnóstico**: checagem nova "importar" (pipeline completa com modelo
  embutido + nome único + undo/redo do Command + glTF externo em .zip).
- Testes JVM: 15 novos (ModelImporterTest) — total da suíte: 96, verde.

## F2.8 — Importador FBX → .KERN (sem APK novo ainda)

- **Importador FBX binário 7.1–7.7** (`assets/import/FbxImporter.kt`): header
  "Kaydara FBX Binary" + versão u32; records de node com offsets u32 (< 7500) ou
  u64 (≥ 7500); propriedades i16/bool/i32/f32/f64/i64, strings (nomes "A::B"
  viram "B") e arrays 'd'/'i'/'f'/'l'/'b' com encoding 0 (raw) e 1 (zlib/Inflater
  com validação de tamanho). Footer ignorado. Limites de profundidade/contagem
  e validação de endOffset/propsLen contra corrupção — erro CLARO, nunca
  silêncio (regra #17; o arquivo não tem UM runCatching).
- **Malha**: Vertices + PolygonVertexIndex (fim de polígono = índice negativo,
  `idx.inv()`); polígonos de N cantos triangulados em LEQUE (quad/ngon → 2+n-3
  triângulos, preservando winding); vértices EXPANDIDOS por canto (hard edges
  corretos nativamente; dedup fica para a pipeline de compressão).
- **Normais/UV**: primeira camada de LayerElementNormal/UV (3 e 2 componentes;
  camadas extras = AVISO no KenkernLog); mappings ByPolygonVertex/ByVertice/
  ByPolygon com Direct e IndexToDirect; normais ausentes = acumulação suave por
  face (mesma matemática do glTF); UV convertida para a convenção glTF
  (v' = 1 − v — DECISIONS #20).
- **Hierarquia/instâncias**: Connections OO monta pais/filhos de Model;
  mundo = pai × TRS local (Lcl Translation/Rotation em GRAUS/Scaling) × TRS
  geométrico (Geometric T/R/S); geometria compartilhada por 2+ Models =
  instâncias fundidas (offset de índices); PreRotation/PostRotation presentes =
  AVISO no KenkernLog (malha estática não usa; rigs são da Fase 8).
- **Saída idêntica ao glTF**: KernWriter.Builder → .KERN (quantização u16 +
  octaédrica 8:8) + KernIntegrity (roundtrip real) + ImportReport reutilizado
  (geometrias×instâncias no lugar de primitivas).
- **Fora de escopo com erro claro**: FBX ASCII/texto (pede binário), FBX 6.x/5.x
  (pede conversão), polígono aberto, índice fora do range.
- **Diagnóstico**: checagem nova `fbx` (quad + cubo compartilhado por 2 Models +
  erro claro) — 9 checagens agora.
- Testes JVM novos: FbxImporterTest (15) + FbxAmostras (mini-escritor de FBX
  binário sintético — quad, ngon, ByVertice, sem normais, zlib, cubo com 2
  Models, v7500, ASCII, 6.x, polígono aberto). Suíte: 67 → 82 testes, verde.
  `assembleRelease` verde.

## F2.7 — Importador glTF/GLB → .KERN (app v0.1.13, sem APK novo ainda)

- **Importador glTF 2.0 completo** (`assets/import`): GLB (chunks JSON+BIN com
  magic/offsets/limites validados) e .gltf (buffers em data URI; buffer externo
  = erro CLARO com orientação). POSITION/NORMAL/TEXCOORD_0/índices nos
  componentTypes i8/u8/i16/u16/u32/f32; só primitivas TRIANGLES (mode≠4 = erro
  claro). Transform dos nodes (matriz column-major OU TRS/quaternion, herança
  de pais) aplicado aos vértices; NORMAL pela matriz 3×3 renormalizada; NORMAL
  ausente = acumulação suave de faces. Todas as primitivas da cena padrão
  fundidas em 1 KernMesh; saída SEMPRE pelo KernWriter (quantização u16 +
  octaédrica 8:8).
- **Meta de compressão**: `ImportReport` (bytes origem → bytes .KERN, razão,
  vértices/triângulos/primitivas/nodes/lods, flag da meta do roadmap 100 MB →
  ≤ 20 MB) — a meta de 5× é da pipeline completa (dedup/LODs/índices, F2.8+);
  mesh denso já sai ~1,3× menor.
- **Teste de integridade** (`KernIntegrity`): roundtrip pelo KernReader REAL —
  contagens, índices no range e idênticos, AABB do header, erro máximo de
  posição pós-dequantização medido em PASSOS de quantização, tabela de LODs.
- **FIX CRÍTICO da Fase 1 achado pelo importador**: rotação de quaternion com
  meia amplitude — `Quaternion.toMatrix` e `Mat4.setTRS` tinham os produtos com
  `w` SEM o fator 2 (90° em Y mapeava (0,0,1) para comprimento 0,5). Todo Nick
  girando (Spin/Bob), o gizmo e o torus entortavam/encolhiam em vez de girar.
  Play nunca tinha sido validado no aparelho — por isso invisível.
  Fix + `QuaternionMatrixRegressionTest` (REGRESSION.md).
- **Diagnóstico**: checagem nova `gltf` (gera GLB na memória → importa → valida
  integridade + meta + erro claro) — 8 checagens agora.
- Testes JVM novos: GltfImporterTest (10), QuaternionMatrixRegressionTest (5),
  KenkernLogTest (5). Suíte: 37 → 67 testes, verde.
- Dependência só de TESTE: `org.json:json` (mesma API nativa do Android; não
  entra no APK).

## Realinhamento ao ROADMAP (2026-11-21 — sem APK novo)

- **Tag `fase-2-ok` REMOVIDA** (local, remote e referências): foi criada sem
  cumprir a Fase 2 do roadmap. A Release v0.1.13 no GitHub foi re-titulada
  "v0.1.13 — build da Fase 2 (fase ABERTA; itens de assets pendentes)" — o APK
  fica (o build existe), só a alegação de fase fechada sai. A Fase 2 volta a
  EM CURSO com os pendentes reais: importador glTF/GLB/FBX → .KERN (meta 100 MB
  → ≤ 20 MB + teste de integridade), .KIMG (ASTC/ETC2), cache, streaming
  progressivo anti-trave, navegador de arquivos, pré-visualização, prefabs e
  registro de assets (ids estáveis + referência quebrada).
- **Tag `fase-1-ok` mantida**, mas os itens que dependem do aparelho (seleção
  por toque, Play real, paisagem automática, gizmos) ficam **COMPILADO**, não
  VALIDADO, até o usuário executar o aceite em TESTES_PENDENTES.md.
- **ROADMAP.md renumerado**: LUA = Fase 5 (não 3); Fase 3 = LENTE + LUME +
  colisão + input; importador/.KIMG/cache/streaming/navegador/preview/prefabs/
  registro de assets = Fase 2. BACKLOG.md renumerado 1:1 (F5.1 = LuaJ
  antecipado, congelado; proibido crescer Lua antes da Fase 5).
- **Regras novas de processo** (DECISIONS #16/#17): tag `fase-N-ok` só com
  TODOS os itens de aceite implementados; nenhum `runCatching` engole erro em
  silêncio (Log + Console `KenkernLog` + tela Diagnóstico).
- **Auditoria runCatching**: 62 ocorrências mapeadas (docs/RUNCATCHING_AUDIT.md);
  32 silenciosas nas classes críticas (save silencioso, cena ilegível → vazia,
  OOM de VBO, picking/resize sem rastro…) corrigidas com Console.
- **FIX docs**: KERN_SPEC.md tabela de LODs corrigida (20 → 32 bytes por
  entrada, como no código desde o fix F2.2).
- Testes JVM: KenkernLogTest novo (anel + sem dependência de android.util.Log);
  suíte segue verde.

## v0.1.13 — build da Fase 2 (fase ABERTA; tag `fase-2-ok` foi removida)

- **v0.1.13 (F2.6)** — .KERNPACK: importar/exportar projeto completo via SAF
  (zip com a estrutura de pastas; proteção zip-slip; zero permissões).
  Duplicar projeto no Hub. Pastas `ui/` e `worlds/` (criação + migração de
  projetos antigos). ENGINE_VERSION de fonte única (BuildConfig).
- **v0.1.13 (F2.3)** — Tela **Diagnóstico** no Hub (ícone do inseto): 7
  checagens APROVADO/FALHOU com motivo (.ck, .KERN, picking, shaders, buffers,
  capacidades GL, memória) — contexto EGL pbuffer offscreen próprio.
- **FIXES da Fase 1 desenterrados pelos testes de ida e volta (F2.1/F2.2)**:
  1. `.ck` corrompia strings com aspas/quebras de linha (unescape faltando);
  2. `.KERN` nunca funcionou de verdade: LOD_ENTRY_SIZE errado (20→32) fazia
     o escritor estourar, e o leitor lia o header desalinhado em 14 bytes —
     o torus de teste nunca renderizou (silenciado pelo runCatching).
- Testes JVM novos: CkFormatRoundtripTest (10), KernRoundtripTest (8),
  ProjectManagerDuplicateTest (3), KernpackTest (5).

## fase-1-ok — Fundação (app v0.1.12)

- **v0.1.12** — Olhar horizontal do modo Voo corrigido: a base da câmera lia o
  yaw do ORBITAL no ramo FLIGHT (`flightYawDegrees` nunca era usado). Overlay do
  voo ganha Δ dx/dy em dp do último MOVE. 4 testes JVM novos
  (`FlightLookRegressionTest`), suíte verde.
- **v0.1.11** — Zoom orbital "preso" corrigido (dedo morto reancorado no
  `ACTION_POINTER_UP`); olhar de 2 dedos no Voo (0,25°/dp, clamp ±89°, sem
  roll); overlay da pinça (modo/distância/spans/fator/reset).
- **v0.1.10** — Diagnóstico visível de seleção/gizmo (overlay de toque),
  correções de picking (raio do orbital apontava para trás; hit-test do gizmo
  projetava pelo MODEL em vez do MVP).
- **v0.1.9** — NPE do TransformGizmo corrigido (ordem de inicialização);
  diálogo de crash com stack trace + BootTrace de 6 etapas
  (`filesDir/boot_trace.txt`), layout corrigido em paisagem.
- **v0.1.0–v0.1.8** — Fundação: editor GLES 3.0 (grade infinita, céu, luz
  direcional), Hub de projetos com pastas e `project.kenkern`, `.ck` CK1,
  `.KERN` binário (quantização/LODs/bones), seleção por toque, gizmo de
  transformação, câmera orbital + voo, undo/redo, Play/Stop com timestep fixo,
  classes nativas Spin/Bob, LINKERF (modelo + serialização), autosave.

Formato do changelog: entrada por versão entregue (APK no CI). A tag `fase-N-ok`
só fecha a fase quando TODOS os itens de aceite estiverem implementados
(DECISIONS #16); itens de aparelho só viram VALIDADO após confirmação do usuário.
