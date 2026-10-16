import unittest
from protect_raha import choose_targets, choose_app_targets, validate_coverage, BRIDGE


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

    def test_navigation_recognizer_is_protected(self):
        mapping = self.mapping + "\ncom.abrar.raha.RahaNavigationRecognizer -> nav:"
        targets, _ = choose_targets(mapping, self.strings, self.inventory | {"Lnav;": []})
        self.assertIn("Lnav;", targets)

    def test_navigation_keep_rule_covers_nested_comparators(self):
        from pathlib import Path
        rules = (Path(__file__).resolve().parents[2] / "app/proguard-rules.pro").read_text()
        self.assertIn("-keep,allowobfuscation class com.abrar.raha.RahaNavigationRecognizer** { *; }", rules)

    def test_rejects_navigation_comparator_merged_into_shared_class(self):
        self.inventory["Lak;"] = []
        mapping = self.mapping + "\nexample.Shared -> ak:\n    30:31:int com.abrar.raha.RahaNavigationRecognizer$recognize$$inlined$sortedByDescending$1.compare(java.lang.Object,java.lang.Object):121:121 -> compare"
        with self.assertRaisesRegex(RuntimeError, "inlined/merged"):
            choose_targets(mapping, self.strings, self.inventory)

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


class ApplicationProtectionTests(unittest.TestCase):
    def setUp(self):
        self.root = "Lcom/abrar/voyahiran/core/bridge/AppInventory;"
        self.mapping = "# pg_map_id: test\n" + "\n".join([
            "com.abrar.voyahiran.core.bridge.BridgeServer -> com.abrar.voyahiran.core.bridge.BridgeServer:",
            "com.abrar.voyahiran.core.bridge.AppInventory -> com.abrar.voyahiran.core.bridge.AppInventory:",
            "com.abrar.voyahiran.ui.MainActivity -> ui:",
            "com.abrar.voyahiran.feature.subscription.SubscriptionRepository -> repo:",
            "com.abrar.raha.speech.SpeechLab -> speech:",
            "com.abrar.voyahiran.core.bridge.Shared -> shared:",
            "third.party.Helper -> lib:",
            "com.abrar.voyahiran.feature.navigation.Data -> data:",
            "com.abrar.raha.Generated$$ExternalSyntheticLambda1 -> lambda:",
        ])
        self.graph = {k:set() for k in [BRIDGE,self.root,"Lui;","Lrepo;","Lspeech;","Lshared;","Llib;","Ldata;","Llambda;"]}
        self.graph[BRIDGE] = {"Lshared;"}
        self.graph["Lshared;"] = {"Llib;"}
        self.graph["Llib;"] = {"Ldata;"}
        self.inventory = dict.fromkeys(self.graph, [])
        self.strings = ['~~R8{"pg-map-id":"test"}']
    def select(self, native=()):
        return choose_app_targets(self.mapping,self.strings,self.inventory,self.graph,native)
    def test_expands_beyond_raha_and_preserves_transitive_shared_code(self):
        targets,_,excluded,_ = self.select()
        self.assertEqual(set(targets), {"Lui;","Lrepo;","Lspeech;","Llambda;"})
        self.assertIn("Ldata;",excluded)
    def test_root_shared_synthetic_is_not_protected(self):
        self.graph['Llib;'].add('Llambda;')
        self.assertNotIn('Llambda;',self.select()[0])
    def test_native_binding_and_dependencies_stay_plain(self):
        self.graph['Lspeech;'] = {'Lrepo;'}
        targets=self.select({'Lspeech;'})[0]
        self.assertNotIn('Lspeech;',targets);self.assertNotIn('Lrepo;',targets)
    def test_root_method_merged_into_app_class_is_preserved(self):
        self.mapping += "\ncom.abrar.raha.Generated$$ExternalSyntheticLambda1 -> lambda:\n    1:2:void com.abrar.voyahiran.core.bridge.Other.run():1 -> a"
        self.assertNotIn('Llambda;',self.select()[0])
    def test_third_party_merge_is_excluded(self):
        self.mapping += "\nthird.party.Merged -> speech:"
        self.assertNotIn('Lspeech;',self.select()[0])
    def test_graph_must_cover_entire_apk(self):
        self.graph.pop('Lui;')
        with self.assertRaisesRegex(RuntimeError,'inventory'):self.select()
    def test_unresolved_edges_fail_closed(self):
        self.graph['Lui;'].add('Lmissing;')
        with self.assertRaisesRegex(RuntimeError,'Unresolved'):self.select()
    def test_missing_app_process_entry_fails(self):
        self.inventory.pop(self.root);self.graph.pop(self.root)
        with self.assertRaisesRegex(RuntimeError,'entry point'):self.select()
    def test_stale_mapping_fails(self):
        self.strings=['~~R8{"pg-map-id":"wrong"}']
        with self.assertRaisesRegex(RuntimeError,'do not match'):self.select()


class ProtectionCoverageTests(unittest.TestCase):
    def test_rejects_observed_19_class_regression(self):
        with self.assertRaisesRegex(RuntimeError, "coverage collapsed"):
            validate_coverage(dict.fromkeys(range(19)), {
                i: {"classes": [f"com.abrar.Root{i}"]} for i in range(1106)
            })

    def test_accepts_bounded_root_exclusions(self):
        validate_coverage(dict.fromkeys(range(800)), {
            i: {"classes": [f"com.abrar.Root{i}"]} for i in range(300)
        })

    def test_rejects_android_entry_even_with_high_coverage(self):
        with self.assertRaisesRegex(RuntimeError, "Android-only"):
            validate_coverage(dict.fromkeys(range(800)), {
                "Lui;": {"classes": ["com.abrar.voyahiran.ui.MainActivity"]}
            })

    def test_empty_selection_fails_closed(self):
        with self.assertRaisesRegex(RuntimeError, "coverage collapsed"):
            validate_coverage({}, {})


if __name__ == "__main__":
    unittest.main()
