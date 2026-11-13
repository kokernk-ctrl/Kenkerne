# Regras ProGuard/R8 da KENKERN (v0.1.3-perf: R8 ativo no release).
#
# Segurança: a engine não usa reflexão sobre as próprias classes (registro de
# funções é por referência direta, serialização .ck/.KERN é manual). Mesmo assim,
# as classes da engine são preservadas para eliminar risco de regressão em
# dispositivo — o R8 ainda encolhe bibliotecas (Compose/Coroutines) e recursos,
# além de aplicar otimizações no código de suporte.
-keep class com.kenkern.engine.** { *; }

# Stack traces legíveis nos logs anti-crash (Log.e do modo seguro).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
