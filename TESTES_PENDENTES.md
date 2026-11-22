# KENKERN — TESTES_PENDENTES (só no aparelho; o agente não roda o app)

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
