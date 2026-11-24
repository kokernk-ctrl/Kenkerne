# KENKERN — TESTES_PENDENTES (só no aparelho; o agente não roda o app)

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
