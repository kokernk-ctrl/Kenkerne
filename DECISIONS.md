# KENKERN — DECISIONS

Formato: decisão · motivo · alternativa descartada.

## Arquitetura

1. **Kotlin + Compose (M3) para o editor; GLSurfaceView via AndroidView com loop
   próprio de timestep fixo** — recomposição nunca roda o render. Alternativa:
   Compose GL Canvas (sem controle de timestep e de contexto EGL).
2. **GLES 3.x até a Fase 12; Vulkan na 13, atrás de `RenderBackend`** — troca de
   backend sem reescrever a engine. Alternativa descartada: Vulkan direto (risco
   alto antes do conteúdo existir).
3. **`.ck` texto (CK1) em vez de binário** — legível, versionável, tolerante a
   corrupção linha a linha (autorecuperação). Alternativa: binário compacto
   (ilegível para o usuário; adiado até medir).
4. **`.KERN` binário com quantização (posição u16, normal octaédrica 8:8,
   índices u16/u32, LODs, clusters)** — meta de 100 MB → ≤ 20 MB. Alternativa:
   float cru (5× maior).
5. **Projetos no armazenamento interno + estrutura de pastas por projeto;
   SAF opcional (F2.6)** — app roda sem permissões; SAF para quem quiser pasta
   própria. Alternativa: só SAF (fricção alta para o dia a dia).
6. **LUA com LuaJ atrás de `ScriptRuntime`** (Fase 3) — sem NDK; troca por Lua
   5.4 nativo só se o PROFILER provar gargalo (medição ficará registrada aqui).
7. **LuaJ como dependência** — Java puro, roda na ART sem NDK. Alternativa:
   lua-native via JNI desde o início (contra a decisão 6).

8. **F1 (v0.1.30): interface ProjectStorage com DOIS locais — "App"
   (filesDir/projects, o atual, rápido) e "Aparelho"
   (/storage/emulated/0/Kenkern/Projetos, acesso DIRETO por java.io.File com a
   permissão "Acesso a todos os arquivos" — MANAGE_EXTERNAL_STORAGE no API
   30+, WRITE/READ_EXTERNAL_STORAGE no 26–29)** · motivo: o app é distribuído
   por APK do GitHub (sem Play, que bloqueia ALL_FILES); o usuário pediu pasta
   VISÍVEL para os projetos; File direto = sem indireção de SAF e sem uri
   expirando a cada reboot. Sem a permissão o app SEGUE no "App" e mantém
   .kernpack e SAF (nada trava). Mover entre locais = CÓPIA VERIFICADA
   (mesmos caminhos + tamanhos), original mantido até confirmação ·
   alternativa descartada: só SAF (fricção alta;.uri não persiste em alguns
   fabricantes) e só armazenamento interno (o usuário não vê os arquivos).

## Processo

8. **Plano de fases 2–13 derivado do Prompt Mestre v3** (o texto recebido traz o
   vocabulário e as regras; a ordenação das fases é decisão do engenheiro-chefe):
   persistência/diagnóstico → script → importação → texturas → luz/céu → natureza
   → animação → física/zona → virtualização/IG → UI/diálogos/IA → orçamento →
   Vulkan. Motivo: cada fase depende só das anteriores e entrega um APK testável.
9. **CI como gate de qualidade**: `testReleaseUnitTest` + `assembleRelease`
   assinado a cada push; teste JVM vermelho bloqueia o APK.
10. **KAI fica BLOQUEADO até o usuário fornecer a própria chave de API** — a
    chave mora no Android Keystore, nunca no repo (regra de segurança do projeto).
11. **FBX adiado para depois do glTF/GLB** — glTF é o formato de intercâmbio
    moderno e cobre a meta de compressão; FBX é binário fechado e proprietário.

12. **.KERNPACK como formato portátil (F2.6), projetos moram no armazenamento
    do app** — autosave de 1,5 s não pode depender de ContentResolver por
    arquivo (lentidão/perda em provedores instáveis). Alternativa descartada:
    projeto aberto direto da árvore SAF. Import/export por intent = zero
    permissões de armazenamento.
13. **Checagem "shaders" do Diagnóstico usa shader de teste próprio** em vez
    de GlesBackend.initialize() — initialize() marca o BootTrace
    ("shader compilado") e poluiria o rastreio de crash de uma sessão real.
    Os shaders REAIS da engine são validados ao abrir o editor.
