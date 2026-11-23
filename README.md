# Kenkerne — editor e engine KENKERN para Android

Crie um projeto no Hub, monte a cena com Nicks no editor em paisagem e use **Play** para testar uma cópia da cena; **Stop** retorna à edição. O projeto é salvo no armazenamento privado do app. Use **exportar projeto** para guardar um `.kernpack` antes de trocar de aparelho ou instalação. O APK é do **editor**, não de um jogo exportado.

Esta branch prepara a estabilidade do projeto; **não é a versão 1.0**. Consulte o [estado técnico e inventário](docs/estabilizacao/README.md), [critérios de aceite](docs/estabilizacao/ACEITE.md) e [roadmap](ROADMAP.md). A [cena de exemplo](exemplos/README.md) tem chão, parede, jogador, spawn e câmera; pode ser importada sem baixar assets.

## Build reproduzível

Use JDK 17, Android SDK Platform 35, Build Tools 34.0.0, NDK 27.3.13750724 e CMake 3.22.1. Exporte `ANDROID_HOME` ou informe `sdk.dir` num `local.properties` **não versionado**. Em Linux, instale também `g++` para os testes do JNI host. Use o wrapper do checkout:

```sh
bash scripts/check_i18n.sh
./gradlew testDebugUnitTest testReleaseUnitTest lintRelease assembleDebug assembleRelease
```

APKs em `app/build/outputs/apk/{debug,release}/`. Sem a configuração de distribuição, o build local é assinado com a chave **debug**. Uma chave diferente não pode atualizar uma instalação existente: não desinstale um editor com projetos sem antes exportá-los. No repositório, nunca grave keystores ou senhas. A ABI distribuída é `arm64-v8a`; o renderer requer GLES 3.0.

O teste opcional de modelos reais recebe `-Dkenkern.modelos.dir=/pasta` (subpastas `DamagedHelmet`, `FlightHelmet`, `Sponza`, `BrainStem`); sem fixtures ele é ignorado explicitamente. O [smoke gráfico no Linux](docs/HOST_GLES_SMOKE.md) verifica os shaders do backend com EGL/GLES e readback, mas não executa Android. Os [testes de aparelho](TESTES_PENDENTES.md) continuam necessários.

## Orientação no código

- [Arquitetura e threads](ARCHITECTURE.md) · [catálogo de Nicks](NICKS.md) · [formato de malha KERN](docs/KERN_SPEC.md).
- [Backlog por fase](BACKLOG.md) · [decisões](DECISIONS.md) · [histórico de mudanças](CHANGELOG.md) · [licenças de dependências](LICENSES.md).
