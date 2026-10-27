import unittest

from data.normalize import normalize_requirement, normalize_matrix, normalize_geometry
from data import normalize


class NormalizeTests(unittest.TestCase):
    def test_community_schema_keeps_primary_reference_and_real_verification(self):
        result = normalize.normalize_community({
            "name": "visa-requirements", "license": "CC-BY-4.0", "version": "2026-08-11", "updated": "2026-08-10",
            "rules": {"RU": {"TH": {"requirement": "visa_on_arrival", "max_stay_days": 15,
                "source": "https://example.gov/rule", "verified": "2026-07-01"}}},
        }, {"RU", "TH"})
        self.assertEqual(result["updatedAt"], "2026-08-10")
        self.assertEqual(result["rules"][0]["category"], "VISA_ON_ARRIVAL")
        self.assertEqual(result["rules"][0]["maxStayDays"], 15)
        self.assertEqual(result["rules"][0]["checkedAt"], "2026-07-01")
        self.assertEqual(result["rules"][0]["sourceUrl"], "https://example.gov/rule")

    def test_community_unknown_requirement_is_not_visa_free(self):
        data = {"name": "visa-requirements", "license": "CC-BY-4.0", "updated": "2026-08-10",
                "rules": {"RU": {"TH": {"requirement": "new-requirement", "max_stay_days": None}}}}
        rule = normalize.normalize_community(data, {"RU", "TH"})["rules"][0]
        self.assertEqual(rule["category"], "UNKNOWN")
        self.assertIsNone(rule["checkedAt"])

    def test_community_rejects_invalid_date_code_and_duration(self):
        import copy
        original = {"name": "visa-requirements", "license": "CC-BY-4.0", "updated": "2026-08-10",
                    "rules": {"RU": {"TH": {"requirement": "visa_free", "max_stay_days": 30}}}}
        for key, value in (("verified", "2026-02-31"), ("max_stay_days", 1.5), ("max_stay_days", True)):
            data = copy.deepcopy(original)
            data["rules"]["RU"]["TH"][key] = value
            with self.assertRaises(ValueError):
                normalize.normalize_community(data, {"RU", "TH"})
        with self.assertRaises(ValueError):
            normalize.normalize_community(original, {"RU"})

    def test_community_http_reference_stays_text_without_protocol_invention(self):
        data = {"name": "visa-requirements", "license": "CC-BY-4.0", "updated": "2026-08-10",
                "rules": {"RU": {"TH": {"requirement": "visa_free", "max_stay_days": 730,
                    "source": "http://example.gov/rule"}}}}
        rule = normalize.normalize_community(data, {"RU", "TH"})["rules"][0]
        self.assertEqual(rule["sourceUrl"], normalize.COMMUNITY_SOURCE)
        self.assertIn("http://example.gov/rule", rule["notes"])
        self.assertEqual(rule["maxStayDays"], 730)

    def test_duration_means_visa_free_only_for_positive_integer(self):
        self.assertEqual(normalize_requirement("90"), ("VISA_FREE", 90))
        self.assertEqual(normalize_requirement("0"), ("UNKNOWN", None))
        self.assertEqual(normalize_requirement("-2"), ("UNKNOWN", None))
        self.assertEqual(normalize_requirement("90/180"), ("UNKNOWN", None))

    def test_categories_remain_distinct(self):
        for raw, expected in {
            "visa free": "VISA_FREE", "visa on arrival": "VISA_ON_ARRIVAL",
            "eta": "ETA", "e-visa": "E_VISA", "visa required": "VISA_REQUIRED",
            "no admission": "RESTRICTED", "changed upstream": "UNKNOWN",
        }.items():
            self.assertEqual(normalize_requirement(raw), (expected, None))

    def test_matrix_does_not_turn_fetch_date_into_verification_date(self):
        rules = normalize_matrix(
            {"ru": {"ru": "-1", "tr": {"status": "visa free", "days": 60}, "jp": "new value"}},
            country_codes={"RU", "TR", "JP"}, source_url="https://example.org/data",
        )
        self.assertEqual(len(rules), 2)
        turkey = next(rule for rule in rules if rule["destination"] == "TR")
        self.assertEqual(turkey["maxStayDays"], 60)
        self.assertIsNone(turkey["checkedAt"])
        self.assertEqual(turkey["citizenship"], "RU")
        self.assertEqual(next(rule for rule in rules if rule["destination"] == "JP")["category"], "UNKNOWN")

    def test_unknown_codes_are_rejected_instead_of_creating_phantom_countries(self):
        with self.assertRaises(ValueError):
            normalize_matrix({"ru": {"zz": 90}}, {"RU"}, "https://example.org/data")

    def test_geometry_keeps_multipolygons_and_filters_invalid_codes(self):
        polygon = [[[0, 0], [1, 0], [1, 1], [0, 0]]]
        result = normalize_geometry({"type": "FeatureCollection", "features": [
            {"type": "Feature", "properties": {"ISO_A2": "-99", "ISO_A2_EH": "FR"}, "geometry": {"type": "MultiPolygon", "coordinates": [polygon]}},
            {"type": "Feature", "properties": {"ISO_A2": "-99"}, "geometry": {"type": "Polygon", "coordinates": polygon}},
        ]}, {"FR"})
        self.assertEqual(len(result["features"]), 1)
        self.assertEqual(result["features"][0]["properties"]["code"], "FR")
        self.assertEqual(result["features"][0]["geometry"]["type"], "MultiPolygon")


if __name__ == "__main__":
    unittest.main()
