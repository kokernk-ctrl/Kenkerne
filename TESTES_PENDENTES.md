# KENKERN — TESTES_PENDENTES (só no aparelho; o agente não roda o app)

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
> (F2.7) e o fix da rotação. Continuam pendentes na Fase 2: FBX (F2.8), .KIMG,
> cache, streaming, navegador, pré-visualização, prefabs, registro de assets.

1. Abrir o Hub: versão v0.1.13-fase2 no cabeçalho; projeto ANTIGO abre e ganha
   as pastas ui/ e worlds/ (verificar com gerenciador de arquivos, opcional).
2. Ícone do INSETO no Hub → Diagnóstico → "Rodar tudo": 8 checagens
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

## Diagnóstico (a partir da Fase 2; 8 checagens desde o F2.7)

_Abrir no Hub → Diagnóstico → rodar → mandar print._
(.ck ida e volta, .KERN ida e volta, **importador glTF/GLB → .KERN**, picking,
shaders, caps GL, buffers, memória — cada linha APROVADO/FALHOU com motivo;
erros desta sessão também aparecem na seção CONSOLE.)
