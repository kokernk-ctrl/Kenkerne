# KENKERN 1.0 — auditoria read-only de assets, importação e núcleo nativo

**Data:** 23/11/2026 · **Branch:** `dev/1.0-estabilizacao` · **HEAD:** `1ac8474` (`feat: adiciona Nicks jogáveis ao primeiro recorte do M1`).
**Limite:** revisão estática e verificações locais; não reproduzi crashes/exploits, não alterei arquivos do repositório nem fiz teste em aparelho.

## Resumo executivo

O código tem uma base razoável para importar glTF/GLB e FBX, converter imagem para KIMG/ETC2, serializar KERN e usar etcpak/meshoptimizer por JNI com fallback Kotlin. Há testes de ida-e-volta, fixtures, comparação nativo×Kotlin, limites declarados e diagnósticos. Contudo, “estável 1.0” ainda não está demonstrado: a suíte Android não foi executada neste ambiente (SDK ausente), não há validação local no aparelho, há falhas independentes de fidelidade de glTF e uma falha no caminho de fallback de textura. A Fase 2 permanece aberta pelo próprio backlog.

## Inventário e dependências

- Nenhum arquivo-fonte de modelo/imagem (`.glb/.gltf/.fbx/.obj/.kern/.kimg`, PNG/JPEG etc.) está versionado; os ativos são fixtures em Kotlin ou gerados/fornecidos pelo usuário. Código de formatos/importação está em `app/src/main/java/com/kenkern/engine/assets/{image,import,mesh}/`; carregamento/upload em `render/{AssetMeshLibrary,TextureLibrary,GlesBackend}.kt`.
- JNI: `app/src/main/cpp/{kenkern_native,kenkern_etc2,kenkern_malha}.cpp`; build em `CMakeLists.txt` e `app/build.gradle.kts`, ABI intencionalmente restrita a `arm64-v8a`, NDK 27.3.13750724/CMake 3.22.1.
- Vendors: `third_party/etcpak` (BSD-3-Clause, `LICENSE.txt`, commit registrado em `LICENSES.md`) e `third_party/meshoptimizer` (MIT, `LICENSE.md`, commit registrado). CMake compila apenas os módulos listados em `CMakeLists.txt`.
- Dependências Gradle têm versões no `gradle/libs.versions.toml`: AndroidX/Compose, coroutines e LuaJ são de runtime; JUnit/Mockito/org.json são de teste. Não encontrei lock/verification metadata nem inventário de avisos transitive de licença; convém acrescentar SBOM/notice de release, sem concluir violação atual.
- Formatos: KERN v1/v2 (`KernReader/Writer.kt`); KIMG v1/v2 (`KimgFormat.kt`, Deflate opcional); ASTC é apenas ID reservado/detecção, não encoder (BACKLOG). FBX externo/não embutido é limitação comunicada.

## Achados confirmados

1. **P1 — normais glTF incorretas sob escala não uniforme.** `GltfImporter.kt:719–723` transforma NORMAL pela mesma matriz linear usada em posição; `:739–743` apenas normaliza. Normais exigem a inversa-transposta da parte 3×3 para transformações não uniformes. Modelos sem NORMAL têm normais de face calculadas no espaço transformado (`:745–757`), então o defeito afeta o caminho com normais fornecidas. **Evidência estática**, sem reprodução/aparelho. Falta fixture com normal + escala não uniforme.
2. **P1 — `normalized` de accessor glTF ignorado.** `GltfImporter.kt:357–376` converte componentes inteiros em float com o valor bruto e não consulta `accessor.normalized`. Normal/UV codificados como byte/short normalizado (permitidos pelo formato) serão importados com escala/intervalo errado. Testar u8/u16 normalized UV e i8/i16 normalized NORMAL, além de rejeitar combinações inválidas.
3. **P2 — fallback ETC2→RGBA8 não cria mipmaps.** `GlesBackend.kt:919–939` decodifica e envia somente mip 0 quando upload ETC2 falha; em seguida `:947–949` configura `GL_LINEAR_MIPMAP_LINEAR`. A textura fica incompleta para esse filtro e pode não amostrar corretamente, contrariando o contrato de fallback. Corrigir gerando/enviando cadeia completa ou usar filtro sem mipmaps nesse ramo; teste de diagnóstico simulando recusa de mip ETC2.
4. **P2 — leitor KIMG pode não progredir no Deflate.** `KimgFormat.kt:203–219` repete `Inflater.inflate()` até `finished`, mas o caso `n == 0 && needsDictionary()` não termina nem falha (só é tratado `needsInput`). Adicionar guarda para todo retorno sem progresso e limite/validação de offset com soma em `Long` (`:134–139`, hoje `offset + tamanho` pode transbordar `Int`). Não executei arquivo adversarial.

## Suspeitas / lacunas a confirmar

