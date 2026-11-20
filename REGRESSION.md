# KENKERN — REGRESSION (lista de verificação por tarefa e por fase)

## Sempre (todo commit)

- [ ] Nenhum `runCatching` engole erro em silêncio em caminho de render/formato/
      carregamento — registrar via `KenkernLog` (Log + Console + Diagnóstico);
      nova ocorrência entra em `docs/RUNCATCHING_AUDIT.md`.
- [ ] try/catch nos 3 callbacks GL (`KenkernSurfaceView` onSurfaceCreated/
      onSurfaceChanged/onDrawFrame) + runCatching do onTouch.
- [ ] Shader de fallback da grade (grade infinita incondicional no frame).
- [ ] isFinite antes de uniforms (`GlesBackend.fin`) e guardas de NaN da câmera
      (zoom/clamps/focusOn).
- [ ] Propriedades Kotlin declaradas ANTES de init que as usa (NPE v0.1.8).
- [ ] Nenhum `DisposableEffect` fecha o projeto no dispose (bug v0.1.1).
- [ ] Limiares de toque em dp; coordenadas de toque na base da projeção.
- [ ] Compose não trabalha por frame; FPS atualiza estado ≤ 2×/s.
- [ ] ZERO alocação por frame nos loops de render/update.
- [ ] Projeto novo E antigo abrem (CK1 com defaults na migração).
- [ ] Nenhuma credencial em código/log/repo.
- [ ] Paisagem com insets, sem sobreposição.

## Ao fim de cada fase (antes da tag `fase-N-ok`)

0. **GATE da tag**: TODOS os itens de aceite da fase implementados (BACKLOG.md
   sem `TODO`/`DOING` na fase). Sem isso, NÃO criar tag — a fase segue ABERTA
   (erro de processo cometido com `fase-2-ok` e revertido em 2026-11-21).
1. CI verde (`testReleaseUnitTest` + `assembleRelease`).
2. Checklist manual no aparelho (o usuário roda e manda print):
   - [ ] Abrir projeto NOVO e projeto ANTIGO (pré-fase).
   - [ ] Tocar num cubo (seleção) e arrastar o gizmo (mover/rotacionar/escalar).
   - [ ] Desfazer/Refazer.
   - [ ] Voltar ao Hub e reabrir (autosave + cena).
   - [ ] Câmera Orbital: orbitar, pan, pinça (afastar e aproximar, no mesmo gesto
         e em gestos separados).
   - [ ] Câmera Voo: mover (joystick/botões), olhar com 1 e 2 dedos nos DOIS eixos.
   - [ ] Play/Stop (classes Spin/Bob animam o cubo).
   - [ ] Tela Diagnóstico: todas as checagens APROVADAS (ou motivo anotado).
3. Toda regressão reportada entra aqui com: versão, sintoma, causa, fix.

## Regressões históricas (resolvidas — manter os testes)

- **Rotação de quaternion meia-amplitude (v0.1.13, achado pelo F2.7)**: os termos
  com `w` na conversão quaternion→matrix (`Quaternion.toMatrix` e `Mat4.setTRS`)
  estavam SEM o fator 2 — uma rotação de 90° em Y mapeava (0,0,1) para um vetor
  de comprimento 0,5 (malha encolhia/entortava em vez de girar; Play nunca foi
  validado no aparelho, por isso não foi visto). Fix + `QuaternionMatrixRegressionTest`.
- Zoom orbital preso (v0.1.10) → `PinchZoomRegressionTest`.
- Olhar horizontal morto no voo (v0.1.11) → `FlightLookRegressionTest`.
- Raio de tela invertido no orbital → `OrbitCameraRayTest`.
