import unittest
from protect_raha import choose_targets, BRIDGE


class ProtectionSelectionTests(unittest.TestCase):
    def setUp(self):
        self.names = ["RahaCommandRecognizer", "RahaPhaseTwoRecognizer",
                      "RahaPhaseThreeRecognizer", "RahaPhaseFourRecognizer",
                      "RahaPhaseFiveRecognizer", "RahaFormalVocabulary"]
        self.mapping = "# pg_map_id: test\n" + "\n".join(
            f"com.abrar.raha.{name} -> p{i}:" for i, name in enumerate(self.names))
        self.inventory = {f"Lp{i};": [] for i in range(6)} | {BRIDGE: [], "Lshared;": []}
        self.strings = ['~~R8{"pg-map-id":"test"}']

    def test_selects_only_recognizer_classes(self):
        targets, _ = choose_targets(self.mapping, self.strings, self.inventory)
        self.assertEqual(6, len(targets))
        self.assertNotIn(BRIDGE, targets)

    def test_rejects_wrong_mapping(self):
        with self.assertRaisesRegex(RuntimeError, "do not match"):
            choose_targets(self.mapping, ['~~R8{"pg-map-id":"wrong"}'], self.inventory)

    def test_rejects_inline_leak(self):
        mapping = self.mapping + "\nexample.Shared -> shared:\n    1:3:void com.abrar.raha.RahaCommandRecognizer.normalize():1 -> a"
        with self.assertRaisesRegex(RuntimeError, "inlined/merged"):
            choose_targets(mapping, self.strings, self.inventory)

    def test_field_type_reference_is_not_inline_leak(self):
        mapping = self.mapping + "\nexample.Shared -> shared:\n    com.abrar.raha.RahaCommandRecognizer recognizer -> a"
        self.assertEqual(6, len(choose_targets(mapping, self.strings, self.inventory)[0]))

    def test_rejects_shared_class_merge(self):
        with self.assertRaisesRegex(RuntimeError, "merged protected and shared"):
            choose_targets(self.mapping + "\nexample.Shared -> p0:", self.strings, self.inventory)

    def test_preserves_shared_lambda_named_after_recognizer(self):
        # Regression: vi held both a recognizer lambda and OemSession.write$lambda$2.
        mapping = self.mapping + """
com.abrar.raha.RahaPhaseFourRecognizer$$ExternalSyntheticLambda1 -> vi:
# {"id":"com.android.tools.r8.synthesized"}
    1:3:kotlin.Unit com.abrar.voyahiran.core.bridge.RahaVehicleBridge$OemSession.write$lambda$2(java.lang.String):131 -> invoke
    1:3:java.lang.Object RahaPhaseFourRecognizer$$ExternalSyntheticLambda1.invoke(java.lang.Object):0 -> invoke
"""
        self.inventory["Lvi;"] = []
        targets, _ = choose_targets(mapping, self.strings, self.inventory)
        self.assertEqual(6, len(targets))
        self.assertNotIn("Lvi;", targets)

    def test_preserves_synthetic_identified_by_class_metadata(self):
        mapping = self.mapping + """
com.abrar.raha.RahaPhaseFourRecognizer$Generated -> generated:
# {"id":"com.android.tools.r8.synthesized"}
"""
        self.inventory["Lgenerated;"] = []
        self.assertNotIn("Lgenerated;", choose_targets(mapping, self.strings, self.inventory)[0])

    def test_rejects_foreign_method_inside_protected_class(self):
        mapping = self.mapping + """
com.abrar.raha.RahaPhaseFourRecognizer$Nested -> nested:
    1:3:void com.abrar.voyahiran.core.bridge.Other.run():1 -> a
"""
        self.inventory["Lnested;"] = []
        with self.assertRaisesRegex(RuntimeError, "shared app method"):
            choose_targets(mapping, self.strings, self.inventory)

    def test_rejects_recognizer_logic_in_unprotected_synthetic(self):
        mapping = self.mapping + """
com.abrar.raha.RahaPhaseFourRecognizer$$ExternalSyntheticLambda1 -> vi:
    1:3:void com.abrar.raha.RahaCommandRecognizer.normalize():1 -> a
"""
        self.inventory["Lvi;"] = []
        with self.assertRaisesRegex(RuntimeError, "inlined/merged"):
            choose_targets(mapping, self.strings, self.inventory)

    def test_rejects_missing_bridge(self):
        self.inventory.pop(BRIDGE)
        with self.assertRaisesRegex(RuntimeError, "Root bridge"):
            choose_targets(self.mapping, self.strings, self.inventory)


if __name__ == "__main__":
    unittest.main()
