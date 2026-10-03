package com.abrar.voyahclustertest;
public final class NeshanEnums {
 public enum Direction { U_TURN(0),SHARP_RIGHT(1),RIGHT(2),SLIGHT_RIGHT(3),STRAIGHT(4),SLIGHT_LEFT(5),LEFT(6),SHARP_LEFT(7),UNKNOWN(-1); public final int raw; Direction(int raw){this.raw=raw;} public String toString(){return name()+" ("+raw+")";} }
 public enum Maneuver { TURN(0),NEW_NAME(1),DEPART(2),ARRIVE(3),MERGE(4),ON_RAMP(5),OFF_RAMP(6),FORK(7),END_OF_ROAD(8),CONTINUE(9),ROUNDABOUT(10),ROTARY(11),ROUNDABOUT_TURN(12),BIKE_STATION(13),WALK(14),USE_LANE(15),EXIT_ROUNDABOUT(16),NEW_EXIT_ROUNDABOUT(17),UNKNOWN(-1); public final int raw; Maneuver(int raw){this.raw=raw;} public String toString(){return name()+" ("+raw+")";} }
 public static int guess(Direction d) { return switch(d) {case STRAIGHT -> 1; case SLIGHT_RIGHT -> 2; case RIGHT -> 3; case SHARP_RIGHT -> 4; case U_TURN -> 5; case SHARP_LEFT -> 6; case LEFT -> 7; case SLIGHT_LEFT -> 8; case UNKNOWN -> 0;};}
}