- `KernReader.read` (`KernReader.kt:32–55, 93–118`) aloca com contagens declaradas antes de validar contagens não negativas, tamanho mínimo/total, índice válido, ranges de LOD/material e `clusterSize`; campos truncados viram exceções genéricas e contagens absurdas podem consumir memória. `readHeader` também lê sem pré-checar os 80 bytes. Endurecer parser antes de aceitar KERN externo/arquivado; definir limites baseados em tamanho do arquivo e validar tudo em pré-passagem.
- FBX ASCII recursa via `lerListaTopo()` e volta a criar profundidade fixa (`FbxImporter.kt:235–245, 346–354`), ao contrário do parser binário limitado em `:478–480`; risco estático de stack overflow com árvore profunda. Não reproduzido.
- Import ainda materializa o arquivo e geometria em memória; ZIP lê entradas para mapa em memória (`ModelImporter.kt:50–78`) e processo não pode cancelar durante parse (`:162–165`). É lacuna de performance já refletida em F2.11, não bug novo confirmado.
- Compilação host com `-Wall -Wextra` passou, mas `etcpak/ProcessRGB.cpp` emitiu alertas de shift overflow, signedness e possíveis variáveis não inicializadas (incl. linhas 2490, 3806–3852). São fontes vendorizadas e não provei falha funcional; comparar upstream e flags/toolchain NDK antes de qualquer patch. Não modificar vendor sem registrar patch/licença.

## Verificações e testes

- `./gradlew testReleaseUnitTest --no-daemon`: **não iniciou**; configuração falhou porque `ANDROID_HOME`/`ANDROID_SDK_ROOT` vazios e `local.properties` ausente (`SDK location not found`). Não contar os relatórios de build preexistentes como execução deste audit.
- Inventário: 38 classes de teste, 246 marcadores `@Test`; cobertura relevante em `assets/image/{KimgTest,Etc2NativoComparacaoTest,Etc2QualidadeParaleloTest,PerfilTexturaImportacaoTest}`, `assets/import/{GltfImporterTest,FbxImporterTest,ModelImporterTest,PosProcessamentoTest}`, `assets/mesh/KernRoundtripTest`, `nativecore/*ComparacaoTest` e `diag/*`.
- `bash scripts/check_i18n.sh`: passou.
- Compilei fora do repo todos os wrappers C++ + os módulos vendorizados para `/tmp/kenkern-audit/libkenkern-host.so`: compilou, com os avisos etcpak acima. `scripts/check_jni_symbols.sh` contra essa .so host verificou os 8 símbolos JNI e passou; isso **não** substitui teste do APK arm64/CI.
- `git status --short --branch` permaneceu limpo; nenhuma mudança no repositório. Sem aparelho: falta checar GPU GLES3, memória/latência, abertura/reabertura, fallback real e aceite manual.

## Roadmap de estabilidade (prioridade sugerida)

- **P0 — fechar baseline antes de correções:** provisionar SDK/JDK conforme CI; executar `testReleaseUnitTest`, `assembleRelease` e validação JNI na .so do APK. Depois, aparelho arm64/GLES3: import/reabertura glTF+FBX com texturas, cancelamento, ETC2/fallback, projeto antigo/novo, relatório Diagnóstico. Marcar separadamente COMPILADO e VALIDADO-NO-APARELHO.
- **P1 — ativos confiáveis:** corrigir os dois achados de semântica glTF e fallback KIMG; criar testes de regressão. Acrescentar validação de limites/consistência KERN/KIMG e teste de erro/truncamento sem alocação excessiva. Não publicar 1.0 enquanto essas garantias e a matriz de aparelho não passarem.
- **P2 — conclusão funcional pendente conforme `BACKLOG.md`/`ROADMAP.md`:** M1 aparece como prioridade corrente em `PROGRESS.md`; concluir e validar M1. T0.4 (PBR completo) está parcial, T0.5 TODO. Fase 2 continua aberta: F2.11 streaming, F2.12 navegador, F2.13 preview, F2.14 prefabs e F2.15 IDs/referências quebradas. Resolver/documentar a ordem: ROADMAP põe T0.4/T0.5 antes de M1, enquanto PROGRESS diz prioridade M1.
- **P3 — release 1.0:** congelar escopo e critérios, build limpa/reprodutível, dependências/notices, suíte + device matrix + teste de migração e importação, regressão/crash diagnostics, changelog e status/tag coerentes. A versão atual no `app/build.gradle.kts` é `0.1.19-fase2` (`versionCode=20`), não 1.0.

## Próxima correção focada sugerida (sujeita à aprovação)

Um commit estreito em `GltfImporter`: ler `accessor.normalized` e transformar normals com inversa-transposta 3×3 (com tratamento explícito para matriz singular); adicionar fixtures/testes de u8/u16 UV normalizado e normal sob escala não uniforme, verificando valores/range e orientação. Não incluir PBR, parser KERN ou outras melhorias no mesmo commit.
