package com.abrar.voyahiran.feature.voice

import org.junit.Assert.*
import org.junit.Test

class WindowPositionControllerTest {
    private val fields = WindowPositionController.feedback.keys.toList()
    private var time = 0L
    private val positions = WindowPositionController.feedback.values.associateWith { 0 }.toMutableMap()
    private val moving = mutableMapOf<String, Int>()
    private val writes = mutableListOf<Pair<String, Int>>()
    private var frozen = false
    private var invalidAfterStart = false
    private fun controller() = WindowPositionController(
        read = { if (invalidAfterStart && time > 0) 127 else positions.getValue(it) },
        write = { field, value ->
            writes += field to value
            if (value == 5) moving.remove(field) else moving[field] = if (value == 3 || value == 4) 1 else -1
        }, log = {}, now = { time }, sleep = { duration ->
            time += duration
            // Reproduce the recorded car: a lone manual-open command stops at 10%.
            moving.keys.toList().forEach { field ->
                if (writes.lastOrNull { it.first == field }?.second == 4 &&
                    positions.getValue(WindowPositionController.feedback.getValue(field)) >= 10) moving.remove(field)
            }
            if (!frozen) moving.forEach { (field, direction) ->
                val key = WindowPositionController.feedback.getValue(field)
                positions[key] = (positions.getValue(key) + direction * 2).coerceIn(0, 100)
            }
        })
    private fun goal(index: Int, target: Int) = RahaWrite("window-position", fields[index], target)
    @Test fun repeatedRelativeThenHalfStartsFromActualPosition() {
        val control = controller()
        repeat(2) { control.move(listOf(goal(0, positions.values.first() + 10))) }
        control.move(listOf(goal(0, 50)))
        assertTrue(positions.values.first() in 47..53)
        assertEquals(3, writes.count { it.second == 5 })
        assertTrue(moving.isEmpty())
    }
    @Test fun windowsMoveInOppositeDirectionsAndStopIndependently() {
        positions[WindowPositionController.feedback.getValue(fields[1])] = 80
        controller().move(listOf(goal(0, 50), goal(1, 50)))
        assertTrue(writes.contains(fields[0] to 3))
        assertTrue(writes.contains(fields[1] to 1))
        assertTrue(positions.values.take(2).all { it in 47..53 })
        assertTrue(moving.isEmpty())
    }
    @Test fun alreadyNearTargetDoesNotMove() {
        positions[WindowPositionController.feedback.getValue(fields[0])] = 49
        controller().move(listOf(goal(0, 50)))
        assertTrue(writes.isEmpty())
    }
    @Test fun stalledFeedbackStopsEveryStartedWindow() {
        frozen = true
        assertThrows(IllegalStateException::class.java) { controller().move(listOf(goal(0, 50), goal(1, 50))) }
        assertTrue(moving.isEmpty())
        assertTrue(writes.contains(fields[0] to 5))
        assertTrue(writes.contains(fields[1] to 5))
    }
    @Test fun invalidFeedbackDuringMovementStopsWindow() {
        invalidAfterStart = true
        assertThrows(IllegalStateException::class.java) { controller().move(listOf(goal(0, 50))) }
        assertEquals(fields[0] to 5, writes.last())
    }
    @Test fun invalidInitialFeedbackMovesNothing() {
        positions[WindowPositionController.feedback.getValue(fields[1])] = 127
        assertThrows(IllegalStateException::class.java) { controller().move(listOf(goal(0, 50), goal(1, 50))) }
        assertTrue(writes.isEmpty())
    }
    @Test fun slowButChangingFeedbackStillHasAnOverallDeadline() {
        val stops = mutableListOf<Int>()
        val control = WindowPositionController(read = { (time / 600).toInt() },
            write = { _, value -> stops += value }, log = {}, now = { time }, sleep = { time += it })
        assertThrows(IllegalStateException::class.java) { control.move(listOf(goal(0, 50))) }
        assertEquals(5, stops.last())
        assertTrue(time in 8000..8100)
    }
    @Test fun failedStartStillStopsBothPossiblyStartedWindows() {
        val sent = mutableListOf<Pair<String, Int>>()
        val control = WindowPositionController(read = { 0 }, write = { field, value ->
            sent += field to value
            if (field == fields[1] && value == 3) error("Ambiguous Binder reply")
        }, log = {}, now = { time }, sleep = { time += it })
        assertThrows(IllegalStateException::class.java) { control.move(listOf(goal(0, 50), goal(1, 50))) }
        assertTrue(sent.contains(fields[0] to 5))
        assertTrue(sent.contains(fields[1] to 5))
    }
}
