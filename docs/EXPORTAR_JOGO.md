# EXPORTAR O APK DO JOGO (M1.8, v0.1.23)

O botão **"Exportar jogo"** (TopBar do editor, ícone de foguete) dispara o
GitHub Actions COM O PROJETO e o APK assinado do jogo sai como ARTIFACT do
workflow (o usuário baixa da aba Actions do repositório).

## Credenciais necessárias (NUNCA no repo, NUNCA em log)

- Um **Personal Access Token (classic ou fine-grained) com escopo `workflow`**
  (classic) ou permissão `actions: write` + `contents: read` (fine-grained).
- O token é digitado NO DIÁLOGO e fica SÓ EM MEMÓRIA: não é gravado no
  projeto, não vai para o .ck, não aparece em log — só o código HTTP da
  resposta é registrado no Console.

## Workflow de exemplo (`exportar-jogo.yml` no repositório)

```yaml
name: Exportar jogo
on:
  workflow_dispatch:
    inputs:
      projeto:
        description: Nome do projeto .kenkern
        required: true
jobs:
  exportar:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: actions/upload-artifact@v4
        with:
          name: kenkern-projeto
          path: ${{ github.event.inputs.projeto }}
      # A exportação completa (projeto embutido num APK jogável) entra no
      # fechamento da Fase 12 (ROADMAP) — este dispatch já entrega o projeto
      # ao runner para o build assinado.
```

## Passos

1. No repositório, criar `exportar-jogo.yml` (acima) na branch que você
   indicar no diálogo.
2. No app: Editor → botão "Exportar jogo" → preencher dono/repo, arquivo do
   workflow, branch e o token → "Disparar exportação".
3. Sucesso = "OK — dispatch aceito": acompanhar em Actions → workflow run →
   artifacts. Falha = mensagem com o código HTTP e o que conferir.
