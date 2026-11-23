# Auditoria e preparação da primeira versão estável

Base imutável: `1ac847461154c6b78a683d6e9b0cdcbcd66a4df0` em `main`, 23/11/2026. Branch de trabalho: `dev/1.0-estabilizacao`. Sem tag nem alteração do número de versão. Os relatórios `base-*.md` são fotografias **anteriores** às correções, não o estado do HEAD da branch. [Inventário de todos os caminhos versionados](inventario.tsv); [roadmap fase a fase](base-roadmap.md); [contrato proposto e gates](ACEITE.md).

| Subsistema | Classificação na base | Evidência e limite |
|---|---|---|
| Gradle/JDK17/NDK, empacotamento Android arm64 e JNI | WORKING | debug e release gerados; oito símbolos conferidos na `.so` do APK. Assinatura local Android Debug não é de distribuição. |
| Pipeline de distribuição / CI | PARTIALLY IMPLEMENTED | Workflow de push existente, sem gate de PR; lint reportou erros sem impedir sucesso do Gradle. |
| Kotlin math, transform, registry, CK1→2 e serialização comum | WORKING (JVM, caminhos cobertos) | Testes de matemática/round-trip. Não prova segurança entre threads ou recuperação de toda corrupção. |
| Projetos, recuperação e histórico | BROKEN nos caminhos de perda de dados | Cena com 1 Nick, autosave mais novo em branco → `loadScene` com 0; pai com filho, `removeNick`/`attachSubtree` → 0 filhos. [Cena/undo](base-scenes.md). |
| Renderer GLES3, câmera, gizmos, seleção, textura/malha | PARTIALLY IMPLEMENTED | [8 pares GLSL + readback real no host](base-gles.md), mas ownership e perda de contexto problemáticos; não é driver Android. [Análise](base-render.md). |
| Gestão de recursos, formatos, importação, JNI ETC2/meshopt | PARTIALLY IMPLEMENTED | Fixtures, round-trips e testes nativo/Kotlin. Limites e bugs de fidelidade detalhados em [assets](base-assets.md); sem streaming completo, preview ou LRU. |
| Shell Compose, Hub/SAF e ciclo Activity↔GL | PARTIALLY IMPLEMENTED | Fluxos no código, aceite anterior do layout; reformulação do Hub pendente. Atualização/SAF e perda de contexto desta revisão UNKNOWN — NOT YET VERIFIED no alvo. |
| Edit/Play, toque, cenas e componentes (Nicks) | PARTIALLY IMPLEMENTED | Recorte M1 com colisão de caixa, spawn/jogador/câmera. 77 entradas de catálogo não são 77 comportamentos executáveis. |
| LINKERF, Lua, sinais/input | PARTIALLY IMPLEMENTED | Grafo serializado, sandbox com testes; undo destrutivo, API e componentes planejados não completos. |
| Diagnóstico, i18n e crash reporting | PARTIALLY IMPLEMENTED | i18n, fixtures e checagens locais; smoke da versão minificada, sinais e coleta no hardware ainda necessários. |
| Export de jogo independente, sistemas avançados das fases posteriores | MISSING como entregas completas | Diferenciar APK do editor e saída do jogo. Cobertura fase a fase no roadmap; protótipo/parte antecipada não fecha fase. |
| Performance, consumo, estabilidade prolongada no aparelho | UNKNOWN — NOT YET VERIFIED | Sem dispositivo ADB/arm64 nesta rodada, sem números de memória/tempo de frame atribuíveis a Android. |

**Baseline central**: `bash scripts/check_i18n.sh`; `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=$HOME/Android/Sdk bash gradlew testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease lintRelease --rerun-tasks`: BUILD SUCCESSFUL, 108 tarefas executadas, 5m06s, 245 passaram e 1 ignorado **em cada variante**. XML do lint: **2 erros, 32 avisos, 10 informativos**; não foi lint limpo. Sem plugin de cobertura percentual/formatter dedicado, sem `androidTest`. A falha de descoberta do SDK em um shell de auditoria de assets não impediu a execução central. Não confundir checagem de símbolos da biblioteca host com a checagem adicional do APK Android.
