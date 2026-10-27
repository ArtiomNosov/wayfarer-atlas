# Проверка App Bundle0.1.1 / code2

26 октября2026. Финальный AAB: 4418806 байт; SHA-256 `0b94d2ff4c8cb18cce6cf40793d76a28fa985c2b6246c2dbedb453ba4ee8ee43`.

- Официальный bundletool1.18.1 validate: PASS, exit0.
- dump config: PASS; LANGUAGE negate:true, оба языка вместе.
- Native uncompressed alignment: PAGE_ALIGNMENT_16K.
- ZIP CRC: PASS.
- APK/AAB signatures и ELF/RELRO/zipalign: [artifact-verification.json](artifact-verification.json), `python3 scripts/verify-artifacts.py`.

Jarsigner предупреждает о самоподписанном upload certificate, отсутствии timestamp и manifest в конце AGP ZIP (JarInputStream); подпись через JarFile и структура через официальный bundletool проходят. Это не одобрение Google Play. Runtime на16KB устройстве не проверен.

Повтор в подготовленной локальной среде: `python3 .tooling/bundletool.py validate --bundle=app/build/outputs/bundle/release/app-release.aab`.
