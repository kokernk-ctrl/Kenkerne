# KENKERN — TESTES_PENDENTES (só no aparelho; o agente não roda o app)

## Aceite v0.1.18 — E1–E6 — Release `v0.1.18-fase2-teste`

> 6 bugs do aparelho corrigidos. Cada item abaixo cita o que o teste precisa
> provar. Fase 2 CONTINUA ABERTA; tag `fase-2-ok` NÃO criada.

### E1 — handler nativo (Diagnóstico → "Rodar tudo")

1. Checagem **"nativo"**: APROVADO com a LISTA de funções JNI —
   `versaoNativa ✓ · ecoNativo ✓ · instalarHandlerSinaisNativo ✓ ·
   handlerInstaladoNativo ✓` — versão 1.0.0, ABI arm64-v8a. Se algo faltar,
   o detalhe mostra o NOME e o erro literal ("No implementation found…").
2. (Crash real, se acontecer um dia) crash nativo gera
   crash_logs/native_crash.txt com sinal + backtrace no diálogo do Hub.

### E2 — Diagnóstico nunca trava

3. "Rodar tudo" de novo: cada linha mostra o TEMPO da checagem (ex.: "412 ms",
   "2,3 s") no canto; a UI RESPONDE durante a rodada (rola a lista, volta).
4. Botão vira **CANCELAR** durante a rodada: toque → a checagem corrente para
   e o resto sai como "–" CANCELADA; nada congela.
5. Checagem **"etc2 nativo"**: aprova com o nativo em 1024² (< 1 s; MP/s
   visíveis) + comparação 256² com PSNR dos dois encoders. Nenhuma checagem
   passa de 30 s (se passar, ela falha com "tempo limite" e a rodada para).

### E3 — janela "Adicionar Nick"

6. No editor → "+" da Hierarquia → rolar a lista até o FIM em PAISAGEM
   (mesmo com 360dp de altura): o ÚLTIMO tipo do catálogo fica INTEIRO
   visível; o rodapé "Como filho do selecionado / Na raiz" aparece embaixo.
7. Rolar a lista e tocar nas áreas vazias do painel: A CÂMERA NÃO MEXE.

### E4 — painéis flutuantes

8. Menu do painel → "Flutuar": ARRASTAR pelo CABEÇALHO move o painel AO VIVO;
   ele NUNCA sai do viewport (solta na borda e ele fica DENTRO).
9. Alça no CANTO INFERIOR DIREITO (48dp): redimensiona com mínimo
   (~220×200dp) e máximo = viewport; tocar no painel o traz para FRENTE.
10. Botão ALFINETE no cabeçalho = ACOPLAR de volta; botão RESETAR LAYOUT na
    TopBar devolve tudo ao padrão (visível + acoplado + tamanho padrão).
11. Mover/redimensionar, GIRAR O APARELHO: cada orientação lembra o PRÓPRIO
    layout; toques no painel NUNCA movem a câmera.

### E5 — auxiliares de editor

12. "+" da Hierarquia → criar "Nick de Luz Direcional", "Nick de Luz
    Pontual", "Nick de Luz de Foco", "Nick de Câmera", "Nick de Área",
    "Ponto de Surgimento" e "Sinal": cada um aparece no viewport como
    AUXILIAR colorido (seta/esfera+anel/cone/pirâmide/caixa/losango), com
    tamanho CONSTANTE ao dar zoom, cor por grupo e âmbar ao SELECIONAR.
13. TOCAR no auxiliar seleciona (tolerância ~24dp); a Hierarquia e o
    Inspetor concordam; o gizmo de transformação funciona neles.
14. Botão LÂMPADA na TopBar ("Mostrar auxiliares"): desliga/liga os ícones.
    PLAY: os auxiliares SOMEM; STOP: voltam.
15. Collider selecionado: o WIREFRAME VERDE agora aparece SOBRE O NICK no
    viewport (não mais no quadradinho do canto).
16. NÃO é aceite desta versão: luz Nick ILUMINAR a cena (entra no LUME) e
    FOV/corpo com forma própria (LENTE/M1) — ver NICKS.md.

### E6 — StatusBar

17. Projeto novo: StatusBar lê "2 nicks · 1 malha" (era "1 desenhado").
    Importar um modelo com N grupos de material: M cresce por GRUPO.
