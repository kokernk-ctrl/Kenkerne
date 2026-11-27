# KENKERN — TESTES_PENDENTES (só no aparelho; o agente não roda o app)

## v0.1.28 — LUZ DO LUME + 30/30 + ONDA 0: 6 itens de aceite

> 7 commits (L1, L2, Onda 0 P1/P2, P3+P4, P5, P6, fechamento), CI verde em
> cada; 456 testes JVM verdes (cada bug nasceu VERMELHO primeiro —
> reprodução antes de corrigir). Fatos do aparelho valem mais que testes de
> JVM.

1. **Diagnóstico → Rodar tudo: 30/30 APROVADAS, 0 falhas, 0 PENDENTES** —
   a "Luz do LUME" APROVADA com o pixel dominado pelo vermelho (R>200,
   G<100, B<100) e os uniforms da ETAPA A no Console (Copiar relatório);
   as 4 que ficavam PENDENTES (Saída da janela de jogo, Nicks de Área,
   Editor de código Lua, UI do TELAR) agora RODAM e ficam APROVADAS.
2. **Janela de jogo tocável**: tocar PAUSAR pausa em ≤100 ms; SAIR volta ao
   editor; NENHUM botão do editor (Grade/Focar) na janela de jogo nem sobre
   a barra de navegação; o overlay de toque (botão TouchApp) aparece POR 3 s
   com coordenada, alvo, consumidor e ordem z.
3. **Porta de entrada do editor de código**: 2 TOQUES do Hub até o editor de
   código (aba Código na TopBar) com o navegador de arquivos (abas de
   scripts + botão + dos modelos).
4. **TopBar**: no máximo 8 botões visíveis + menu "mais" com o resto —
   confira que Importar/Mover/Girar/Escalar/Snap/Voo/Focar/Ver pela câmera/
   Auxiliares/Exportar/Diagnóstico/Resolução/Resetar continuam TODOS no
   menu "mais" (manda o print antes/depois).
5. **Fontes OFL em modo avião**: com o aparelho em MODO AVIÃO, o texto em
   pt renderiza com as fontes do TELAR (troque a fonte de um TEXTO no
   TELAR e veja mudar) e a checagem "ui" mostra "FONTES: 8/8 OFL carregadas
   do APK (sem rede)".
6. **Aviso de skinning**: importe um personagem com esqueleto — o relatório
   mostra "esqueleto/skinning ainda não suportado — Onda 3" (em vez de
   entregar modelo quebrado sem explicação; manda o print do aviso).


> 1 commit (U1) + fechamento, CI verde; 431 testes JVM verdes.

1. **Aba "UI" (TELAR)**: no editor, o ícone GridOn na TopBar abre o editor de
   UI — canvas 1920x1080 com zoom/pan, régua, pré-visualização 16:9/18:9/20:9
   (chips no topo) e voltar ao editor 3D sem perder nada.
2. **Monte uma tela**: adicione PAINEL + TEXTO + BOTÃO; arraste com snap de
   8px; troque a âncora (os 9 cantos/centros + esticar); camadas reordenam
   (setas Z), ocultar e travar funcionam; Remover com undo/redo.
3. **Joystick migra**: projetos com Joystick Nick abrem o TELAR com o
   joystick JÁ como elemento de UI (tipo/tamanho/opacidade/zona morta/8
   direções/"Controla"/"Seguir Nick" preservados).
4. **UI em Play**: Play desenha o .kui SOBRE o jogo — o botão chama a função
   Lua do campo aoClicar, o joystick move o jogador e os toques NÃO vazam
   para o editor; Sair devolve tudo como estava.
5. **Diagnóstico → Rodar tudo: 25 checagens, 0 falhas** — a nova "UI do
   TELAR" (âncoras nas 3 proporções, .kui ida e volta, migração, commands,
   tela do template) fica APROVADA. NOTA: as 8 fontes OFL estão como NOMES
   no modelo (as TTFs embutidas entram na sequência — registrado).

## v0.1.26 — PARADA 2 (C1/C2): 6 itens de aceite

> 2 commits (C1, C2) + fechamento, CI verde em cada; 415 testes JVM verdes.

1. **Editor de código (C1)**: Inspetor → classe Script → "Abrir no editor de
   código" — a tela vira RETRATO com números de linha, realce, teclas de
   apoio sobre o teclado e "Verificar" que sublinha a linha do erro; ao sair,
   volta PAISAGEM sem recarregar o projeto.
2. **Autocompletar e modelos (C1)**: digitar `nick.` mostra os membros; o
   botão + cria script novo dos modelos (vazio/movimento/coletar/gatilho);
   edite e SAIA sem salvar — o SALVAR AUTOMÁTICO guardou (reabra e confira).
3. **Desempenho (C1)**: buscar/substituir funcionam; arquivo > 200 KB mostra
   o aviso; nada congela com o teclado aberto (linhas virtualizadas).