14. **.KERN v1 existente não precisa de migração** — nenhum arquivo válido foi
    jamais gravado (o escritor estourava sempre, LOD ≥1 obrigatório): não
    existem .KERN legados no mundo.

## Realinhamento ao roadmap (2026-11-21 — decisão do usuário)

15. **Ordenação das fases corrigida: LUA = Fase 5 (não 3); Fase 3 = LENTE, LUME,
    colliders e input** — o importador glTF/GLB/FBX, .KIMG (ASTC/ETC2), cache,
    streaming progressivo anti-trave, navegador de arquivos, pré-visualização,
    prefabs e registro de assets são pendências da **Fase 2** (Projeto & Assets).
    O LuaJ existente (antes F3.1) vira **F5.1, trabalho antecipado CONGELADO** —
    proibido crescer código Lua antes da Fase 5. BACKLOG.md renumerado 1:1 com o
    ROADMAP.md. Alternativa descartada: manter a ordenação antiga (contrariava o
    roadmap do Prompt Mestre v3).
16. **Tag `fase-N-ok` exige TODOS os itens de aceite da fase implementados**
    (CI verde) — itens não feitos ficam pendentes e a fase continua ABERTA, sem
    tag. Motivo: a tag `fase-2-ok` foi criada sem cumprir a Fase 2 e foi removida
    (a Release v0.1.13 foi re-titulada "fase ABERTA"; o APK fica, pois o build
    existe). Alternativa descartada: tag parcial + itens pendentes.
17. **Nenhum `runCatching` engole erro em silêncio** — todo `runCatching` em
    caminho de render, formato ou carregamento registra o erro em 3 destinos:
    Log (logcat) + Console (`KenkernLog`, anel em memória) + tela Diagnóstico.
    Motivo: a Fase 1 quase perdeu o torus .KERN para um `runCatching` silencioso
    (LOD_ENTRY_SIZE) — erro invisível = bug invisível. Varredura inicial (62
    ocorrências; 32 silenciosas nas classes críticas): docs/RUNCATCHING_AUDIT.md.
18. **Console de erros = `KenkernLog` (objeto, anel de 200 entradas) exibido na
    tela Diagnóstico e no overlay do editor** — sem dependência nova; JVM-safe
    (log de Android pode ser desligado nos testes). Alternativas descartadas:
    Timber (dependência externa), só logcat (não aparece no aparelho do usuário).