18. Checagem "cena = tela": APROVADO com o texto novo ("M malhas medido").

### Depois do teste

- Reportar: o que falhou + print da checagem "nativo" (lista de funções) e
  dos auxiliares (uma luz + câmera + área na mesma cena).
- Ações seguintes: **PARADA 2 (T0.4 fidelidade de importação + T0.5 Hub
  novo)** do BLOCO 0.

## Aceite da PARADA 1 — T0.1 idiomas + T0.2 tipos de Nick + T0.3 janela Adicionar Nick

> BLOCO 0 do adendo v4 (DECISIONS #24–#28): app em en/pt, tipos de Nick como
> preset de dados (CK2), janela "Adicionar Nick", cabeçalho de tipo no Inspetor
> e seletor de forma do collider. Build pós-T0.3 (APK de teste do CI).

1. Abrir o app: a interface segue o IDIOMA DO APARELHO (português no seu caso).
   No Hub, tocar o ÍCONE DE GLOBO no cabeçalho → Português / English / idioma
   do aparelho — a troca é IMEDIATA (tela recria traduzida, sem reiniciar).
2. Criar um PROJETO NOVO: a Hierarquia mostra "Nick de Grupo (1)" e
   "Nick de Malha" (nomes automáticos = rótulo do TIPO; renomear deixa o texto
   do usuário fixo — o campo vazio volta ao rótulo).
3. ABRIR UM PROJETO ANTIGO (CK1, ex.: com "Mundo"/"Cubo"): os nomes automáticos
   viram o rótulo do tipo migrado ("Nick Vazio"/"Nick de Malha" — Mundo vira
   nick vazio, modelo com malha vira Nick de Malha); o conteúdo NÃO muda.
4. No editor, tocar o "+" da Hierarquia: painel "Adicionar Nick" (~40% da
   largura, ao lado da Hierarquia) com busca, abas (Recentes, Base, Luz, Câmera,
   Corpos, Áreas), cartões com ícone/nome/descrição e ESTRELA de favorito.
   Criar um "Nick de Corpo Colisor": nasce SELECIONADO no CENTRO da tela
   projetado no chão; a opção "Como filho do selecionado / Na raiz" funciona;
   DESFAZER remove (é UM passo de undo).
5. Com o Collider selecionado: o Inspetor mostra o CABEÇALHO com ícone + tipo
   (+ chave em mono), e a seção "Forma do colisor" com Caixa/Esfera/Cápsula/
   Cilindro/Convexo/Malha — trocar a forma mostra o WIREFRAME VERDE no viewport
   (ajustado à AABB do Nick) e o DESFAZER devolve a forma anterior.
6. Botão "Adicionar classe" no Inspetor: menu AGRUPADO por função (Movimento:
   Spin/Bob · Geometria: Malha · Física: Colisor).
7. Trocar o idioma para English e conferir que Hub, editor, painéis, janela
   Adicionar Nick, Diagnóstico e diálogo de crash ficam em inglês (voltar para
   Português depois).
8. Reportar: o que ficou estranho/no idioma errado + prints da janela Adicionar
   Nick e do wireframe do collider.

## Aceite v0.1.17 — BUGS A/B + NÚCLEO NATIVO — Release `v0.1.17-fase2-teste`

> Bug A: viewport desenhava objetos fora da cena. Bug B: importação lenta.
> Núcleo C++: etcpak (ETC2) + meshoptimizer (dedup/cache/LOD) + handler de
> sinais. Fase 2 CONTINUA ABERTA (F2.11–F2.15); tag `fase-2-ok` NÃO criada.

1. Instalar a v0.1.17 pela Release e abrir um PROJETO NOVO: o viewport mostra
   SOMENTE 1 cubo (o "Cubo" da Hierarquia) — sem torus girando e sem cubo
   extra na origem (Bug A).
2. No editor, conferir a StatusBar: "N nicks · M desenhados" — em um projeto
   novo tem que ler "2 nicks · 1 desenhado" (Mundo é container, não desenha).
   Cada objeto visível no viewport aparece na Hierarquia e TOQUE seleciona.
3. Hub → ícone do INSETO → Diagnóstico → "Rodar tudo": **20 checagens, 0
   falhas**, incluindo as novas **"nativo"** (libkenkern.so, versão, ABI,
   eco(21)=43, handler de sinais), **"etc2 nativo"** (1024² com ms · MP/s ·
   PSNR do etcpak × Kotlin) e **"malha nativa"** (grade 20.000 tris: ms nativo
   × Kotlin, únicos iguais) e **"cena = tela"** (M medido == visíveis com
   malha; torus .KERN renderiza SÓ offscreen). Enviar o print.
4. IMPORTAR um modelo de ~2 MB com 1 textura: acompanhar o progresso
   ("textura 1/1 · P% · ~Ns") e, ao fim, abrir o RELATÓRIO e LER A TABELA DE
   TEMPO POR ETAPA (ms e % do total: leitura, parse, dedup, LODs, decodificação,
   redimensionamento, mipmaps, ETC2, gravação, criação do Nick) — conferir se
   ficou abaixo de 10 s e onde ficou o maior tempo. A tabela também vai para o
   Console (Diagnóstico → CONSOLE). Enviar o print do relatório.
5. No diálogo de importação, conferir as opções NOVAS: "Limite de resolução
   de textura" (512/1024/2048, padrão 1024) e "Qualidade ETC2" (Rápida padrão /
   Alta qualidade T/H).
