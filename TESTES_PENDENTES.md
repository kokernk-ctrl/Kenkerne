# KENKERN — TESTES_PENDENTES (só no aparelho; o agente não roda o app)

## v0.1.30 — PARADA 2 (parcial: F1 locais + F2 árvore + F6 checagens): 7 itens

> 3 commits, CI verde em cada; 493 testes JVM verdes; DECISIONS.md #8.
> F3 (painel Projeto explorador), F4 (importar de qualquer pasta) e F5
> (referências estáveis) ficam para a sequência da Parada 2. COMPILADO.

1. **Permissão "Acesso a todos os arquivos"**: no diálogo de novo projeto,
   escolher "Aparelho" SEM permissão mostra a explicação; o botão ABRE A
   CONFIGURAÇÃO DO SISTEMA; voltar concedendo atualiza o diálogo sem
   recriar; NEGADA → "Continuar no App" segue em App com .kernpack/SAF.
2. **Criar no Aparelho** (com permissão): criar um projeto com local
   "Aparelho" gera a pasta VISÍVEL em /storage/emulated/0/Kenkern/Projetos/
   <nome> com a árvore completa (conferir no gerenciador de arquivos).
3. **Pasta pai**: com subpastas na raiz do local, criar o projeto DENTRO de
   uma delas; a pasta do projeto nasce no caminho escolhido.
4. **Mover**: menu do cartão de projeto → "Mover para o Aparelho (cópia
   verificada)" — a cópia aparece no destino COMPLETA; o ORIGINAL continua
   no Hub até você conferir e apagar à mão.
5. **Árvore F2**: abrir um projeto VELHO (v0.1.29) — ele ganha
   assets/{models,textures,materials,audio,fonts,video}, dialogues e
   .kenkern SEM APAGAR nada (arquivos antigos intactos).
6. **Diagnóstico 33/33**: as três novas ("armazenamento", "pastas",
   "explorador") APROVADAS com números reais (ms da listagem de 5.000
   sintéticos, estado da permissão, espaço livre) — e as 30 anteriores
   verdes (a "luz" com os números da R1).
7. **Auto-save nos dois locais**: criar no Aparelho, editar a cena no
   editor, esperar o auto-save (500 ms) e conferir que o arquivo mudou na
   pasta VISÍVEL (não só no interno).
8. **Relatório do aparelho**: copiar o relatório do Diagnóstico e mandar
   (os números reais valem mais que os daqui).
