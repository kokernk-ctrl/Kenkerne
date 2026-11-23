# F2.11 — carga de assets do viewport

**Validação em aparelho pendente.** Os testes JVM exercitam a fila, não o driver GLES.

A cena editável recebe uma raiz antes de ser publicada. Play usa a raiz da cena de origem, sem publicar a cópia. Cada renderer mantém seus próprios handles GL. A troca de projeto invalida os handles e cancela a demanda; a perda de EGL descarta nomes antigos sem enviá-los ao novo contexto. Undo/redo invalida somente os caminhos da importação. Texturas de cache por conteúdo não são removidas no undo; caminhos recuperados durante outra importação podem voltar à fila.

A demanda de assets visíveis é limitada a 32 por renderer; um worker compartilhado aceita até quatro tarefas em espera, e cada renderer mantém uma tarefa ativa ou concluída por vez. O worker faz I/O, parse, inflação do KIMG, empacotamento da malha e eventual fallback ETC2 → RGBA8. A GL consome no máximo uma tentativa de upload por frame, incluindo o fallback. Não há promessa de que um upload individual caiba num frame: os uploads ainda são indivisíveis e dependem do driver. Limites de carga: KERN até 32 MiB no disco; KIMG até 64 MiB no disco e em mips decodificados; fallback RGBA8 até 64 MiB. O Console informa asset ausente, inválido ou recusado; a mesma chave não falha repetidamente até ser invalidada ou o contexto ser recriado. Enquanto pendente, a malha continua selecionável pela AABB; a textura cai para a cor do material.

## Roteiro no aparelho

1. Abra um projeto com várias malhas texturizadas, incluindo um asset grande. Gire a câmera enquanto os modelos aparecem. Registre os tempos de frame e confirme num trace que leitura, codecs e empacotamento estão em `Kenkern-asset-decode`, não em `GLThread`.
2. Abra outro projeto com os mesmos caminhos relativos e conteúdos diferentes antes de terminar a carga. Confira que não aparece geometria/textura do primeiro projeto. Repita em Play (e ao voltar à edição).
3. Faça undo/redo de uma importação durante a carga e após a malha pronta; confirme que não há reaparição tardia, e que o redo recupera a malha. Remova/estrague uma cópia de teste de um KIMG: deve restar a cor do material e aparecer uma falha clara por chave no Console.
4. Recrie a superfície/contexto (não basta pausar se EGL for preservado). Confirme que os assets voltam a aparecer, sem handles inválidos. Execute também `cena` e `tex-cena` no Diagnóstico para verificar desenho e pixel offscreen.
5. Registre modelo/driver/aparelho e os piores frames do primeiro upload; não feche F2.11 só com a suíte JVM ou com o APK compilado.