6. Opcional (prova do handler nativo): se acontecer crash nativo algum dia, o
   diálogo de crash do Hub passa a mostrar a seção "— crash nativo (handler de
   sinais C++) —" com sinal, endereço e backtrace (crash_logs/native_crash.txt).

## Aceite das CORREÇÕES D1–D5 — Release `v0.1.16-fase2-teste`


## Aceite das CORREÇÕES D1–D5 — Release `v0.1.16-fase2-teste`

> Valida as correções das 4 falhas do Diagnóstico da v0.1.15 no aparelho
> (Mali-G57, GLES 3.2). Fase 2 CONTINUA ABERTA (faltam F2.11–F2.15); a tag
> `fase-2-ok` CONTINUA não criada. Todos os itens ficam `COMPILADO` até este
> aceite.

1. Instalar a v0.1.16 pela Release: o app abre em **PAISAGEM** automaticamente
   (sensor, as duas laterais) e NÃO abre em vertical (D5).
2. Hub → ícone do INSETO → Diagnóstico: o cabeçalho mostra "Diagnóstico" e
   "checagens da engine neste aparelho" em 1 linha cada, sem corte (D5).
3. Diagnóstico → "Rodar tudo": **16 checagens** — as 4 que falhavam devem
   ficar APROVADO: **picking** (mensagem nova com olho/origem do raio),
   **release** (script Lua real com print+math+bit32+string), **importar**
   (undo/redo com a lixeira no caminho certo) e **etc2** (mensagem separa
   (a) erro de upload (b) status do framebuffer (c) pixel GPU vs decodificação
   CPU + fallback RGBA8); conferir as NOVAS checagens **"toque no cubo"**
   (toque no centro projetado do cubo acerta) e **"tex-cena"** (quad texturizado
   não-preto). **Meta: 0 falhas** — enviar o print (D6.2).
4. No editor, TOCAR no cubo da cena padrão: ele deve SELECIONAR (D1) — e
   duplo toque continua focando, arrasto continua girando a câmera.
5. IMPORTAR um modelo (ex.: DamagedHelmet.glb, 3,8 MB) e DESFAZER: o Nick sai
   e o .KERN volta para a lixeira; REFAZER devolve tudo (D3). Se o desfazer
   falhar, a mensagem mostra origem/destino/existe/canRead/canWrite/mesmoVolume.
6. Reportar o resultado com o print do Diagnóstico (16 checagens).

## Aceite da AUDITORIA v0.1.15 — Release `v0.1.15-fase2-teste`

