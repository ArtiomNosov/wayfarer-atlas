#!/usr/bin/env python3
"""Build canonical government snapshots from reviewed, attributed source inputs."""
import csv
import hashlib
import io
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent
HK_URL = "https://www.immd.gov.hk/eng/services/visas/visit-transit/visit-visa-entry-permit.html"
HK_COMMON_EN = "Ordinary short visit only. Adequate funds and onward/return ticket required, except onward-ticket exemption for transit to Chinese Mainland or Macao. Visitors may not work, establish/join a business, study or take up residence. Admission remains discretionary."
HK_COMMON_RU = "Только обычное краткое посещение. Нужны достаточные средства и билет для дальнейшего следования или возвращения; для транзита в материковый Китай или Макао действует исключение по билету. Посетителям нельзя работать, учреждать бизнес или участвовать в нём, учиться или переселяться. Допуск не гарантирован."
HK_HEADER = ["National of foreign country (territory)/Type of travel document", "Visa free period for visiting the Hong Kong Special Administrative Region", "Visa requirements"]
HK_NA = "Not applicable"
HK_REQUIRED_VALUES = {
    "All nationals are required to hold a valid visa for the HKSAR for whatever purpose (including those who are in transit and remain on the airside)",
    "All nationals are required to hold a valid visa for the HKSAR for whatever purpose (except those who are in transit and remain on the airside)",
    "All nationals are required to hold a valid visa for the HKSAR for whatever purpose (except those who are in transit and remain on the airside). Holders of Diplomatic and Official passports may visit the HKSAR visa-free for a stay not exceeding 14 days.",
    "All nationals (except holders of Diplomatic and Official passports) are required to hold a valid visa for the HKSAR for whatever purpose (including those who are in transit and remain on the airside). Holders of Diplomatic and Official passports may visit the HKSAR visa-free for a stay not exceeding 14 days.",
    "All nationals (except holders of Diplomatic and Official passports) are required to hold a valid visa for the HKSAR for whatever purpose (including those who are in transit and remain on the airside). Holders of Diplomatic and Official passports may visit the HKSAR visa-free for a stay not exceeding 30 days.",
}
HK_PAR_VALUE = "Indian nationals are required to apply for and successfully complete pre-arrival registration online before they can visit the HKSAR visa free."


def _label_key(value):
    value = re.sub(r"\s*\(please see remarks\)", "", value, flags=re.IGNORECASE)
    value = re.sub(r"\s*\(formerly known as [^)]+\)", "", value, flags=re.IGNORECASE)
    value = unicodedata.normalize("NFKD", value.replace("&", "and"))
    return "".join(character for character in value.casefold() if character.isascii() and character.isalnum())


