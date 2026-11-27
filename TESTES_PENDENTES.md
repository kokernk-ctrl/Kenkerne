# KENKERN — TESTES_PENDENTES (só no aparelho; o agente não roda o app)

## v0.1.29 — PARADA 1 (regressões e telas): 7 itens de aceite

> 8 commits (R1–R7 + fechamento), CI verde em cada; 486 testes JVM verdes.
> Cada bug nasceu VERMELHO primeiro (prova de reprodução antes de corrigir).
> Nada disto é VALIDADO sem estes testes — o que segue é COMPILADO.

1. **Diagnóstico → "Luz do LUME" APROVADA com os números no Console**:
   pontual LIGADA dominada pelo vermelho (r>g+40 e r>b+40; espera algo como
   ~(243,108,111), não mais (102,63,70)); direcional vermelha com R>200,
   G<100, B<100 (não mais igual ao sol (59,63,70)); R1.3: o log "luz R1.3
   piso visto de cima" mostra a direcional int 2 MUDANDO o pixel nos DOIS
   caminhos e EDITOR = PLAY (o "quase preto" × "azul claro" do chão tem que
   sumir — se aparecer, mandar o print do Console com os 4 RGB).
2. **Verificar (editor de código)**: colar cada trecho quebrado (end
   faltando, string aberta, "= 1", função sem nome, caractere inválido) →
   painel "Problemas" com LINHA e MENSAGEM, linha sublinhada, toque na
   mensagem pula para a linha; os 3 válidos compilam limpos.
3. **Editor de código**: script criado do modelo "movimento" abre COM
   CONTEÚDO (não vazio); o diálogo pergunta o nome (padrão "script.lua",
   sufixo "_2" SÓ em colisão — nunca mais "script_movimento_34934.lua");
   barra [voltar | nome e estado | Verificar | ⋮] sem cortes e "salvo"
   NUNCA coberto; a faixa de teclas SEM os botões A−/A+/⏎/Buscar por cima
   (eles estão no menu ⋮); copiar/colar multilinha funciona.
4. **TELAR**: a barra superior (título, desfazer, refazer, Adicionar) ABAIXO
   da barra de status, visível; o canvas "16:9" com proporção real (~1,78:1)
   e letterbox nas 3 proporções (16:9/18:9/20:9 — não mais ~1,25:1); estado
   vazio mostra orientação + botão "Criar tela de UI" que monta a tela do
   template; "Adicionar" abre os 8 elementos.
5. **Inspetor**: NENHUM interruptor gigante/sobreposto; cabeçalho fixo com
   ícone, tipo, chave e "Ativo" 36dp; "Visível" em linha; Nome em UMA linha e
   Aplicar/Reverter só quando há alteração ("Concluído" do teclado aplica);
   seções recolhíveis por classe com "Adicionar classe" no fim; ao trocar de
   Nick a rolagem VOLTA AO TOPO (o Nome não vem mais cortado).
6. **Diagnóstico → mensagens sem "%" crú**: em "Malha nativa", "Botão
   Importar modelo" e ".KIMG" os números aparecem preenchidos (ex. "4,0×",
   "4 MB", "210 ms", "41,3 dB") — não mais "%.1f", "%.0f", "%d".
7. **Hub**: o globo do idioma com o MESMO aro de cobre da pasta e do inseto
   (os três consistentes); criar um projeto "mm" já existindo → "mm 2"
   (nunca dois "mm" na lista).
8. **Relatório do aparelho**: copiar o relatório do Diagnóstico
   (30/30 esperadas — se algo falhar, o relatório traz os números reais).