19. **Importador glTF/GLB antes do FBX** (reafirma #11): GLB é o formato de
    intercâmbio moderno, binário aberto e documentado; FBX é proprietário e fica
    para F2.8. Parser JSON do glTF usa `org.json` (nativo no Android;
    `testImplementation org.json:json` só nos testes JVM) — sem dependência nova
    no APK. Alternativa descartada: parser JSON artesanal (risco de bug).
20. **Convenção de UV normalizada para a glTF (origem TOPO-esquerda) e FBX só
    binário (F2.8)** — (a) o importador FBX converte v' = 1 − v na entrada, para
    que TODOS os importadores entreguem UV na mesma convenção ao .KERN (texturas
    da TRAMA/Fase 4 não terão um "modo FBX"); (b) FBX ASCII (texto) e FBX 6.x/5.x
    dão ERRO CLARO pedindo binário 7.x — só o caminho binário 7.1–7.7 é
    implementado (é o que Blender/Maya/Assimp exportam por padrão);
    (c) PreRotation/PostRotation presentes = AVISO no `KenkernLog` e ignorados
    (malha estática não os usa; rigs/animações são da Fase 8). Alternativa
    descartada: manter a convenção nativa de cada formato (misturaria convenções
    no mesmo formato .KERN).

21. **ETC2 em Kotlin puro é o encoder do .KIMG (F2.9); ASTC reservado no formato; encoder ASTC fora do escopo** — (a) ETC2 é OBRIGATÓRIO no OpenGL ES 3.0, então roda em TODO aparelho-alvo; ASTC fica no cabeçalho (ETC2_ASTC_4x4/6x6/8x8 reservados) e a checagem `astc` do Diagnóstico só informa a extensão GL_KHR_texture_compression_astc_ldr (nunca reprova); (b) o encoder implementa os 5 modos da spec (individual, diferencial, T, H, planar) com os stuffs EXATOS do ETCPACK (Ericsson) — incluindo o padding de overflow que força o hardware a escolher o modo certo; (c) qualidade MEDIDA (PSNR, ida e volta encoder→decoder, mip 0): cor sólida 52.9 dB · gradiente 64×64 38.5 dB · senoides 128×128 (canais independentes) 33.5 dB · alfa EAC 52.9 dB — razão de compressão 5.8× (RGB) e 2.9× (RGBA); (d) fallback AUTOMÁTICO: driver recusou ETC2 → decodifica na CPU (Etc2 decoder) e sobe RGBA8 — a textura nunca some; (e) bugs achados e corrigidos no caminho: ordem dos blocos é LINHA-MAJOR (hardware), quantização com arredondamento (truncar colapsava médias baixas), planar quantiza o código 6/7/6 (clampar quebrava gradientes). Alternativa descartada: ASTC encoder em Kotlin (fora do escopo desta etapa; segue para fase futura com a extensão detectada).

22. **Camada de compressão EM DISCO do .KIMG = Deflate do JDK, nível 6, LIGADA POR PADRÃO na importação (auditoria v0.1.15, V2.4)** — (a) formato VERSIONADO: arquivo sem camada continua versão 1 byte-idêntico; com camada = versão 2 + flag bit3 no header; o leitor aceita 1 e 2 e valida tamanho esperado por formato (limite HARD de inflação, erro claro em truncado/corrompido); a decisão é POR ARQUIVO (só grava v2 se o total ficar menor); (b) POR QUE adotar: em modelos REAIS do Khronos (DamagedHelmet, FlightHelmet, Sponza) o ETC2+Deflate ganhou 54,7% / 61,2% / 37,7% de disco vs ETC2 cru — a camada nunca engordou um arquivo nos testes; (c) POR QUE nível 6 (medido nas texturas reais 2048×2048 do DamagedHelmet): nível 9 ganha ≤0,5 ponto percentual custando 1,6–2,7× o tempo de gravação (223–283 ms vs 106–172 ms); nível 1 perde 2–4 pp — 6 é o equilíbrio; (d) CUSTO DE CARGA medido: inflar na leitura = 8–16 ms por textura 2048×2048 (desprezível perto do decode PNG/JPEG e do upload GL); o cache por hash evita re-encodar ETC2 ao reabrir o projeto; (e) DISCO vs GPU (medido): a camada melhora SÓ o tamanho em disco; a memória de GPU melhora por causa do ETC2 (8/16 bits por pixel) — nos 3 modelos com texturas: GPU ETC2 5,3/23,3/31,3 MB vs RGBA8 42,7/181,3/234,7 MB (87% de economia em todos). Limitação honesta: com origem JPEG/PNG já comprimida, a razão origem→assets caiu abaixo de 5× em 3 dos 4 modelos (ver CHANGELOG v0.1.15) — alavancas futuras listadas no relatório da auditoria.

23. **-keep para TODO o org.luaj.** (D2, v0.1.16) — a conclusão da auditoria V1 ("zero reflexão, R8 refutado") valia SÓ para o código com.kenkern.engine e estava ERRADA para o LuaJ. Prova no bytecode do luaj-jse 3.0.1 (javap): `Bit32Lib.call()` resolve as classes internas por NOME (`Class.forName "org.luaj.vm2.lib.Bit32Lib$Bit32LibV"`, padrão class$ do javac) e `LibFunction.bind()` instancia cada função de biblioteca com `Class.newInstance()` — reflexão em TODAS as libs (bit32/math/string/table). No release o R8 renomeava org.luaj.* (141 classes → a2.*) e o newInstance quebrou no aparelho Mali-G57 (exceção em Bit32Lib$Bit32LibV na checagem "release"). Correções: (a) `-keep class org.luaj.** { *; }` com motivo no proguard-rules.pro (custo ~0,3 MB de dex); (b) auditoria dos TERCEIROS: org.json e mockito são testImplementation (não entram no APK); androidx/kotlinx publicam consumer-rules próprios — LuaJ era o único reflexivo no APK; (c) a checagem "release" agora roda um script Lua REAL (print + função com math, bit32 e string) e confere o VALOR retornado (28 = floor(abs(-2.5)+4.5) + bor(lshift(1,4),3) + len(format("%x",19)) = 7+19+2; +1 no on_update = 29) — um script que só soma floats passaria e esconderia o bug; (d) por que o CI NÃO roda a suíte contra o JAR minificado pelo R8: o R8 produz dex/ART para o runtime Android, não um JAR da JVM — unit tests JVM rodam sobre as classes compiladas; rodar contra o artefato minificado exigiria instrumentação em emulador (connectedAndroidTest, indisponível no CI atual) — a prova definitiva continua sendo a checagem "release" no APARELHO (TESTES_PENDENTES.md).

## Adendo v4 — NICKS, IDIOMAS, UI, MUNDO (2026-11-22, decisão do usuário)

24. **Tipo de Nick = preset de DADOS em um registro (`NickKindRegistry`), não
    classe de código por tipo** — cada tipo é um registro com chave estável em
    inglês (ex.: `body.player`), rótulos en/pt, ícone, grupo, classes padrão e
    filhos padrão. Motivo: o catálogo tem 80+ tipos; uma classe Kotlin por tipo
    explosionaria o código sem ganho. Alternativa descartada: hierarquia de
    classes `NickPlayer : Nick` (acoplamento e custo de manutenção).

25. **O `.ck` guarda a CHAVE estável do tipo em inglês, nunca o rótulo —
    formato CK2 com o campo `kind`** — CK1 (sem kind) carrega com
    `kind = nick.empty`, ou `nick.mesh` se o Nick tem a classe Malha; o parser
    antigo continua funcionando (DECISIONS #3 preservada: texto, versionável,
    tolerante a corrupção linha a linha). Motivo: rótulo muda com o idioma;
    chave estável garante projetos portáveis entre idiomas e versões.
    Alternativa descartada: gravar o rótulo (quebraria ao trocar idioma).

26. **`name` nulo = exibe o rótulo do tipo no idioma atual; renomeação do
    usuário é gravada e NUNCA traduzida** — migração CK1→CK2: nomes automáticos
    antigos ("Mundo", "Cubo", "Nick N") viram nulos quando exatamente iguais ao
    padrão do template; nome diferente do padrão é do usuário e fica. Motivo:
    nomes automáticos devem seguir o idioma do app; só texto digitado pelo
    usuário pertence ao projeto. Alternativa descartada: manter "Mundo"/"Cubo"
    fixos em português no template (deixaria o app meio traduzido).

27. **Idiomas do app: Inglês (en) e Português (pt), recursos Android
    (`res/values/strings.xml` + `res/values-pt/strings.xml`)** — padrão = idioma
    do aparelho; seletor no Hub (e futuro painel Configurações) com troca
    imediata (`recreate` da Activity com `createConfigurationContext`). Toda
    string de interface vem de recursos; o CI falha se houver texto literal em
    `Text("...")` nas telas (fora da lista de exceções do script
    `scripts/check_i18n.sh`: dados técnicos/monoespaçados do overlay de debug,
    formatos numéricos e siglas). Traduções cobrem tipos de Nick, classes,
    campos do Inspetor, mensagens e relatórios (cada sistema migra para o
    recurso ao tocar em strings). Alternativa descartada: sistema de tradução
    próprio em Kotlin (reinventar o que o Android resolve por recurso).

28. **A janela "Adicionar Nick" lista SOMENTE tipos IMPLEMENTADOS; o catálogo
    completo vive em `NICKS.md`** (status PLANEJADO/IMPLEMENTADO/VALIDADO por
    tipo; um tipo só muda de status com o sistema dele existindo). Motivo:
    prometer na UI o que não existe quebra confiança; o documento é a fonte da
    verdade. Alternativa descartada: listar tudo com badge "planejado" (ruído).

29. **Nicks de UI (2D) ficam numa árvore PRÓPRIA do TELAR (arquivos em `ui/`);
    a Hierarquia 3D mostra apenas um Nick de Tela** — motivo: a árvore 3D
    continua enxuta (cena); a árvore 2D tem outro modelo (camadas, ordem Z,
    paralaxe). Alternativa descartada: uma árvore só com nós 2D e 3D misturados.

30. **Modelar (FORJA), Animar (LINHA/OSSATURA) e Texturizar (TRAMA) são ABAS
    (workspaces) do editor que REAPROVEITAM o viewport e trocam os painéis** —
    seletor na TopBar; o Hub tem atalhos que abrem a aba num projeto "Sandbox"
    criado automaticamente. Painéis continuam acopláveis, redimensionáveis e
    ocultáveis. Alternativa descartada: uma Activity/tela por workspace (perde
    o viewport compartilhado e o estado de painéis).

31. **Input de tela (joystick/botões) é Nick de UI com propriedades editáveis
    e CAMPO "Controla" sem script** — o Joystick Nick expõe x/y/força/ângulo/
    pressionado (eventos on_press/on_move/on_release), multitoque com posse de
    dedo por controle (cada dedo pertence ao controle onde começou), toque em
    dp, zero alocação por frame e nunca bloqueia a câmera do editor fora do
    Play. O campo "Controla" liga o joystick a um Nick (normalmente Player
    Body): movimento relativo à câmera ou ao mundo, velocidade, girar o
    personagem para a direção, ação do toque (pular/atacar) para botões; o
    segundo joystick pode controlar câmera/olhar/mira. Modo "Seguir Nick"
    ancora a base na posição do Nick projetada na tela (vale para rótulos,
    barras de vida e ícones de UI). AÇÕES de input nomeadas (mover, pular,
    atacar) são o vocabulário comum de joystick, botões, teclado e controle
    Bluetooth; Lua (`input.joystick("nome")`, sem alocar tabela) e LINKERF
    (portas de saída) leem AÇÕES, não dispositivos. Alternativa descartada:
    input global lendo estado de controles por nome mágico (sem editor, sem
    ligação visível).

## Correções históricas (resumo; detalhes no CHANGELOG)

- v0.1.12: yaw do voo lia `yawDegrees` do orbital em `computeBasis` (causa (b)).
- v0.1.11: zoom preso = dedo morto reancorado no `ACTION_POINTER_UP`.
- v0.1.8: NPE de inicialização do TransformGizmo (ordem de propriedades Kotlin).

## Portões de CI (v0.1.32 — HARNESS.md)

1. **Ratchet em `gates/baseline.json`**: o baseline guarda o número PROVADO de
   classes de teste, métodos `@Test`, checagens do Diagnóstico e `@Ignore`.
   Diminuir reprova; subir também reprova até o baseline ser aumentado no
   mesmo commit — o número nunca é ajustado para baixo para passar.
2. **KVM**: o job do Portão 2 faz uma sonda (`/dev/kvm` + grupo `kvm`) antes
   de instalar o emulador. Com KVM: API 34 x86_64, `swiftshader_indirect`,
   cache do AVD e `connectedDebugAndroidTest`. Sem KVM: o MESMO corpo de
   teste roda com Robolectric na JVM, com as checagens nativas/GL na lista de
   AVISO (o runner não tem GPU nem a `.so` nativa). O modo usado vai no
   CI-RESUMO.md.
3. **Prova do fix**: para commits `fix`/`corrige` com arquivo de teste, o CI
   monta um worktree no commit PAI, copia só os testes tocados e os roda. Se
   todos passarem, o CI falha com "teste não reproduz o bug". Limite
   documentado: teste que não compila no pai (API nova) conta como reprodução.
4. **Release**: só o workflow cria, em tag `vX.Y.Z`, com Portão 1, Portão 2 e
   REPORT.md verdes e a tag igual ao `versionName`. Release manual é proibida
   (HARNESS.md §4).
5. **Palavras proibidas**: o varredura é no arquivo inteiro (não só no diff) —
   `CHANGELOG.md`, `PROGRESS.md`, `TESTES_PENDENTES.md`, `DECISIONS.md` e
   `REPORT.md`. Um documento antigo com uma palavra proibida reprova o build até ser
   reescrito com fato ou `NÃO VERIFICADO`.

## Limite do Portão 2 sem KVM (v0.1.32)

O teste Robolectric do Portão 2 cobre o fluxo real (criar projeto, scripts,
Nicks, Play, salvar/reabrir) e a física esperada-falhar. A checagem completa do
Diagnóstico NÃO roda nesse runner: a checagem "nativo" instala o handler de
SIGSEGV do C++ e o HotSpot usa SIGSEGV para checagem implícita de nulo — a JVM
de teste morre com exit 139 (o mesmo mecanismo do E1). No emulador (caminho
principal) o Diagnóstico roda inteiro. Esse limite é aceito porque o fallback
só existe quando o runner não tem KVM, e o modo usado vai no CI-RESUMO.md.