def hong_kong_mapping(csv_text, countries):
    country_codes = {country["code"] for country in countries}
    aliases = {_label_key(country["nameEn"]): country["code"] for country in countries}
    extra = {
        "RUSSIAN FEDERATION": "RU", "BRITAIN (British citizens)": "GB",
        "U.S.A. (except U.S.A. Diplomatic passports)": "US",
        "CAPE VERDE (REPUBLIC OF)": "CV", "COTE D'IVOIRE (REPUBLIC OF)": "CI",
        "BRUNEI DARUSSALAM": "BN", "MYANMAR": "MM", "SLOVAK REPUBLIC": "SK",
        "CONGO (DEMOCRATIC REPUBLIC OF)": "CD", "CONGO (REPUBLIC OF)": "CG",
        "CYPRUS (REPUBLIC OF)": "CY", "CZECH REPUBLIC": "CZ", "DOMINICA (COMMONWEALTH OF)": "DM",
        "FALKLAND ISLANDS & DEPENDENCIES": "FK", "IRELAND (REPUBLIC OF)": "IE",
        "KOREA (DEMOCRATIC PEOPLE'S REPUBLIC OF)": "KP", "KOREA (REPUBLIC OF)": "KR",
        "LAOS": "LA", "MARSHALL ISLANDS (REPUBLIC OF)": "MH", "MICRONESIA (FEDERATED STATES OF)": "FM",
        "MOLDOVA (REPUBLIC OF)": "MD", "MONTENEGRO ( REPUBLIC OF )": "ME", "PALESTINE (STATE OF)": "PS",
        "PITCAIRN, HENDERSON, DUCIE & OENO ISLANDS": "PN", "SERBIA (REPUBLIC OF)": "RS",
        "ST. HELENA DEPENDENCIES (ASCENSION, TRISTAN DA CUNHA)": "SH",
        "ST. VINCENT AND THE GRENADINES": "VC", "THE SOUTH GEORGIA AND THE SOUTH SANDWICH ISLANDS": "GS",
        "TURKEY": "TR", "TURKS AND CAICOS ISLANDS": "TC", "YEMEN (REPUBLIC OF)": "YE",
        "Tongan National passports": "TO", "Tongan Protected Persons passports": "TO",
        "TUVALU (with national status stated as 'I-TUVALU')": "TV",
    }
    aliases.update({_label_key(label): code for label, code in extra.items() if code in country_codes})
    grouped, unmapped = {}, []
    csv_rows = list(csv.reader(io.StringIO(csv_text.lstrip("\ufeff"))))
    if not csv_rows or csv_rows[0] != HK_HEADER:
        raise ValueError("Hong Kong CSV header or column order changed")
    source_labels = []
    source_as_at = None
    for row in csv_rows[1:]:
        if len(row) < 3:
            continue
        label = row[0].strip()
        if re.fullmatch(r"\(As at [0-9]{1,2} [A-Za-z]+ [0-9]{4}\)", label) and row[1:] == ["", ""]:
            from datetime import datetime
            source_as_at = datetime.strptime(label, "(As at %d %B %Y)").date().isoformat()
            continue
        source_labels.append(label)
        if label.upper().startswith("ALBANIA ("):
            code = "AL"
        elif label.upper().startswith("SERBIA (REPUBLIC OF)"):
            code = "RS"
        else:
            code = aliases.get(_label_key(label))
        if code in {"CN", "TW", "HK"}:
            code = None
        if code is None:
            if _hk_requirement(row[1], row[2]) != ("UNKNOWN", None):
                unmapped.append(label)
            continue
        grouped.setdefault(code, {"code": code, "rows": []})["rows"].append(label)

    notes = {
        "AL": ("UNKNOWN", "Biometric Albanian passports: 14 days visa-free; non-biometric: visa required. Passport subtype is not selected in this app.", "Биометрический паспорт Албании: без визы до 14 дней; небиометрический: нужна виза. Подтип паспорта в приложении не выбирается."),
        "RS": ("UNKNOWN", "Biometric Serbian passports: 14 days visa-free; non-biometric or Coordination Directorate passports: visa required. Passport subtype is not selected in this app.", "Биометрический сербский паспорт: без визы до 14 дней; небиометрический либо выданный Координационным управлением: нужна виза. Подтип паспорта в приложении не выбирается."),
        "TO": ("UNKNOWN", "Source lists Tonga with 90 days, but Tongan National and Tongan Protected Persons passports require a visa. Confirm the precise passport type.", "Источник указывает для Тонга 90 дней, но для Tongan National и Tongan Protected Persons требуется виза. Уточните точный тип паспорта."),
        "TV": ("UNKNOWN", "Source lists Tuvalu with 90 days, but passports stating national status I-TUVALU require a visa. Confirm the precise passport status.", "Источник указывает для Тувалу 90 дней, но для паспорта со статусом I-TUVALU нужна виза. Уточните точный статус паспорта."),
        "IN": ("ETA", "Indian nationals must successfully complete online pre-arrival registration (PAR) before a visit of up to 14 days, unless an official exemption applies. PAR is shown in the electronic-authorisation category.", "Гражданам Индии перед посещением до 14 дней требуется успешно пройти онлайн-регистрацию PAR, если не действует официальное исключение. PAR показана в категории электронного разрешения."),
        "GB": (None, "180 days applies to British citizens. Other British nationality categories are listed separately with 90 days and are outside this ordinary-citizen baseline.", "180 дней — для British citizens. Иные категории британского гражданства перечислены отдельно с лимитом 90 дней и не входят в это базовое правило."),
        "US": (None, "Ordinary passports; US diplomatic passports require a visa.", "Для обычного паспорта; для дипломатического паспорта США нужна виза."),
        "CR": (None, "Excludes provisional passports and Documento de Identidad Y Viaje, which require a visa.", "Не включает временные паспорта и Documento de Identidad Y Viaje: для них нужна виза."),
        "PE": (None, "Excludes special Peruvian passports, which require a visa.", "Не включает специальные паспорта Перу: для них нужна виза."),
        "VA": (None, "Excludes Vatican Service passports, which require a visa.", "Не включает служебные паспорта Ватикана: для них нужна виза."),
        "UY": (None, "Excludes Uruguay passports issued under Decree 289/90, which require a visa.", "Не включает паспорта Уругвая, выданные по Decree 289/90: для них нужна виза."),
    }
    for code, (override, note, note_ru) in notes.items():
        if code in grouped:
            grouped[code].update({"notes": note, "notesRu": note_ru})
            if override:
                grouped[code]["categoryOverride"] = override
    runtime_notes = {
        "AL": ("Requirements depend on biometric/non-biometric passport subtype, which this app does not select. Check the official table.", "Условия зависят от биометрического или небиометрического подтипа паспорта, который в приложении не выбирается. Сверьтесь с официальной таблицей."),
        "RS": ("Requirements depend on passport subtype and issuing authority, which this app does not select. Check the official table.", "Условия зависят от подтипа паспорта и выдавшего его органа, которые в приложении не выбираются. Сверьтесь с официальной таблицей."),
        "TO": ("Requirements depend on the passport category, which this app does not select. Check the official table.", "Условия зависят от категории паспорта, которая в приложении не выбирается. Сверьтесь с официальной таблицей."),
        "TV": ("Requirements depend on the passport's stated national status, which this app does not select. Check the official table.", "Условия зависят от указанного в паспорте национального статуса, который в приложении не выбирается. Сверьтесь с официальной таблицей."),
        "IN": ("If shown as electronic authorisation, this refers to Hong Kong pre-arrival registration (PAR). Check the current official exemptions and conditions.", "Если показано электронное разрешение, имеется в виду предварительная онлайн-регистрация Гонконга (PAR). Проверьте действующие официальные исключения и условия."),
        "GB": ("British citizens only. Other British nationality categories are outside this baseline.", "Только для British citizens. Иные категории британского гражданства не входят в это базовое правило."),
        "US": ("Ordinary passports only; diplomatic passports are outside this baseline.", "Только для обычного паспорта; дипломатические паспорта не входят в это базовое правило."),
        "CR": ("Provisional passports and Documento de Identidad Y Viaje are outside this ordinary-passport baseline.", "Временные паспорта и Documento de Identidad Y Viaje не входят в это базовое правило для обычного паспорта."),
        "PE": ("Special Peruvian passports are outside this ordinary-passport baseline.", "Специальные паспорта Перу не входят в это базовое правило для обычного паспорта."),
        "VA": ("Vatican Service passports are outside this ordinary-passport baseline.", "Служебные паспорта Ватикана не входят в это базовое правило для обычного паспорта."),
        "UY": ("The special Uruguayan passport category listed separately in the official source is outside this baseline.", "Специальная категория паспортов Уругвая, отдельно указанная в официальном источнике, не входит в это базовое правило."),
    }
    for code, (note, note_ru) in runtime_notes.items():
        if code in grouped:
            grouped[code].update({"notesRuntime": note, "notesRuntimeRu": note_ru})
    for entry in grouped.values():
        entry.setdefault("notesRuntime", "")
        entry.setdefault("notesRuntimeRu", "")
    return {"version": 1, "destination": "HK", "sourceUrl": HK_URL,
            "entries": [grouped[code] for code in sorted(grouped)], "unmappedRows": unmapped,
            "header": HK_HEADER, "sourceLabels": source_labels, "sourceAsAt": source_as_at,
            "notApplicableValue": HK_NA, "visaRequiredValues": sorted(HK_REQUIRED_VALUES), "preArrivalRegistrationValue": HK_PAR_VALUE,
            "mappingPolicy": "Exact original CSV cell-0 strings after trimming; no catch-all 7-day rule; no Mainland/Taiwan inference; ambiguous ordinary-passport subtypes remain UNKNOWN."}


