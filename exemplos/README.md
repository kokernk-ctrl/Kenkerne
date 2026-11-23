# Exemplo jogável: Teste M1

`teste-m1/` é um projeto CK2 em texto, sem assets externos: chão e parede estáticos, um jogador, um ponto de surgimento e uma câmera seguidora. Para gerar o arquivo **a partir da raiz do repositório**:

```sh
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
root = Path('exemplos/teste-m1')
with ZipFile('teste-m1.kernpack', 'w', ZIP_DEFLATED) as z:
    for file in sorted(root.rglob('*')):
        if file.is_file():
            z.write(file, file.relative_to(root))
PY
```

Copie `teste-m1.kernpack` para o aparelho. No Hub, use **Importar projeto**, selecione o pacote e abra **Teste M1**. Em Play, o jogador deve aparecer no ponto de surgimento e parar no chão. Mova-o em direção à parede e solte o joystick. Após Stop, a posição de edição deve voltar a `(-3, 0.5, 0)`. Faça uma segunda importação do mesmo pacote: o primeiro projeto não deve ser alterado. O roteiro completo está em `TESTES_PENDENTES.md`.

O exemplo verifica o caminho básico com controles padrão. Não demonstra todos os Nicks, nem substitui validação em dispositivo.
