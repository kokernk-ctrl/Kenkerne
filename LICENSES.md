# KENKERN — LICENÇAS DE TERCEIROS (third_party/)

Todo código de terceiros vendorizado vive em `third_party/` e tem a licença
registrada AQUI. Nenhuma outra dependência de código-fonte existe além das
bibliotecas do Gradle (que publicam suas próprias licenças).

## meshoptimizer — `third_party/meshoptimizer/`

- **Upstream**: https://github.com/zeux/meshoptimizer
- **Commit vendorizado**: 8547fc321b782a2235dfc14c65a346a640544cd8 (2026-11-21)
- **Licença**: MIT (`third_party/meshoptimizer/LICENSE.md`)
- **Uso**: dedup de vértices (meshopt_generateVertexRemap), otimização de
  cache de vértices (meshopt_optimizeVertexCache) e LODs por simplificação
  (meshopt_simplify) no núcleo C++ (B6, v0.1.17) — ligado ao `libkenkern.so`
  pelo wrapper JNI `app/src/main/cpp/kenkern_malha.cpp`; subconjunto mínimo
  (só os 3 módulos usados), sem alterações locais (ver
  `third_party/meshoptimizer/README.kenkern.md`).
- **Copyright**: Copyright (c) 2016-2026 Arseny Kapoulkine

Texto da licença (MIT):

```
MIT License

Copyright (c) 2016-2026 Arseny Kapoulkine

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## etcpak — `third_party/etcpak/`

- **Upstream**: https://github.com/wolfpld/etcpak
- **Commit vendorizado**: 3d716e1550023dc5ff8b602636af8b76584e0b66 (2026-09-13)
- **Licença**: BSD-3-Clause (`third_party/etcpak/LICENSE.txt`)
- **Uso**: encoder ETC2 RGB8/RGBA8 nativo (B4, v0.1.17) — ligado ao
  `libkenkern.so` pelo wrapper JNI `app/src/main/cpp/kenkern_etc2.cpp`;
  subconjunto mínimo do upstream (só os arquivos do caminho ETC2), sem
  alterações locais (ver `third_party/etcpak/README.kenkern.md`).
- **Copyright**: Copyright (c) 2013-2026, Bartosz Taudul <wolf@nereid.pl>

Texto da licença (BSD-3-Clause):

```
Copyright (c) 2013-2026, Bartosz Taudul <wolf@nereid.pl>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the <organization> nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## Fontes OFL — `app/src/main/assets/fonts/` (Onda 0, v0.1.28)

As 8 fontes livres do TELAR (lista `FONTES_OFL`) EMBUTIDAS no APK
(`assets/fonts/*.ttf`) e carregadas DE DENTRO do APK em runtime — SEM
download nenhum (funciona em modo avião). Arquivos obtidos do repositório
canônico https://github.com/google/fonts (ramo `main`), todos licenciados
SIL Open Font License 1.1 (texto íntegro abaixo; o texto é o MESMO para as
8 — as licenças por família diferem apenas no cabeçalho de copyright).

- **Roboto.ttf** (488 KB, variável) — Copyright 2011 The Roboto Project
  Authors (https://github.com/googlefonts/roboto-classic) — `ofl/roboto/`
- **RobotoMono.ttf** (184 KB, variável) — Copyright 2015 The Roboto Mono
  Project Authors (https://github.com/googlefonts/robotomono) — `ofl/robotomono/`
- **NotoSans.ttf** (2,0 MB, variável) — Copyright 2022 The Noto Project
  Authors (https://github.com/notofonts/latin-greek-cyrillic) — `ofl/notosans/`
- **NotoSerif.ttf** (1,9 MB, variável) — Copyright 2022 The Noto Project
  Authors (https://github.com/notofonts/latin-greek-cyrillic) — `ofl/notoserif/`
- **OpenSans.ttf** (533 KB, variável) — Copyright 2020 The Open Sans Project
  Authors (https://github.com/googlefonts/opensans) — `ofl/opensans/`
- **Lato.ttf** (657 KB, estática Regular) — Copyright (c) 2010-2014 by
  tyPoland Lukasz Dziedzic (team@latofonts.com), Reserved Font Name "Lato" —
  `ofl/lato/`
- **Montserrat.ttf** (745 KB, variável) — Copyright 2024 The
  Montserrat.Git Project Authors (https://github.com/JulietaUla/Montserrat.git)
  — `ofl/montserrat/`
- **JetBrainsMono.ttf** (187 KB, variável) — Copyright 2020 The JetBrains
  Mono Project Authors (https://github.com/JetBrains/JetBrainsMono) —
  `ofl/jetbrainsmono/`

Texto da licença (SIL Open Font License 1.1 — comum às 8 famílias):

```
Copyright (c) <CADA FAMÍLIA, VER ACIMA>

This Font Software is licensed under the SIL Open Font License, Version 1.1.
This license is copied below, and is also available with a FAQ at:
https://scripts.sil.org/OFL

SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of cooperative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the Font Software.

TERMS & CONDITIONS

Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Contributor(s) of the
Font Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Contributor(s).

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```