> Valida a auditoria (V1 fumaça do release, V2 importação com camada Deflate
> em disco do .KIMG, V3 avisos honestos do ImportReport). A Fase 2 CONTINUA
> ABERTA (faltam F2.11–F2.15) e a tag `fase-2-ok` CONTINUA não criada. Todos
> os itens abaixo ficam `COMPILADO` até este aceite.
>
> **Modelos reais usados nos testes do agente (Khronos glTF-Sample-Assets,
> licença aberta — reprodutíveis pelo `ModeloRealMedicaoTest`)**:
> DamagedHelmet.glb (3,8 MB) · FlightHelmet glTF+bin+15 PNG (48,4 MB) ·
> Sponza glTF+bin+68 texturas (52,7 MB) · BrainStem.glb (3,2 MB, com
> esqueleto+animação — o ImportReport deve avisar "ignorados nesta fase").

1. Instalar a v0.1.15 pela Release, abrir o Hub → ícone do INSETO →
   Diagnóstico → "Rodar tudo": **14 checagens**, conferir a NOVA checagem
   **"Release minificado — classes críticas + operações mínimas"** (deve estar
   APROVADO; se aparecer FALHOU, o APK está quebrado — enviar o print).
2. IMPORTAR o DamagedHelmet.glb: o relatório deve mostrar razão ≥ 1,0×,
   PSNR das texturas e o modelo com textura visível; desfazer/refazer.
3. IMPORTAR o FlightHelmet (pasta glTF com .bin + texturas): relatório deve
   avisar **"extensões glTF declaradas e IGNORADAS: KHR_materials_transmission"**
   — e a malha importa mesmo assim (4,25× de razão na medição do agente).
4. IMPORTAR o BrainStem.glb: relatório deve avisar **"contém esqueleto"** e
   **"contém animações: ignoradas"** — e o modelo estático aparece na cena.
5. IMPORTAR um FBX com textura externa (sem embutido): relatório deve avisar
   "textura externa … sem conteúdo embutido — cor sólida nesta fase" e o
   modelo aparece com cor sólida do material.
6. Fechar e reabrir o projeto: texturas NÃO reconvertam (cache por hash) e a
   segunda abertura é mais rápida.
7. Conferir que o app inteiro funciona normal (regressão): gizmo, Play, undo,
   .kernpack, Diagnóstico completo.
8. Reportar resultado com o print do Diagnóstico (as 14 checagens).

## Aceite da FASE 2 (fechamento testável) — app v0.1.14 (Release `v0.1.14-fase2-teste`)

> Valida o fechamento testável da Fase 2: botão Importar modelo (A), importadores
> corrigidos (B) e .KIMG/texturas (C). A tag `fase-2-ok` CONTINUA não criada —
> só sai quando TODOS os itens da Fase 2 estiverem implementados e o usuário
> escrever "aprovado" (restam: navegador de arquivos, pré-visualização, prefabs,
> registro de assets, streaming anti-trave). Todos os itens abaixo ficam
> `COMPILADO` até este aceite.

1. Instalar a v0.1.14 pela Release, abrir o Hub → ícone do INSETO → Diagnóstico →
   "Rodar tudo": 12 checagens; enviar o PRINT (todas APROVADO; `astc` pode ser
   "NÃO SUPORTADO" sem problema).
2. Abrir um projeto, tocar no cubo, arrastar a seta X do gizmo, girar com o anel
   e desfazer.
3. Apertar Play e Stop e conferir que a cena volta igual.
4. IMPORTAR um .glb pelo botão da TopBar (menu "Arquivo"): aparece o relatório
   com tamanho antes/depois e razão; o modelo aparece na cena, dá para selecionar
   e mover; desfazer REMOVE o modelo e o asset; refazer devolve os dois.
5. IMPORTAR um glTF com .bin e texturas (pasta OU .zip) e um .fbx: conferir a
   textura VISÍVEL no modelo (se o driver recusar ETC2, a textura continua
   visível via fallback).
6. Desfazer a importação e refazê-la com Refazer.
7. Fechar e reabrir o projeto: o modelo continua e a textura NÃO reconvertre
   (segunda abertura mais rápida — cache por hash).
8. IMPORTAR um arquivo grande (≥ 50 MB): a UI não trava, há progresso por
   etapas e é possível CANCELAR; acima de 200 MB aparece a confirmação.
9. Reportar resultado com o print do Diagnóstico.


**Estado da validação (2026-11-21): COMPILADO ≠ VALIDADO.** Nenhum item abaixo é
VALIDADO até o usuário executar no aparelho. Em especial: seleção por toque, Play
real, paisagem automática e gizmos (Fase 1) ficam `COMPILADO` até este aceite.

