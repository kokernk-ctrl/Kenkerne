# Regras ProGuard/R8 da KENKERN (v0.1.3-perf: R8 ativo no release).
#
# Segurança: a engine não usa reflexão sobre as próprias classes (registro de
# funções é por referência direta, serialização .ck/.KERN é manual). Mesmo assim,
# as classes da engine são preservadas para eliminar risco de regressão em
# dispositivo — o R8 ainda encolhe bibliotecas (Compose/Coroutines) e recursos,
# além de aplicar otimizações no código de suporte.
-keep class com.kenkern.engine.** { *; }

# AUDITORIA v0.1.15 (V1): nenhuma regra -keep nova era necessária PARA A ENGINE.
# CORREÇÃO D2 (v0.1.16): essa conclusão ("zero reflexão, R8 refutado") valia só
# para o código com.kenkern.engine — para o LuaJ ela estava ERRADA. Prova no
# bytecode do luaj-jse 3.0.1 (javap):
#   1. Bit32Lib.call() resolve a classe interna por NOME
#      (Class.forName "org.luaj.vm2.lib.Bit32Lib$Bit32LibV" — padrão class$ do
#      javac) e passa a Class a LibFunction.bind;
#   2. LibFunction.bind() instancia cada função de biblioteca com
#      Class.newInstance() — REFLEXÃO em TODAS as libs (bit32, math, string...).
# No release minificado o R8 renomeava/encolhia org.luaj.* (141 classes → a2.*)
# e o newInstance quebrou no aparelho (exceção em Bit32Lib$Bit32LibV na
# checagem "release", Mali-G57). Regra abaixo mantém TODAS as classes e
# membros do LuaJ: custo pequeno (~0,3 MB de dex) e elimina a classe inteira
# de risco de reflexão no runtime de script.
-keep class org.luaj.** { *; }

# AUDITORIA D2 (v0.1.16) — TERCEIROS por reflexão/carregamento por nome:
#   - org.luaj (luaj-jse 3.0.1): REFLEXÃO comprovada no bytecode
#     (LibFunction.bind → Class.newInstance; LuajavaLib → Class.forName para
#     classes Java expostas ao script; PackageLib → carga por nome) → -keep
#     acima. O sandbox da engine NÃO instala os/io/luajava, mas Bit32Lib/
#     MathLib/StringLib/TableLib/CoroutineLib/JseBaseLib USAM bind;
#   - org.json: SOMENTE testImplementation (parser glTF usa o org.json nativo
#     do Android) — não entra no APK, sem regra;
#   - mockito-core: SOMENTE testImplementation — não entra no APK, sem regra;
#   - androidx.* / kotlinx.coroutines: publicam consumer-rules próprios
#     (R8-safe); sem carregamento por nome na engine.

# Stack traces legíveis nos logs anti-crash (Log.e do modo seguro).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# LuaJ (Fase 3): o artefato luaj-jse inclui a ponte JSR-223 (org.luaj.vm2.script.*)
# que referencia javax.script.*, inexistente no Android. A engine NÃO usa essa
# ponte (usamos Globals/LuaC direto) — R8 pode ignorar as referências ausentes.
-dontwarn javax.script.**
-dontwarn org.luaj.vm2.script.**

