package com.abrar.voicecommands

data class VehicleCommand(val catalogId: String, val action: String, val parameters: Map<String, Any>, val description: String)
sealed interface Recognition {
    data class Matched(val command: VehicleCommand) : Recognition
    data class Unknown(val reason: String) : Recognition
}
