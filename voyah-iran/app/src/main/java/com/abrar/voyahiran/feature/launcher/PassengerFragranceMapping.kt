package com.abrar.voyahiran.feature.launcher

/** OEM FragranceFragmentUIRevision.onClick/updateCanStatus and fragment_fragrance_uirevision:
 * typebg_2 (Fresh bamboo) = CAN 1; typebg_3 (Peony scarlet) = CAN 2;
 * typebg_1 (Watery mist) = CAN 3. Artwork suffixes are NOT CAN channel numbers.
 */
internal object PassengerFragranceMapping {
    fun artwork(channel:Int):Int?=when(channel){1->2;2->3;3->1;else->null}
    fun channel(artwork:Int):Int=when(artwork){2->1;3->2;1->3;else->error("Unknown fragrance artwork: $artwork")}
}
