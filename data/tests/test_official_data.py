import unittest
import csv
import io
from pathlib import Path

from data.prepare_official_data import new_zealand_rules, hong_kong_rules, hong_kong_mapping


class OfficialDataTests(unittest.TestCase):
    def test_new_indian_prior_visa_requirement_is_not_overridden_by_par(self):
        text = 'Nationality,Days,Visa\nIndia,Not applicable,All nationals are required to hold a valid visa for the HKSAR for whatever purpose (except those who are in transit and remain on the airside)\n'
        mapping = {"entries": [{"code": "IN", "rows": ["India"], "categoryOverride": "ETA",
                                "notes": "Old PAR duration should not survive", "notesRuntime": "If ETA is shown, this refers to PAR."}]}
        rule = hong_kong_rules(text, mapping, "2026-10-26")[0]
        self.assertEqual(rule["category"], "VISA_REQUIRED")
        self.assertNotIn("Old PAR", rule["notes"])

    def test_mapping_preserves_all_source_labels_and_stable_runtime_notes(self):
        text = (Path(__file__).parent / "fixtures/hong-kong-contract.csv").read_text(encoding="utf-8-sig")
        rows = list(csv.reader(io.StringIO(text)))
        mapping = hong_kong_mapping(text, [{"code": "IN", "nameEn": "India"}, {"code": "AL", "nameEn": "Albania"}, {"code": "GB", "nameEn": "United Kingdom"}])
        self.assertEqual(mapping["sourceLabels"], [row[0] for row in rows[1:-1]])
        self.assertEqual(mapping["sourceAsAt"], "2025-12-31")
        for item in mapping["entries"]:
            self.assertNotRegex(item["notesRuntime"] + item["notesRuntimeRu"], r"[0-9]")

    def test_real_hong_kong_csv_contract(self):
        text = (Path(__file__).parent / "fixtures/hong-kong-contract.csv").read_text(encoding="utf-8-sig")
        countries = [{"code": code, "nameEn": label} for code, label in {"AF": "Afghanistan", "AO": "Angola", "PK": "Pakistan", "KH": "Cambodia", "IN": "India", "RU": "Russia", "GB": "United Kingdom", "US": "United States", "AL": "Albania", "RS": "Serbia", "TO": "Tonga", "TV": "Tuvalu"}.items()]
        mapping = hong_kong_mapping(text, countries)
        rules = {rule["citizenship"]: rule for rule in hong_kong_rules(text, mapping, "2026-10-26")}
        for code, days in {"RU": 14, "GB": 180, "US": 90}.items():
            self.assertEqual((rules[code]["category"], rules[code]["maxStayDays"]), ("VISA_FREE", days))
        self.assertEqual((rules["IN"]["category"], rules["IN"]["maxStayDays"]), ("ETA", 14))
        for code in ("AF", "AO", "PK", "KH"):
            self.assertEqual(rules[code]["category"], "VISA_REQUIRED")
            self.assertIsNone(rules[code]["maxStayDays"])
        for code in ("AL", "RS", "TO", "TV"):
            self.assertEqual(rules[code]["category"], "UNKNOWN")

    def test_hong_kong_changed_requirement_keeps_country_as_unknown(self):
        text = 'National of foreign country (territory)/Type of travel document,Visa free period for visiting the Hong Kong Special Administrative Region,Visa requirements\nRUSSIAN FEDERATION,new upstream arrangement,Not applicable\n'
        mapping = hong_kong_mapping(text, [{"code": "RU", "nameEn": "Russia"}])
        self.assertEqual([entry["code"] for entry in mapping["entries"]], ["RU"])
        self.assertEqual(hong_kong_rules(text, mapping, "2026-10-26")[0]["category"], "UNKNOWN")

    def test_hong_kong_keeps_registration_and_ambiguous_passports(self):
        csv_text = 'Nationality,Visa free period,Always require VISA\nRUSSIAN FEDERATION,14,Not applicable\nINDIA,14,Indian nationals are required to apply for and successfully complete pre-arrival registration online before they can visit the HKSAR visa free.\nALBANIA biometric,14,Not applicable\nALBANIA non-biometric,Not applicable,All nationals are required to hold a valid visa for the HKSAR for whatever purpose (except those who are in transit and remain on the airside)\n'
        mapping = {"entries": [
            {"code": "RU", "rows": ["RUSSIAN FEDERATION"]},
            {"code": "IN", "rows": ["INDIA"], "categoryOverride": "ETA", "notes": "PAR required", "notesRu": "Требуется PAR"},
            {"code": "AL", "rows": ["ALBANIA biometric", "ALBANIA non-biometric"], "categoryOverride": "UNKNOWN", "notes": "Passport subtype matters"},
        ]}
        rules = {rule["citizenship"]: rule for rule in hong_kong_rules(csv_text, mapping, "2026-10-26")}
        self.assertEqual((rules["RU"]["category"], rules["RU"]["maxStayDays"]), ("VISA_FREE", 14))
        self.assertEqual((rules["IN"]["category"], rules["IN"]["maxStayDays"]), ("ETA", 14))
        self.assertEqual(rules["AL"]["category"], "UNKNOWN")
        self.assertIsNone(rules["AL"]["maxStayDays"])

    def test_hong_kong_does_not_apply_fallback_to_unlisted_codes(self):
        mapping = {"entries": [{"code": "RU", "rows": ["RUSSIAN FEDERATION"]}]}
        rules = hong_kong_rules('Nationality,Days,Visa\nRUSSIAN FEDERATION,14,Not applicable\nNationals of other foreign countries,7,Not applicable\n', mapping, "2026-10-26")
        self.assertEqual([rule["citizenship"] for rule in rules], ["RU"])
        with self.assertRaises(ValueError):
            hong_kong_rules('Nationality,Days,Visa\nRUSSIAN FEDERATION,14,Not applicable\nRUSSIAN FEDERATION,90,Not applicable\n', mapping, "2026-10-26")

    def test_new_zealand_months_are_not_converted_to_days(self):
        source = {"list": [{"code": "GB", "notes": "British citizen or qualifying British passport.", "notesRu": "Условие британского паспорта."}],
                  "defaults": {"maxStayMonths": 3, "maxStayMonthsByPassport": {"GB": 6}},
                  "provenance": {"sourceUrl": "https://www.immigration.govt.nz/waiver", "verifiedOnLocal": "2026-10-26"}}
        rules = new_zealand_rules(source)
        self.assertEqual(len(rules), 1)
        self.assertEqual(rules[0]["category"], "ETA")
        self.assertIsNone(rules[0]["maxStayDays"])
        self.assertIn("6 calendar months", rules[0]["notes"])
        self.assertIn("6 календарных месяцев", rules[0]["notesRu"])
        self.assertEqual(rules[0]["checkedAt"], "2026-10-26")

    def test_new_zealand_absence_is_not_inferred_as_visa_required(self):
        source = {"list": [{"code": "DE", "notes": "", "notesRu": ""}],
                  "defaults": {"maxStayMonths": 3, "maxStayMonthsByPassport": {}},
                  "provenance": {"sourceUrl": "https://www.immigration.govt.nz/waiver", "verifiedOnLocal": "2026-10-26"}}
        rules = new_zealand_rules(source)
        self.assertEqual([rule["citizenship"] for rule in rules], ["DE"])
        self.assertNotIn("RU", {rule["citizenship"] for rule in rules})


if __name__ == "__main__":
    unittest.main()
