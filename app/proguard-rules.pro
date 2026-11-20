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

# LuaJ (Fase 3): o artefato luaj-jse inclui a ponte JSR-223 (org.luaj.vm2.script.*)
# que referencia javax.script.*, inexistente no Android. A engine NÃO usa essa
# ponte (usamos Globals/LuaC direto) — R8 pode ignorar as referências ausentes.
-dontwarn javax.script.**
-dontwarn org.luaj.vm2.script.**
