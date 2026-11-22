# KENKERN — LICENÇAS DE TERCEIROS (third_party/)

Todo código de terceiros vendorizado vive em `third_party/` e tem a licença
registrada AQUI. Nenhuma outra dependência de código-fonte existe além das
bibliotecas do Gradle (que publicam suas próprias licenças).

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