def _hk_requirement(days_text, visa_text):
    days_text, visa = days_text.strip(), visa_text.strip()
    days = re.fullmatch(r"([1-9][0-9]*)", days_text)
    if days_text == HK_NA and visa in HK_REQUIRED_VALUES:
        return "VISA_REQUIRED", None
    if days and int(days.group(1)) <= 3660 and visa in {HK_NA, HK_PAR_VALUE}:
        return ("ETA" if visa == HK_PAR_VALUE else "VISA_FREE"), int(days.group(1))
    return "UNKNOWN", None


def hong_kong_rules(csv_text, mapping, checked_at):
    rows = {}
    expected = {label for entry in mapping["entries"] for label in entry["rows"]}
    for row in csv.reader(io.StringIO(csv_text.lstrip("\ufeff"))):
        if len(row) < 3:
            continue
        label = row[0].strip()
        if label not in expected:
            continue
        if label in rows:
            raise ValueError("Duplicate official Hong Kong row: " + label)
        rows[label] = _hk_requirement(row[1], row[2])
    rules = []
    for entry in mapping["entries"]:
        if not entry["rows"] or any(label not in rows for label in entry["rows"]):
            raise ValueError("Official Hong Kong source label changed or is missing for " + entry["code"])
        values = {rows[label] for label in entry["rows"]}
        category, days = values.pop() if len(values) == 1 else ("UNKNOWN", None)
        override = entry.get("categoryOverride")
        if override == "UNKNOWN":
            category, days = "UNKNOWN", None
        elif override not in {None, "ETA"}:
            raise ValueError("Unsupported Hong Kong source override")
        # ETA identifies PAR in this mapping; the current CSV remains authoritative.
        # In particular, a new prior-visa requirement must not be turned back into ETA.
        note = entry.get("notesRuntime", entry.get("notes", "")) if override == "ETA" else entry.get("notes", "")
        note_ru = entry.get("notesRuntimeRu", entry.get("notesRu", "")) if override == "ETA" else entry.get("notesRu", "")
        rules.append({"citizenship": entry["code"], "destination": "HK", "category": category, "maxStayDays": days,
                      "notes": (HK_COMMON_EN + " " + note).strip(),
                      "notesRu": (HK_COMMON_RU + " " + note_ru).strip(),
                      "sourceUrl": HK_URL, "checkedAt": checked_at,
                      "citations": [{"nameEn": "Hong Kong Immigration Department — visit requirements", "nameRu": "Иммиграционный департамент Гонконга — условия посещения",
                                     "url": HK_URL, "checkedAt": checked_at, "official": True}]})
    return sorted(rules, key=lambda rule: rule["citizenship"])


