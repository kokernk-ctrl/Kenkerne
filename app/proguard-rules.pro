# Regras ProGuard/R8 da KENKERN (v0.1.3-perf: R8 ativo no release).
#
# Segurança: a engine não usa reflexão sobre as próprias classes (registro de
# funções é por referência direta, serialização .ck/.KERN é manual). Mesmo assim,
# as classes da engine são preservadas para eliminar risco de regressão em
# dispositivo — o R8 ainda encolhe bibliotecas (Compose/Coroutines) e recursos,
# além de aplicar otimizações no código de suporte.
-keep class com.kenkern.engine.** { *; }

# AUDITORIA v0.1.15 (V1): nenhuma regra -keep nova era necessária. Provas:
#  1. dexdump do APK v0.1.14 = 3.441 classes, TODAS as 22 subpackages
#     com.kenkern.engine.* presentes (importador glTF/FBX, KIMG/Etc2,
#     ScriptRuntime, KenkernLog, telas Compose);
#  2. mapping.txt: 141 classes org.luaj.vm2 → a2.* (LuaJ encolhido mas
#     PRESENTE — LuaJScriptRuntime referencia a2.f/a2.m = Globals/LuaError);
#  3. zero Class.forName/getMethod/newInstance/ServiceLoader/Parcelable no
#     código da engine (grep) — nada removível por uso indireto;
#  4. guardas contínuos: checagem "release" no Diagnóstico (Class.forName +
#     operação mínima de glTF/FBX/.KIMG/ETC2/Lua) + ChecagemReleaseTest JVM.

# Stack traces legíveis nos logs anti-crash (Log.e do modo seguro).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# LuaJ (Fase 3): o artefato luaj-jse inclui a ponte JSR-223 (org.luaj.vm2.script.*)
# que referencia javax.script.*, inexistente no Android. A engine NÃO usa essa
# ponte (usamos Globals/LuaC direto) — R8 pode ignorar as referências ausentes.
-dontwarn javax.script.**
-dontwarn org.luaj.vm2.script.**
