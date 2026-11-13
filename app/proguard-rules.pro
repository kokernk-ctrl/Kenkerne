# Regras ProGuard da KENKERN (fase 1: sem minificação em release).
# Preserva classes referenciadas por reflexão do SDK Android.
-keep class com.kenkern.engine.** { *; }
