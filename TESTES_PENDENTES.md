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

> A tag `fase-2-ok` foi removida (2026-11-21). Este aceite valida SOMENTE os itens
> já feitos da Fase 2 (Diagnóstico, pastas, duplicar, .KERNPACK). Os pendentes
> (importador glTF/GLB → .KERN, .KIMG, cache, streaming, navegador, pré-
> visualização, prefabs, registro de assets) ganham aceite quando implementados.

1. Abrir o Hub: versão v0.1.13-fase2 no cabeçalho; projeto ANTIGO abre e ganha
   as pastas ui/ e worlds/ (verificar com gerenciador de arquivos, opcional).
2. Ícone do INSETO no Hub → Diagnóstico → "Rodar tudo": 7 checagens
   APROVADAS (mandar print). FALHOU em glcaps/memoria pode ser característica
   do aparelho — anotar o motivo mostrado.
3. No editor: com o overlay (inseto da TopBar), conferir que o TORUS aparece
   na cena (ele nunca tinha renderizado antes do fix do .KERN).
4. Menu de três pontos de um projeto → Duplicar: "X (cópia)" aparece no Hub e
   abre igual.
5. Exportar (.kernpack) → escolher pasta → importar de volta (ícone de pasta
   no Hub): projeto volta com cena idêntica.
6. Criar projeto novo: cube/Spin/gizmo/undo/Play continuam funcionando
   (regressão padrão da Fase 1).

## Diagnóstico (a partir da Fase 2)

_Abrir no Hub → Diagnóstico → rodar → mandar print._
(Após F2.3: .ck ida e volta, .KERN ida e volta, picking, shaders, caps GL,
buffers, memória — cada linha APROVADO/FALHOU com motivo.)
