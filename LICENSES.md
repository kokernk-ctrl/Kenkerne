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
