# KENKERN — NICKS (catálogo vivo)

Catálogo de TODOS os tipos de Nick da engine. Status por tipo:
- `PLANEJADO` — só existe aqui (a janela "Adicionar Nick" NÃO lista);
- `IMPLEMENTADO` — existe no `NickKindRegistry` e na janela "Adicionar Nick"
  (CI verde; sem validação no aparelho);
- `VALIDADO` — testado pelo usuário no aparelho.

Regras (DECISIONS #24–#28):
- Tipo de Nick = preset de DADOS no `NickKindRegistry` (chave estável em inglês,
  rótulos en/pt, ícone, grupo, classes padrão, filhos padrão) — NÃO existe uma
  classe de código por tipo.
- O `.ck` (CK2) guarda a CHAVE (ex.: `body.player`), nunca o rótulo.
- `name` nulo = exibe o rótulo do tipo no idioma atual; renomeação do usuário é
  gravada e nunca traduzida (DECISIONS #26).
- A janela "Adicionar Nick" lista SOMENTE tipos IMPLEMENTADOS.
- Nicks de UI (2D) vivem na árvore própria do TELAR (DECISIONS #29).

## BASE

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| nick.empty | Empty Nick | Nick Vazio | Base | IMPLEMENTADO (T0.2) | T0.2 |
| nick.group | Group Nick | Nick de Grupo | Base | IMPLEMENTADO (T0.2) | T0.2 |
| nick.mesh | Mesh Nick | Nick de Malha | Base | IMPLEMENTADO (T0.2) | T0.2 |
| nick.prefab | Prefab Nick | Nick de Prefab | Base | PLANEJADO | F2.14 (prefabs) |

## LUZ (LUME)

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| light.directional | Directional Light Nick | Nick de Luz Direcional | Luz | IMPLEMENTADO (T0.2) | T0.2 |
| light.point | Point Light Nick | Nick de Luz Pontual | Luz | IMPLEMENTADO (T0.2) | T0.2 |
| light.spot | Spot Light Nick | Nick de Luz de Foco | Luz | IMPLEMENTADO (T0.2) | T0.2 |
| light.probe | Light Probe Nick | Nick de Sonda de Luz | Luz | PLANEJADO | KELU |

## CÂMERA (LENTE; modos como propriedade: primeira/segunda/terceira pessoa, perspectiva, ortogonal, topo)

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| camera.basic | Camera Nick | Nick de Câmera | Câmera | IMPLEMENTADO (T0.2) | T0.2 |
| camera.follow | Follow Camera Nick | Nick de Câmera Seguidora | Câmera | PLANEJADO | M1 |
| camera.cinematic | Cinematic Camera Nick | Nick de Câmera Cinemática | Câmera | PLANEJADO | anim.timeline |

## CORPOS

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| body.static | Static Body Nick | Nick de Corpo Estático | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.rigid | Rigid Body Nick | Nick de Corpo Rígido | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.kinematic | Kinematic Body Nick | Nick de Corpo Cinemático | Corpos | PLANEJADO | M1 |
| body.collider | Collider Body Nick | Nick de Corpo Colisor | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.character | Character Body Nick | Nick de Corpo de Personagem (andar, pular, rampas) | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.player | Player Body Nick | Nick de Corpo de Jogador (Character + input + câmera seguidora) | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.enemy | Enemy Body Nick | Nick de Corpo Inimigo (Character + comportamento + vida e dano) | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.npc | NPC Body Nick | Nick de Corpo NPC (Character + diálogo + rotinas) | Corpos | IMPLEMENTADO (T0.2) | T0.2 |
| body.car | Car Body Nick | Nick de Corpo de Carro | Corpos | PLANEJADO | RODANTE |
| body.bike | Bike Body Nick | Nick de Corpo de Moto | Corpos | PLANEJADO | RODANTE |
| body.ragdoll | Ragdoll Body Nick | Nick de Corpo de Boneco Físico | Corpos | PLANEJADO | OSSATURA |

## ÁREAS E SINAIS (ZONA)

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| area.volume | Area Nick | Nick de Área (volume 3D de detecção) | Áreas | IMPLEMENTADO (T0.2) | T0.2 |
| area.signal | Signal Nick | Nick de Sinal (emite e recebe eventos nomeados, liga no LINKERF) | Áreas | IMPLEMENTADO (T0.2) | T0.2 |
| area.spawn | Spawn Point Nick | Nick de Ponto de Surgimento | Áreas | IMPLEMENTADO (T0.2) | T0.2 |
| area.checkpoint | Checkpoint Nick | Nick de Ponto de Controle | Áreas | PLANEJADO | M1 |
| area.navigation | Navigation Area Nick | Nick de Área de Navegação | Áreas | PLANEJADO | MUNDO |

## IA E DIÁLOGO (MENTE, FALA)

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| ai.behavior | Behavior Nick | Nick de Comportamento | IA | PLANEJADO | MENTE |
| dialogue.dialogue | Dialogue Nick | Nick de Diálogo | IA | PLANEJADO | FALA |

## ANIMAÇÃO (LINHA, OSSATURA)

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| anim.player | Animation Player Nick | Nick de Reprodutor de Animação | Animação | PLANEJADO | OSSATURA/LINHA |
| anim.body | Animation Body Nick | Nick de Corpo Animado (malha + esqueleto + clipes) | Animação | PLANEJADO | OSSATURA/LINHA |
| anim.skeleton | Skeleton Nick | Nick de Esqueleto | Animação | PLANEJADO | OSSATURA |
| anim.state | Animation State Nick | Nick de Estados de Animação | Animação | PLANEJADO | OSSATURA/LINHA |
| anim.parts | Part Animation Nick | Nick de Animação por Partes (cabelo, cauda, tecido, correntes) | Animação | PLANEJADO | anim. por partes |
| anim.timeline | Timeline Nick | Nick de Linha do Tempo (cinemática) | Animação | PLANEJADO | anim.timeline |

## AMBIENTE

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| env.environment | Environment Nick | Nick de Ambiente | Ambiente | PLANEJADO | MUNDO |
| env.sky | Sky Nick | Nick de Céu | Ambiente | PLANEJADO | MUNDO |
| env.daynight | Day-Night Nick | Nick de Dia e Noite | Ambiente | PLANEJADO | MUNDO |
| env.weather | Weather Nick | Nick de Clima | Ambiente | PLANEJADO | MUNDO |
| env.fog | Fog Nick | Nick de Névoa | Ambiente | PLANEJADO | MUNDO |
| env.wind | Wind Nick | Nick de Vento | Ambiente | PLANEJADO | MUNDO |
| env.water | Water Nick | Nick de Água | Ambiente | PLANEJADO | MUNDO |
| env.terrain | Terrain Nick | Nick de Terreno | Ambiente | PLANEJADO | MUNDO |
| env.vegetation | Vegetation Nick | Nick de Vegetação | Ambiente | PLANEJADO | MUNDO |
| env.procedural | Procedural World Nick | Nick de Mundo Procedural | Ambiente | PLANEJADO | MUNDO |

## EFEITOS

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| fx.particles | Particle Nick | Nick de Partículas | Efeitos | PLANEJADO | MUNDO |
| fx.decal | Decal Nick | Nick de Decalque | Efeitos | PLANEJADO | MUNDO |
| fx.trail | Trail Nick | Nick de Rastro | Efeitos | PLANEJADO | MUNDO |

## ÁUDIO

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| audio.source | Audio Source Nick | Nick de Fonte de Áudio | Áudio | PLANEJADO | áudio |
| audio.music | Music Nick | Nick de Música | Áudio | PLANEJADO | áudio |
| audio.zone | Audio Zone Nick | Nick de Zona de Áudio | Áudio | PLANEJADO | áudio |

## MÍDIA

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| media.video | Video Nick | Nick de Vídeo | Mídia | PLANEJADO | vídeo/mídia |
| media.sprite | Sprite Nick | Nick de Sprite | Mídia | PLANEJADO | TELAR |
| media.anim2d | 2D Animation Nick | Nick de Animação 2D | Mídia | PLANEJADO | TELAR |

## CURVAS (ROTA)

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| curve.mesh | Mesh Curve Nick | Nick de Curva de Malha | Curvas | PLANEJADO | CURVAS |
| curve.path | Path Nick | Nick de Trajeto | Curvas | PLANEJADO | CURVAS |
| curve.road | Road Nick | Nick de Estrada | Curvas | PLANEJADO | CURVAS |
| curve.traffic | Traffic Nick | Nick de Tráfego | Curvas | PLANEJADO | CURVAS |

## LÓGICA

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| logic.linkerf | Linkerf Graph Nick | Nick de Grafo Linkerf | Lógica | PLANEJADO | LINKERF |
| logic.timer | Timer Nick | Nick de Temporizador | Lógica | PLANEJADO | LINKERF |
| logic.counter | Counter Nick | Nick de Contador | Lógica | PLANEJADO | LINKERF |
| logic.spawner | Spawner Nick | Nick de Gerador | Lógica | PLANEJADO | ciclo de vida |
| logic.save | Save Point Nick | Nick de Ponto de Salvamento | Lógica | PLANEJADO | ciclo de vida |

## MUNDO

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| world.world | World Nick | Nick de Mundo | Mundo | PLANEJADO | MUNDOS |
| world.layer | Layer Nick | Nick de Camada | Mundo | PLANEJADO | camadas |

## UI (2D — árvore própria do TELAR; nome: "UI X Nick" / "Nick de X de UI")

| Chave | Inglês | Português | Grupo | Status | Entra em |
|-------|--------|-----------|-------|--------|----------|
| ui.screen | UI Screen Nick | Nick de Tela de UI | UI | PLANEJADO | TELAR |
| ui.panel | UI Panel Nick | Nick de Painel de UI | UI | PLANEJADO | TELAR |
| ui.text | UI Text Nick | Nick de Texto de UI | UI | PLANEJADO | TELAR |
| ui.image | UI Image Nick | Nick de Imagem de UI | UI | PLANEJADO | TELAR |
| ui.button | UI Button Nick | Nick de Botão de UI | UI | PLANEJADO | TELAR |
| ui.video | UI Video Nick | Nick de Vídeo de UI | UI | PLANEJADO | TELAR |
| ui.joystick | UI Joystick Nick | Nick de Joystick de UI | UI | PLANEJADO | M1.3 |
| ui.slider | UI Slider Nick | Nick de Controle Deslizante de UI | UI | PLANEJADO | TELAR |
| ui.progress | UI Progress Nick | Nick de Barra de Progresso de UI | UI | PLANEJADO | TELAR |
| ui.list | UI List Nick | Nick de Lista de UI | UI | PLANEJADO | TELAR |
| ui.dialogue | UI Dialogue Nick | Nick de Caixa de Diálogo de UI | UI | PLANEJADO | FALA |
| ui.touch | UI Touch Area Nick | Nick de Área de Toque de UI (tocar, deslizar, segurar) | UI | PLANEJADO | TELAR |

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
