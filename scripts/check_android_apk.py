#!/usr/bin/env python3
"""Inspeciona o APK construído (não as bibliotecas intermediárias do NDK)."""
import struct
import sys
import zipfile


def verify(path):
    with zipfile.ZipFile(path) as apk, open(path, 'rb') as raw:
        libs = [e for e in apk.infolist() if e.filename.startswith('lib/') and e.filename.endswith('.so')]
        if not any(e.filename == 'lib/arm64-v8a/libkenkern.so' for e in libs):
            raise ValueError('libkenkern.so arm64 ausente')
        for entry in libs:
            data = apk.read(entry)
            if data[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H', data, 18)[0] != 183:
                raise ValueError(f'{entry.filename}: esperado ELF64 AArch64 little endian')
            offset = struct.unpack_from('<Q', data, 32)[0]
            size, count = struct.unpack_from('<HH', data, 54)
            alignments = []
            for i in range(count):
                row = offset + i * size
                if struct.unpack_from('<I', data, row)[0] == 1:  # PT_LOAD
                    file_offset, vaddr = struct.unpack_from('<QQ', data, row + 8)
                    align = struct.unpack_from('<Q', data, row + 48)[0]
                    if align < 16384 or (vaddr - file_offset) % 16384:
                        raise ValueError(f'{entry.filename}: PT_LOAD não compatível com 16 KiB')
                    alignments.append(align)
            if not alignments:
                raise ValueError(f'{entry.filename}: nenhum segmento carregável')
            if entry.compress_type == zipfile.ZIP_STORED:
                raw.seek(entry.header_offset + 26)
                name_len, extra_len = struct.unpack('<HH', raw.read(4))
                if (entry.header_offset + 30 + name_len + extra_len) % 16384:
                    raise ValueError(f'{entry.filename}: desalinhada no APK')
            print(f'{entry.filename}: ELF alinhado ({min(alignments)}), embalagem OK')


if __name__ == '__main__':
    verify(sys.argv[1])