4. **Área completa (C2)**: Adicionar Nick tem Área/Sinal/Ponto de Surgimento/
   Checkpoint de volta (sem "Em breve"); área com classe ÁREA dispara
   on_enter/on_exit/on_stay; "filtro por tag" e "uma vez só" funcionam;
   `area.bodies()`/`area.contains(nick)` na Lua.
5. **Checkpoint (C2)**: monte um chão com um buraco, um Checkpoint no meio e
   caia pelo buraco — o jogador REAPARECE no último checkpoint; na Lua,
   `player.respawn()` faz o mesmo.
6. **Diagnóstico → Rodar tudo: 24 checagens, 0 falhas** — as novas "Nicks de
   Área (ZONA)" e "Editor de código Lua" ficam APROVADAS.

## v0.1.25 — PARADA 1 (P1/P2/P3): 6 itens de aceite

> 3 commits (P1, P2, P3) + fechamento, CI verde em cada; 388 testes JVM
> verdes (P1 e P3 nasceram VERMELHOS). Fatos do aparelho valem mais que
> testes de JVM.

1. **JOGO EM PLAY — botões respondem (P1)**: entre no Play de qualquer cena
   e toque em PAUSAR e SAIR — os DOIS respondem (antes ficavam mortos e o
   usuário ficava preso). Voltar de PAUSED para o jogo também responde.
2. **Saída garantida (P1.3)**: dentro da Janela de Jogo, o botão BACK do
   sistema SEMPRE sai do jogo (jogando ou pausado). Nada fica preso.
3. **Visual "Singularidade" (P2)**: Hub, TopBar, painéis, diálogos,
   Diagnóstico e Janela de Jogo usam o novo estilo (superfície escura + aro
   cobre + âmbar no primário); os painéis do editor continuam OPAQUES e
   legíveis; NADA de shader animado fora do Hub.
4. **Adicionar Nick com "Em breve" (P3)**: os Nicks de Área (volume/spawn)
   aparecem com selo "Em breve" e DESABILITADOS (não criam Nick); todos os
   demais (corpos, luzes, câmeras, joystick) continuam criáveis e os
   placeholders continuam fora da lista.
5. **Diagnóstico → Rodar tudo: 22 checagens, 0 falhas** — a nova
   "Saída da janela de jogo" (roteamento de toque, watchdog 3 s e BACK)
   fica APROVADA junto das 21 anteriores.
6. **Debug de toque (P1.4)**: na Janela de Jogo, o botão novo no canto
   superior direito mostra o ÚLTIMO toque (px), o alvo decidido e quem
   consumiu (cena/botão) — mande o print se algo não responder.

## v0.1.24 — ESTABILIZAÇÃO (S1–S8): 8 itens de aceite

> 8 commits (S1–S8), CI verde em cada. 356 testes JVM verdes (cada correção
> nasceu VERMELHA primeiro — reprodução antes de corrigir). Fatos do
> aparelho valem mais que testes de JVM.

1. **Diagnóstico → Executar**: "materiais", "luz", "layout", "joystick" e
   "jogo" mostram título PRÓPRIO (nada mais sob ".ck (cena)"); a checagem
   "materiais" fica APROVADA; as FALHAS (se houver) aparecem no TOPO da
   lista; "Copiar relatório" copia o texto COMPLETO (cole num bloco de
   notas para conferir).
2. **Checagem "jogo" APROVADA**: o template Coletar moedas joga sozinho
   (300 ticks, input sintético, contador ≥ 1, jogador no chão) — se falhar,
   a mensagem diz o passo (caiu / atravessou o chão / não moveu / não
   colidiu).
3. **Corpos caem**: monte um chão (Static Body) e solte Rigid Body,
   Character Body e Collider Body a 5 m — Play: todos POUSAM e param;
   botão de inseto (overlay de debug de Play) lista os corpos simulados,
   velocidade e "no chão".
4. **Inspetor ao vivo em Play**: durante o Play, mude posição/cor/
   intensidade da luz e fov da câmera — o viewport muda NO MESMO frame;
   Sair (Stop) devolve a cena do editor EXATAMENTE como estava.
5. **Joystick Nick 2D**: Adicionar Nick → UI → Joystick — no editor aparece
   como MOLDURA 2D no canto inferior esquerdo (NUNCA um cubo 3D no mundo).
6. **Sem spinner eterno**: operações do Hub (abrir/criar/importar pacote)
   nunca ficam em "Carregando…" para sempre — após 15 s aparece o erro com a
   operação e a etapa, e os botões voltam a responder.
7. **Janela de jogo**: Play abre TELA CHEIA só do jogo (sem TopBar/painéis/
   StatusBar) com Pausar/Continuar e Sair; entrar e sair 10× — a cena volta
   correta toda vez e o app segue responsivo.
8. **Luz no editor**: uma luz do Nick (ponte/spot) ilumina a vista do
   EDITOR no mesmo pipeline do Play (a checagem "luz" verde confirma; se o
   seu aparelho ainda não ilumina no editor, mande o relatório copiado).
