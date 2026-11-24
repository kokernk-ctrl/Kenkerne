# KENKERN — NICKS (catálogo vivo)

Catálogo de TODOS os tipos de Nick da engine. Status por tipo:
- `PLANEJADO` — só existe aqui (a janela "Adicionar Nick" NÃO lista);
- `IMPLEMENTADO` — preset registrado no `NickKindRegistry` e listado em
  "Adicionar Nick" (CI verde; sem validação no aparelho). Isso NÃO significa
  que a função de jogo descrita esteja implementada em runtime.

- `VALIDADO` — testado pelo usuário no aparelho.

Como ler as descrições: a coluna **Papel do tipo** resume a finalidade/preset
previsto; não é prova de comportamento existente no jogo. Registro, rótulo,
classes padrão e presença em "Adicionar Nick" são dados de editor. Só marque
uma função como runtime implementada quando houver execução correspondente e
teste/validação próprios — não pelo nome ou descrição do tipo.

Regras (DECISIONS #24–#28):
- Tipo de Nick = preset de DADOS no `NickKindRegistry` (chave estável em inglês,
  rótulos en/pt, ícone, grupo, classes padrão, filhos padrão) — NÃO existe uma
  classe de código por tipo.
- O `.ck` (CK2) guarda a CHAVE (ex.: `body.player`), nunca o rótulo.
- `name` nulo = exibe o rótulo do tipo no idioma atual; renomeação do usuário é
  gravada e nunca traduzida (DECISIONS #26).
- A janela "Adicionar Nick" lista SOMENTE tipos IMPLEMENTADOS.
- Nicks de UI (2D) vivem na árvore própria do TELAR (DECISIONS #29).

**Auditoria do catálogo:** 19 tipos registrados
(3 base, 3 luz, 2 câmeras, 7 corpos, 3 áreas/sinais, 1 UI — ui.joystick);
o restante abaixo é planejado e não aparece em "Adicionar Nick". Desde o S2
(v0.1.24) TODOS os 5 kinds de corpo do primeiro recorte (static/rigid/
collider/character/player) têm a classe padrão `Colisor`; luzes têm `Luz`,
câmeras têm `Lente`, joystick tem `Joystick`. As classes/funções de Nick são
um mecanismo separado dos tipos: o registro do tipo não associa
automaticamente input, física, IA, áudio, etc.

### Tabela S4 (v0.1.24) — TODO kind implementado: viewport, classes, Play

Fonte única testável: `KindEmPlay` (core) — `NickKindTabelaTest` cria um Nick
de CADA kind e confere as três colunas abaixo (a mesma tabela é a prova de
regressão do S4).

| Chave | Viewport (editor) | Classes padrão | Em Play |
|-------|--------------------|----------------|---------|
| nick.empty | auxiliar LOSANGO | — | nada — só transform/filhos/classes |
| nick.group | container (não desenha) | — | nada — só estrutura |
| nick.mesh | malha importada ou cubo | — | desenha a malha |
| body.static | cubo × escala | Colisor | BLOQUEIA (estático; volume do Colisor) |
| body.rigid | cubo | Colisor | SIMULA: gravidade + colisão |
| body.collider | cubo | Colisor | SIMULA: gravidade + colisão — e bloqueia os outros |
| body.character | cubo | Colisor | SIMULA: gravidade + colisão (sem input) |
| body.player | cubo | Colisor | SIMULA: gravidade + input (mover/pular) + colisão; nasce no spawn; câmera segue |
| body.enemy | cubo | — | NÃO simulado (IA pendente) |
| body.npc | cubo | — | NÃO simulado (diálogo pendente) |
| area.volume | auxiliar volume em arame | — | GATILHO on_trigger 1× na entrada do jogador |
| area.spawn | auxiliar LOSANGO | — | SPAWN: teleporta o jogador ao iniciar |
| area.signal | auxiliar | — | NÃO simulado (LINKERF pendente) |
| camera.basic | auxiliar PIRÂMIDE | Lente | câmera de Play pela Lente (principal vence) |
| camera.follow | auxiliar PIRÂMIDE | Lente | segue o jogador (clamp do chão) |
| light.directional | auxiliar SETA | Luz | ILUMINA: substitui o sol (editor e Play) |
| light.point | auxiliar ESFERA + alcance real | Luz | ILUMINA: ≤4 pontuais/spot por frame |
| light.spot | auxiliar CONE + ângulo/alcance reais | Luz | ILUMINA: cone + alcance reais |
| ui.joystick | MOLDURA 2D de tela (S4) — NUNCA malha 3D/cubo | Joystick | INPUT: joystick de tela (tipo/tamanho/zona morta/8 direções; Controla) |

S4b (v0.1.24): Nicks ui.* são ELEMENTOS 2D DE TELA
(`MalhaNick.ehKindElemento2D`): NUNCA desenham malha 3D, cubo nem auxiliar
3D — no editor aparecem como MOLDURA 2D no viewport (tamanho da classe
Joystick); em Play viram controles reais. (Antes do S4 o ui.joystick folha
desenhava o CUBO 3D — `MalhaNick.desenhavel` não cobria `ui.*`.)

S4c (v0.1.24) — luz no editor: editor e Play compartilham o MESMO pipeline
(`EngineRenderer.renderFrame` → `LumeCena.coletar` por frame → setLighting/
setDynamicLights, sem guarda de `emJogo`). A checagem "luz" do Diagnóstico
renderiza pelo MESMO `renderFrame` (offscreen): verde nela = a luz do Nick
ilumina o EDITOR também.

### Primeiro recorte do Marco Jogável (M1)

Em **Play**, a cena é uma cópia descartável. O primeiro jogador ativo usa o
primeiro ponto de surgimento ativo, se houver. O joystick de teste move esse
jogador em X/Z; gravidade e colisão com corpos estáticos e colisores caixa
mantêm-no sobre o chão e bloqueiam paredes. A câmera básica usa a posição
e a rotação configuradas; a câmera seguidora, se presente, acompanha o jogador
a partir do deslocamento inicial. Sem câmera criada, há uma vista seguidora
padrão para o jogador. **Stop** volta à cena e à câmera do editor.

Este recorte não inclui pulo, rampas, física de corpos rígidos, formas de
colisão além de caixas, ligação de eventos, múltiplos jogadores nem Joystick
Nick configurável. Iluminação por Nick continua reservada ao LUME.

AUXILIARES DE EDITOR (E5, v0.1.18): todo Nick SEM malha própria (luz, câmera,
área, sinal, surgimento e o Nick Vazio) é visível no viewport como um
AUXILIAR desenhado SÓ no editor (nunca em Play, nunca no jogo exportado):
tamanho constante na tela, cor por grupo, seleção por toque (tolerância 24dp)
e âmbar quando selecionado. Formas: luz direcional = SETA na direção do Nick;
luz pontual = ESFERA com anel de alcance (simbólico até a classe de luz do
LUME); luz de foco = CONE; câmera = PIRÂMIDE com o FOV da câmera da engine
(o campo FOV no Nick entra com a LENTE); área = volume em ARAME (caixa 1×1×1);
demais tipos = LOSANGO. Botão "Mostrar auxiliares" na TopBar (padrão ligado).
Corpos (body.*) continuam com o CUBO de 1 m como geometria placeholder até o
M1/FORJA darem forma própria. Auxiliares NÃO contam como malha na StatusBar
("N nicks · M malhas" — E6).

## BASE

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| nick.empty | Empty Nick | Nick Vazio | Nó genérico sem comportamento próprio; guarda transform, filhos e classes. | Base | IMPLEMENTADO (T0.2; auxiliar LOSANGO no E5 — não desenha mais cubo) | T0.2 |
| nick.group | Group Nick | Nick de Grupo | Container organizador de filhos, sem geometria própria. | Base | IMPLEMENTADO (T0.2) | T0.2 |
| nick.mesh | Mesh Nick | Nick de Malha | Representa geometria por malha importada ou cubo embutido. | Base | IMPLEMENTADO (T0.2) | T0.2 |
| nick.prefab | Prefab Nick | Nick de Prefab | Instância reutilizável de um conjunto de Nicks, com seus filhos. | Base | PLANEJADO | F2.14 (prefabs) |

## LUZ (LUME)

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| light.directional | Directional Light Nick | Nick de Luz Direcional | Fonte de luz paralela, como sol; direção, sem posição efetiva. | Luz | IMPLEMENTADO (T0.2; auxiliar SETA no E5; **H2 v0.1.20: ILUMINA — substitui o sol fixo; Inspetor cor/intensidade/ligado**) | T0.2 |
| light.point | Point Light Nick | Nick de Luz Pontual | Fonte luminosa pontual com alcance ao redor da posição. | Luz | IMPLEMENTADO (T0.2; auxiliar ESFERA no E5; **H2 v0.1.20: ILUMINA — até 4/frame por contribuição, atenuação suave, esfera de alcance REAL; Inspetor cor/intensidade/alcance/ligado**) | T0.2 |
| light.spot | Spot Light Nick | Nick de Luz de Foco | Fonte pontual que projeta um cone de luz orientado. | Luz | IMPLEMENTADO (T0.2; auxiliar CONE no E5; **H2 v0.1.20: ILUMINA — cone com ângulo interno/externo e alcance REAIS; Inspetor cor/intensidade/alcance/ângulos/ligado**) | T0.2 |

**M1.2 (v0.1.21) — Área FUNCIONA como gatilho:** a area.volume dispara `on_trigger(nome)` UMA VEZ na ENTRADA do jogador em Play (sair e voltar dispara de novo); volume = Colisor da área (se houver) ou cubo de 1 m.

**H2 (v0.1.20) — a luz Nick AGORA ilumina a cena (LUME, editor e Play):**
classe "Luz" no Nick (cor VEC3, intensidade, alcance, ângulo interno/externo,
ligado — Inspetor en/pt; Nick antigo sem a classe usa os defaults do tipo).
A DIRECIONAL ativa e ligada SUBSTITUI o sol fixo (`DefaultLightingPipeline`
só entra sem nenhuma na cena); PONTUAL/SPOT: até **4 luzes ativas por frame**
(maior contribuição para a câmera = intensidade/(1+d²)), atenuação suave
(janela (1−(d/alcance)⁴)² × 1/(1+d²)), arrays primitivos reaproveitados
(LumeCena.Frame) e atualização NO MESMO frame. Sem sombras nesta fase.
Auxiliares de editor agora mostram alcance/ângulo REAIS (esfera de raio =
alcance; cone com a abertura externa real e altura = alcance). Checagem
"luz" no Diagnóstico (quad branco + luz vermelha offscreen: o pixel muda).
| light.probe | Light Probe Nick | Nick de Sonda de Luz | Amostra iluminação ambiente local para iluminar objetos próximos. | Luz | PLANEJADO | KELU |

## CÂMERA (LENTE; modos como propriedade: primeira/segunda/terceira pessoa, perspectiva, ortogonal, topo)

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| camera.basic | Camera Nick | Nick de Câmera | Câmera de jogo; projeção e modo de visão são propriedades configuráveis. | Câmera | IMPLEMENTADO (T0.2; pirâmide E5; **H3 v0.1.20: classe LENTE — fov/near/far/ortográfica+tamanho, "Câmera principal" (só uma por cena) + "Ver pela câmera" na TopBar com moldura; em Play, a principal renderiza com a matriz DO NICK**) | T0.2 |
| camera.follow | Follow Camera Nick | Nick de Câmera Seguidora | Câmera que acompanha o jogador, preservando o deslocamento inicial. | Câmera | IMPLEMENTADO (M1; **H3 v0.1.20: campos Alvo (ReferenceId), distância e altura na classe Lente, usados em Play; sem campos preenchidos mantém o deslocamento inicial**) | M1 |
| camera.cinematic | Cinematic Camera Nick | Nick de Câmera Cinemática | Câmera para tomadas/cenas dirigidas por animação ou timeline. | Câmera | PLANEJADO | anim.timeline |

## CORPOS

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| body.static | Static Body Nick | Nick de Corpo Estático | Corpo imóvel que serve de superfície ou obstáculo de colisão. | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.rigid | Rigid Body Nick | Nick de Corpo Rígido | Corpo movido pela simulação física (massa, gravidade e resposta a impactos). | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.kinematic | Kinematic Body Nick | Nick de Corpo Cinemático | Corpo movido pelo jogo/script, mas considerado nas colisões. | Corpos | PLANEJADO | M1 |
| body.collider | Collider Body Nick | Nick de Corpo Colisor | Define um volume de colisão sem, por si só, definir movimento físico. | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.character | Character Body Nick | Nick de Corpo de Personagem (andar, pular, rampas) | Corpo de personagem com movimento de andar, pular e lidar com rampas. | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.player | Player Body Nick | Nick de Corpo de Jogador (Character + input + câmera seguidora) | Preset de personagem controlado pelo jogador; prevê input e câmera seguidora. | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.enemy | Enemy Body Nick | Nick de Corpo Inimigo (Character + comportamento + vida e dano) | Preset de personagem inimigo; prevê comportamento, vida e dano. | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.npc | NPC Body Nick | Nick de Corpo NPC (Character + diálogo + rotinas) | Preset de personagem não jogável; prevê diálogo e rotinas. | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.car | Car Body Nick | Nick de Corpo de Carro | Corpo veicular para dirigir, com rodas, direção e física de veículo. | Corpos | PLANEJADO | RODANTE |
| body.bike | Bike Body Nick | Nick de Corpo de Moto | Corpo veicular de duas rodas, com equilíbrio e direção de moto. | Corpos | PLANEJADO | RODANTE |
| body.ragdoll | Ragdoll Body Nick | Nick de Corpo de Boneco Físico | Corpo articulado por juntas para simular queda e movimento físico. | Corpos | PLANEJADO | OSSATURA |

## ÁREAS E SINAIS (ZONA)

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| area.volume | Area Nick | Nick de Área (volume 3D de detecção) | Volume 3D que detecta entradas, saídas e presença de outros Nicks. | Áreas | IMPLEMENTADO (preset/editor; auxiliar CAIXA em arame no E5; detecção runtime prevista no M1) | T0.2 |
| area.signal | Signal Nick | Nick de Sinal (emite e recebe eventos nomeados, liga no LINKERF) | Emissor/receptor de eventos nomeados para conectar lógica no LINKERF. | Áreas | IMPLEMENTADO (preset/editor; auxiliar LOSANGO no E5; conexões no LINKERF são futuras) | T0.2 |
| area.spawn | Spawn Point Nick | Nick de Ponto de Surgimento | Marcador de posição e orientação onde objetos/personagens podem surgir. | Áreas | IMPLEMENTADO (preset/editor; auxiliar LOSANGO no E5; surgimento runtime previsto no M1) | T0.2 |
| area.checkpoint | Checkpoint Nick | Nick de Ponto de Controle | Marca de progresso/retorno para reinício após falha ou carregamento. | Áreas | PLANEJADO | M1 |
| area.navigation | Navigation Area Nick | Nick de Área de Navegação | Região que define espaço navegável e restrições de navegação da IA. | Áreas | PLANEJADO | MUNDO |

## IA E DIÁLOGO (MENTE, FALA)

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| ai.behavior | Behavior Nick | Nick de Comportamento | Comportamento de IA que decide ações a partir de estado e estímulos. | IA | PLANEJADO | MENTE |
| dialogue.dialogue | Dialogue Nick | Nick de Diálogo | Dados e fluxo de diálogo entre personagens, escolhas e eventos. | IA | PLANEJADO | FALA |

## ANIMAÇÃO (LINHA, OSSATURA)

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| anim.player | Animation Player Nick | Nick de Reprodutor de Animação | Reproduz, pausa e mistura clipes de animação em um Nick. | Animação | PLANEJADO | OSSATURA/LINHA |
| anim.body | Animation Body Nick | Nick de Corpo Animado (malha + esqueleto + clipes) | Vincula malha/esqueleto a clipes para animar um personagem ou objeto. | Animação | PLANEJADO | OSSATURA/LINHA |
| anim.skeleton | Skeleton Nick | Nick de Esqueleto | Hierarquia de ossos e juntas usada para deformar uma malha. | Animação | PLANEJADO | OSSATURA |
| anim.state | Animation State Nick | Nick de Estados de Animação | Máquina de estados que escolhe/transiciona animações conforme condições. | Animação | PLANEJADO | OSSATURA/LINHA |
| anim.parts | Part Animation Nick | Nick de Animação por Partes (cabelo, cauda, tecido, correntes) | Anima partes secundárias (cabelo, cauda, tecido, correntes etc.). | Animação | PLANEJADO | anim. por partes |
| anim.timeline | Timeline Nick | Nick de Linha do Tempo (cinemática) | Organiza eventos e faixas temporais para sequências/cinemáticas. | Animação | PLANEJADO | anim.timeline |

## AMBIENTE

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| env.environment | Environment Nick | Nick de Ambiente | Configura parâmetros globais de ambiente e renderização de uma cena. | Ambiente | PLANEJADO | MUNDO |
| env.sky | Sky Nick | Nick de Céu | Define aparência e iluminação do céu/cúpula celeste. | Ambiente | PLANEJADO | MUNDO |
| env.daynight | Day-Night Nick | Nick de Dia e Noite | Controla ciclo de tempo e transição de iluminação dia/noite. | Ambiente | PLANEJADO | MUNDO |
| env.weather | Weather Nick | Nick de Clima | Coordena condições climáticas e seus efeitos na cena. | Ambiente | PLANEJADO | MUNDO |
| env.fog | Fog Nick | Nick de Névoa | Define densidade, cor e alcance da névoa atmosférica. | Ambiente | PLANEJADO | MUNDO |
| env.wind | Wind Nick | Nick de Vento | Fornece direção e intensidade do vento a sistemas compatíveis. | Ambiente | PLANEJADO | MUNDO |
| env.water | Water Nick | Nick de Água | Define superfície e propriedades visuais/físicas da água. | Ambiente | PLANEJADO | MUNDO |
| env.terrain | Terrain Nick | Nick de Terreno | Cria/configura terreno com relevo, materiais e colisão. | Ambiente | PLANEJADO | MUNDO |
| env.vegetation | Vegetation Nick | Nick de Vegetação | Distribui vegetação sobre terreno ou regiões definidas. | Ambiente | PLANEJADO | MUNDO |
| env.procedural | Procedural World Nick | Nick de Mundo Procedural | Gera mundo/conteúdo a partir de parâmetros e regras procedurais. | Ambiente | PLANEJADO | MUNDO |

## EFEITOS

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| fx.particles | Particle Nick | Nick de Partículas | Emite partículas para efeitos como fumaça, fogo, faíscas ou poeira. | Efeitos | PLANEJADO | MUNDO |
| fx.decal | Decal Nick | Nick de Decalque | Projeta uma imagem/material sobre superfícies (marcas, detalhes). | Efeitos | PLANEJADO | MUNDO |
| fx.trail | Trail Nick | Nick de Rastro | Desenha rastro ao longo do movimento de um objeto. | Efeitos | PLANEJADO | MUNDO |

## ÁUDIO

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| audio.source | Audio Source Nick | Nick de Fonte de Áudio | Emite áudio posicional a partir de um ponto ou objeto da cena. | Áudio | PLANEJADO | áudio |
| audio.music | Music Nick | Nick de Música | Reproduz música/faixas não posicionais com controle de transições. | Áudio | PLANEJADO | áudio |
| audio.zone | Audio Zone Nick | Nick de Zona de Áudio | Aplica parâmetros de áudio conforme a posição do ouvinte na zona. | Áudio | PLANEJADO | áudio |

## MÍDIA

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| media.video | Video Nick | Nick de Vídeo | Reproduz vídeo como fonte de mídia na cena. | Mídia | PLANEJADO | vídeo/mídia |
| media.sprite | Sprite Nick | Nick de Sprite | Exibe imagem 2D como sprite, pertencente à árvore TELAR. | Mídia | PLANEJADO | TELAR |
| media.anim2d | 2D Animation Nick | Nick de Animação 2D | Reproduz animação 2D de sprites na árvore TELAR. | Mídia | PLANEJADO | TELAR |

## CURVAS (ROTA)

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| curve.mesh | Mesh Curve Nick | Nick de Curva de Malha | Curva-base para gerar ou orientar uma malha ao longo de um traçado. | Curvas | PLANEJADO | CURVAS |
| curve.path | Path Nick | Nick de Trajeto | Trajeto consultável para movimento ou seguimento por Nicks. | Curvas | PLANEJADO | CURVAS |
| curve.road | Road Nick | Nick de Estrada | Gera geometria de estrada ao longo de uma curva. | Curvas | PLANEJADO | CURVAS |
| curve.traffic | Traffic Nick | Nick de Tráfego | Define fluxo/rotas de tráfego associados a caminhos. | Curvas | PLANEJADO | CURVAS |

## LÓGICA

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| logic.linkerf | Linkerf Graph Nick | Nick de Grafo Linkerf | Grafo visual de nós e conexões para lógica sem script. | Lógica | PLANEJADO | LINKERF |
| logic.timer | Timer Nick | Nick de Temporizador | Dispara saída/evento após intervalo ou repetição configurados. | Lógica | PLANEJADO | LINKERF |
| logic.counter | Counter Nick | Nick de Contador | Conta eventos/valores e sinaliza limites ou condições. | Lógica | PLANEJADO | LINKERF |
| logic.spawner | Spawner Nick | Nick de Gerador | Cria instâncias sob demanda ou em ciclos durante o jogo. | Lógica | PLANEJADO | ciclo de vida |
| logic.save | Save Point Nick | Nick de Ponto de Salvamento | Guarda/restaura progresso e estado persistente do jogo. | Lógica | PLANEJADO | ciclo de vida |

## MUNDO

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| world.world | World Nick | Nick de Mundo | Raiz/descrição de um mundo que organiza cenas e conteúdo. | Mundo | PLANEJADO | MUNDOS |
| world.layer | Layer Nick | Nick de Camada | Agrupa conteúdo em camada para organização e controle de visibilidade/ativação. | Mundo | PLANEJADO | camadas |

## UI (2D — árvore própria do TELAR; nome: "UI X Nick" / "Nick de X de UI")

| Chave | Inglês | Português | Papel do tipo (previsto) | Grupo | Status | Entra em |
|-------|--------|-----------|-------------------------|-------|--------|----------|
| ui.screen | UI Screen Nick | Nick de Tela de UI | Raiz de uma tela/interface 2D na árvore própria TELAR. | UI | PLANEJADO | TELAR |
| ui.panel | UI Panel Nick | Nick de Painel de UI | Container visual para agrupar e organizar elementos de UI. | UI | PLANEJADO | TELAR |
| ui.text | UI Text Nick | Nick de Texto de UI | Exibe texto formatado na interface. | UI | PLANEJADO | TELAR |
| ui.image | UI Image Nick | Nick de Imagem de UI | Exibe imagem/textura na interface. | UI | PLANEJADO | TELAR |
| ui.button | UI Button Nick | Nick de Botão de UI | Elemento acionável que produz eventos de interação. | UI | PLANEJADO | TELAR |
| ui.video | UI Video Nick | Nick de Vídeo de UI | Reproduz vídeo dentro da interface. | UI | PLANEJADO | TELAR |
| ui.joystick | UI Joystick Nick | Nick de Joystick de UI | Controle de toque que produz eixos/ações de movimento e pode comandar um Nick. | UI | IMPLEMENTADO (M1.3, v0.1.22 — input de jogo; **S4 v0.1.24: no editor é MOLDURA 2D de tela — NUNCA malha 3D/cubo**) | M1.3 |
| ui.slider | UI Slider Nick | Nick de Controle Deslizante de UI | Controle deslizante para selecionar/editar um valor. | UI | PLANEJADO | TELAR |
| ui.progress | UI Progress Nick | Nick de Barra de Progresso de UI | Exibe progresso ou quantidade como barra/indicador. | UI | PLANEJADO | TELAR |
| ui.list | UI List Nick | Nick de Lista de UI | Apresenta uma lista de itens navegáveis/selecionáveis. | UI | PLANEJADO | TELAR |
| ui.dialogue | UI Dialogue Nick | Nick de Caixa de Diálogo de UI | Apresenta falas, opções e avanço de diálogos na interface. | UI | PLANEJADO | FALA |
| ui.touch | UI Touch Area Nick | Nick de Área de Toque de UI (tocar, deslizar, segurar) | Região de toque que reconhece toque, deslize e pressionamento. | UI | PLANEJADO | TELAR |

## Notas do joystick (ui.joystick — adendo de input de tela)

- Tipos: Fixo (sempre visível), Flutuante (aparece onde o dedo toca numa zona),
  Dinâmico (a base acompanha o dedo além do raio).
- Saídas: x e y de −1 a 1, força 0–1, ângulo, pressionado; eventos on_press,
  on_move, on_release. Multitoque com posse de dedo por controle; toque em dp;
  zero alocação por frame; nunca bloqueia a câmera do editor fora do Play.
- Sem script: campo "Controla" liga a um Nick (normalmente o Player Body Nick):
  movimento relativo à câmera ou ao mundo, velocidade, girar o personagem,
  ação do toque (pular/atacar) para botões; segundo joystick pode controlar
  câmera/olhar/mira. Modo "Seguir Nick" ancora a base na posição projetada do
  Nick na tela (vale também para rótulos, barras de vida e ícones).
- Com LINKERF: portas de saída (x, y, força, ângulo, pressionado) ligam a
  portas de entrada de qualquer Nick (modelo de dados das portas no M1.3;
  edição visual na fase do LINKERF).
- Com Lua: `input.joystick("nome")` devolve x, y e força (sem alocar tabela) +
  eventos por nome; exemplo no template "Coletar moedas".
- Ações de input nomeadas (mover, pular, atacar): joystick, botões de tela,
  teclado e controle Bluetooth ligam às ações num mapeamento editável; Lua e
  LINKERF leem AÇÕES, não dispositivos.
- Testes de JVM exigidos: normalização, zona morta, curva de resposta, travar
  em 8 direções, multitoque com dois dedos, projeção do Nick para a tela.
  Checagem "joystick" no Diagnóstico (injeta toques sintéticos e confere x/y).