def new_zealand_rules(source):
    provenance = source["provenance"]
    checked_at = provenance["verifiedOnLocal"]
    source_url = provenance["sourceUrl"]
    rules = []
    for item in source["list"]:
        months = source["defaults"]["maxStayMonthsByPassport"].get(item["code"], source["defaults"]["maxStayMonths"])
        notes = (
            f"NZeTA required before travel. Qualifying ordinary tourist visits: up to {months} calendar months; "
            "no more than 6 months in any 12-month period. Calendar months are not a fixed number of days. "
            "Hold an onward ticket and adequate funds; meet passport, health and character requirements. "
            "Entry permission is decided at the border. " + item.get("notes", "")
        ).strip()
        notes_ru = (
            f"До поездки требуется NZeTA. Для соответствующего условиям обычного туристического посещения: до {months} календарных месяцев; "
            "не более 6 месяцев за любые 12 месяцев. Календарные месяцы не равны фиксированному числу дней. "
            "Нужны билет для выезда, достаточные средства и соблюдение требований к паспорту, здоровью и благонадёжности. "
            "Решение о допуске принимает пограничная служба. " + item.get("notesRu", "")
        ).strip()
        rules.append({"citizenship": item["code"], "destination": "NZ", "category": "ETA", "maxStayDays": None,
                      "notes": notes, "notesRu": notes_ru, "sourceUrl": source_url, "checkedAt": checked_at,
                      "citations": [{"nameEn": "Immigration New Zealand — visa waiver list", "nameRu": "Immigration New Zealand — список визовых исключений",
                                     "url": source_url, "checkedAt": checked_at, "official": True}]})
    return sorted(rules, key=lambda rule: rule["citizenship"])


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    source = json.loads((ROOT / "sources/new-zealand-waiver-reviewed.json").read_text())
    rules = new_zealand_rules(source)
    if len(rules) != 60 or len({rule["citizenship"] for rule in rules}) != 60:
        raise ValueError("Reviewed New Zealand source must contain exactly 60 distinct listed origins")
    write_json(ROOT / "official-new-zealand.json", {"version": "1", "updatedAt": source["provenance"]["verifiedOnLocal"],
               "source": source["provenance"]["sourceUrl"], "rules": rules})
    result = {"officialNewZealandRules": len(rules)}
    macao = json.loads((ROOT / "sources/macao-russia-reviewed.json").read_text())
    macao_rule = {key: macao[key] for key in ("citizenship", "destination", "category", "maxStayDays", "notes", "notesRu", "sourceUrl", "checkedAt")}
    macao_rule["citations"] = [{"nameEn": macao["sourceNameEn"], "nameRu": macao["sourceNameRu"],
                                "url": macao["sourceUrl"], "checkedAt": macao["checkedAt"], "official": True}]
    write_json(ROOT / "official-macao.json", {"version": "1", "updatedAt": macao["checkedAt"], "source": macao["sourceUrl"], "rules": [macao_rule]})
    result["officialMacaoRules"] = 1
    hong_kong_file = ROOT / "sources/hong-kong-visa-requirements.csv"
    if hong_kong_file.exists():
        raw_csv = hong_kong_file.read_bytes()
        csv_text = raw_csv.decode("utf-8-sig")
        countries = json.loads((ROOT / "countries.json").read_text())["countries"]
        mapping = hong_kong_mapping(csv_text, countries)
        provenance_file = ROOT / "sources/hong-kong-provenance.json"
        provenance = json.loads(provenance_file.read_text())
        hk_rules = hong_kong_rules(csv_text, mapping, provenance["checkedAt"])
        known = sum(rule["category"] != "UNKNOWN" for rule in hk_rules)
        if len(hk_rules) < 180 or known < 175:
            raise ValueError("Official Hong Kong CSV could not be mapped completely enough")
        write_json(ROOT / "hong-kong-mapping.json", mapping)
        write_json(ROOT / "official-hong-kong.json", {"version": "1", "updatedAt": provenance["checkedAt"], "sourceAsAt": mapping["sourceAsAt"], "source": HK_URL, "rules": hk_rules})
        provenance.update({"rawCsvDownloaded": True, "rawCsvFile": "hong-kong-visa-requirements.csv",
                           "rawCsvBytes": len(raw_csv), "rawCsvSha256": hashlib.sha256(raw_csv).hexdigest(),
                           "sourceAsAt": mapping["sourceAsAt"], "sourceDataRows": len(mapping["sourceLabels"]),
                           "mappedCitizenships": len(hk_rules), "knownRules": known,
                           "sourceRetrieval": "Original official CSV saved separately; source table, terms and special passport conditions reviewed. Date denotes source/extraction review, not an official publication or guarantee."})
        write_json(provenance_file, provenance)
        result["officialHongKongRules"] = len(hk_rules)
        result["hongKongUnmappedLabels"] = mapping["unmappedRows"]
    else:
        result["officialHongKongRules"] = "not generated: original CSV has not been downloaded"
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