## Aceite da FASE 1 — app v0.1.12 (Release `fase-1-ok`; validação PENDENTE)

1. Instalar o APK da Release e abrir: Hub lista projetos; criar projeto "Teste F1".
2. No editor: o cubo aparece sobre a grade; girar a câmera com 1 dedo (Orbital).
3. Pinça: afastar e aproximar SEM soltar os dedos — zoom fluido, sem travar;
   soltar e pinçar de novo — continua fluido.
4. Voo: alternar na TopBar; 1 dedo direita/esquerda = olha para o lado; cima/baixo
   = pitch; joystick e botões movem na direção do olhar; 2 dedos também olham.
5. Tocar no cubo (seleciona); arrastar a seta X do gizmo (move); desfazer (volta).
6. Play: cubo gira (Spin). Stop: para.
7. Voltar ao Hub e reabrir o projeto: cena e seleção persistem.
8. Ligar o overlay do inseto (TopBar): linhas cam:/voo: com valores ao vivo.
9. Reportar resultado (e print do overlay se algo falhar).

## Aceite da FASE 2 — app v0.1.13 (build da fase ABERTA — parte FEITA)

> A tag `fase-2-ok` foi removida (2026-11-21). Este aceite valida os itens já feitos
> da Fase 2 (Diagnóstico, pastas, duplicar, .KERNPACK) MAIS o importador glTF/GLB
> (F2.7) e o fix da rotação. Continuam pendentes na Fase 2: .KIMG (F2.9),
> cache, streaming, navegador, pré-visualização, prefabs, registro de assets.

1. Abrir o Hub: versão v0.1.13-fase2 no cabeçalho; projeto ANTIGO abre e ganha
   as pastas ui/ e worlds/ (verificar com gerenciador de arquivos, opcional).
2. Ícone do INSETO no Hub → Diagnóstico → "Rodar tudo": 9 checagens
   APROVADAS (mandar print). FALHOU em glcaps/memoria pode ser característica
   do aparelho — anotar o motivo mostrado.
3. No editor: com o overlay (inseto da TopBar), conferir que o TORUS aparece
   na cena (ele nunca tinha renderizado antes do fix do .KERN).
4. **Rotação correta (fix do quaternion)**: Play com a classe Spin — o cubo
   GIRA uniforme, sem encolher/entortar. Girar o nick no Inspetor (yaw 90°) e
   conferir que a malha fica 90° de verdade.
5. Menu de três pontos de um projeto → Duplicar: "X (cópia)" aparece no Hub e
   abre igual.
6. Exportar (.kernpack) → escolher pasta → importar de volta (ícone de pasta
   no Hub): projeto volta com cena idêntica.
7. Criar projeto novo: cube/Spin/gizmo/undo/Play continuam funcionando
   (regressão padrão da Fase 1).

## Importador glTF/GLB (F2.7) — aceite no aparelho

1. Diagnóstico → checagem "Importador glTF/GLB → .KERN" APROVADA com o resumo
   da meta (print). (A checagem gera GLB na memória, importa e valida.)
2. (Quando o painel de importação existir, F2.12: importar um .glb real e ver
   a malha no viewport — hoje o importador está no núcleo + Diagnóstico.)

## Importador FBX (F2.8) — aceite no aparelho

1. Diagnóstico → checagem "Importador FBX → .KERN (instâncias + integridade)"
   APROVADA com o resumo (print). (A checagem gera FBX binário na memória,
   importa quad + cubo compartilhado por 2 Models e valida integridade.)
2. (Quando o painel de importação existir, F2.12: importar um .fbx REAL do
   Blender (exportação binária 7.4) e ver a malha no viewport — triangulação de
   polígonos e transform dos Models serão os pontos a conferir.)

## Diagnóstico (a partir da Fase 2; 9 checagens desde o F2.8)

_Abrir no Hub → Diagnóstico → rodar → mandar print._
(.ck ida e volta, .KERN ida e volta, **importador glTF/GLB → .KERN**,
**importador FBX → .KERN**, picking, shaders, caps GL, buffers, memória — cada
linha APROVADO/FALHOU com motivo; erros desta sessão também aparecem na seção
CONSOLE.)
