# Raha execution/UI audit — 2026-09-22

All 101 enum actions reviewed for dispatch/UI routing. This is a source audit and host test result, not a vehicle execution certification.

| Action | Route | Visible UI |
|---|---|---|
| SET_CLIMATE_POWER | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_AC_POWER | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_MAX_COOLING | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_CLIMATE_AUTO | RahaVehiclePlan / direct OEM write | No intermediary menu |
| ADJUST_CABIN_TEMPERATURE | RahaVehiclePlan / direct OEM write | No intermediary menu |
| ADJUST_FAN_SPEED | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_AIRFLOW_DIRECTION | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_AIR_RECIRCULATION | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_FRONT_DEFROSTER | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_REAR_DEFROSTER | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_FRONT_CLIMATE_SYNC | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_CABIN_TEMPERATURE | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_FAN_SPEED | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_SEAT_HEATING | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_SEAT_VENTILATION | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_SEAT_MASSAGE | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_SEAT_MASSAGE_PATTERN | RahaVehiclePlan / direct OEM write | No intermediary menu |
| ADJUST_SEAT_HEATING | RahaVehiclePlan / direct OEM write | No intermediary menu |
| ADJUST_SEAT_VENTILATION | RahaVehiclePlan / direct OEM write | No intermediary menu |
| ADJUST_SEAT_MASSAGE | RahaVehiclePlan / direct OEM write | No intermediary menu |
| CONFIGURE_FRAGRANCE | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_FRAGRANCE_DURATION | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_FRAGRANCE_POWER | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_FRAGRANCE_CHANNEL | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_FRAGRANCE_INTENSITY | RahaVehiclePlan / direct OEM write | No intermediary menu |
| ADJUST_FRAGRANCE_INTENSITY | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_WINDOW_POSITION | RahaVehiclePlan / direct OEM write | No intermediary menu |
| ADJUST_WINDOW_POSITION | RahaVehiclePlan / direct OEM write | No intermediary menu |
| ADJUST_SUNROOF_POSITION | RahaVehiclePlan / direct OEM write | No intermediary menu |
| ADJUST_SUNSHADE_POSITION | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_DOOR_LOCK | RahaVehiclePlan / direct OEM write | No intermediary menu |
| OPEN_WINDOW | RahaVehiclePlan / direct OEM write | No intermediary menu |
| CLOSE_WINDOW | RahaVehiclePlan / direct OEM write | No intermediary menu |
| VENT_WINDOW | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_CHILD_LOCK | RahaVehiclePlan / direct OEM write | No intermediary menu |
| OPEN_TAILGATE | RahaVehiclePlan / direct OEM write | No intermediary menu |
| CLOSE_TAILGATE | RahaVehiclePlan / direct OEM write | No intermediary menu |
| OPEN_CHARGE_PORT | RahaVehiclePlan / direct OEM write | No intermediary menu |
| OPEN_FUEL_FLAP | RahaVehiclePlan / direct OEM write | No intermediary menu |
| OPEN_SUNROOF | RahaVehiclePlan / direct OEM write | No intermediary menu |
| CLOSE_SUNROOF | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_SUNROOF_POSITION | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_SUNSHADE_POSITION | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_HEADLIGHT_MODE | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_AUTO_HIGH_BEAM | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_DAYTIME_RUNNING_LIGHTS | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_AMBIENT_LIGHT_MODE | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_AMBIENT_LIGHT_POWER | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_AMBIENT_LIGHT_BRIGHTNESS | RahaVehiclePlan / direct OEM write | No intermediary menu |
| ADJUST_AMBIENT_LIGHT_BRIGHTNESS | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_REAR_FOG_LIGHT | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_RAIN_SENSING_WIPERS | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_SUSPENSION_HEIGHT | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_DRIVE_MODE | RahaVehiclePlan + OemDriveModePopup | Drive mode popup (requested) |
| SET_REGENERATION_LEVEL | RahaVehiclePlan / direct OEM write | No intermediary menu |
| ADJUST_REGENERATION_LEVEL | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_HIGHWAY_SUSPENSION_ADAPTATION | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_AUTO_HOLD | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_HILL_DESCENT_CONTROL | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_STABILITY_CONTROL | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_PARKING_BRAKE | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_WIPER_SENSITIVITY | RahaVehiclePlan / direct OEM write | No intermediary menu |
| ADJUST_WIPER_SENSITIVITY | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SAVE_SEAT_POSITION | OemSeatMemory / event bus | Seat memory feedback (requested) |
| RECALL_SEAT_POSITION | OemSeatMemory / event bus | Seat memory feedback (requested) |
| SET_ENERGY_STRATEGY | OemEnergyBinder | No intermediary menu |
| SET_BATTERY_RESERVE_TARGET | OemEnergyBinder | No intermediary menu |
| STOP_CHARGING | OemEnergySceneActions / existing OEM process | No intermediary menu |
| SET_BATTERY_PRECONDITIONING | OemEnergySceneActions / existing OEM process | No intermediary menu |
| ACTIVATE_SCENE | OemSceneActions | Camp/rest own feature screen may open; no Settings menu |
| DEACTIVATE_SCENE | OemSceneActions | Camp/rest own feature screen may open; no Settings menu |
| SET_POWER_KEEP_DURATION | OemEnergySceneActions / existing OEM process | No intermediary menu |
| SET_POWER_KEEP_ENGINE_CHARGING | OemEnergySceneActions / existing OEM process | No intermediary menu |
| SET_MIRROR_FOLD | RahaVehiclePlan / direct OEM write | No intermediary menu |
| SET_DOOR_OPEN_WARNING | OemDriverAssistanceBinder | No intermediary menu |
| SET_FORWARD_COLLISION_WARNING | OemDriverAssistanceBinder | No intermediary menu |
| SET_REAR_CROSS_TRAFFIC_WARNING | OemDriverAssistanceBinder | No intermediary menu |
| SET_REAR_COLLISION_WARNING | OemDriverAssistanceBinder | No intermediary menu |
| SET_AUTOMATIC_EMERGENCY_BRAKING | OemDriverAssistanceBinder | No intermediary menu |
| SET_BLIND_SPOT_MONITORING | OemDriverAssistanceBinder | No intermediary menu |
| SET_LANE_SUPPORT_MODE | OemDriverAssistanceBinder | No intermediary menu |
| SET_DRIVER_MONITORING | OemDriverAssistanceBinder | No intermediary menu |
| SET_DRIVER_GESTURES | OemDriverAssistanceBinder | No intermediary menu |
| SET_LEAD_VEHICLE_DEPARTURE_ALERT | OemDriverAssistanceBinder | No intermediary menu |
| SET_FRONT_CROSS_TRAFFIC_WARNING | OemDriverAssistanceBinder | No intermediary menu |
| SET_TRAFFIC_SIGN_RECOGNITION | OemDriverAssistanceBinder | No intermediary menu |
| SET_LANE_DEPARTURE_ALERT_TYPE | OemDriverAssistanceBinder | No intermediary menu |
| SET_DISPLAY_BRIGHTNESS | OemPhaseSevenActions | No intermediary menu |
| SET_DISPLAY_THEME_MODE | OemPhaseSevenActions | No intermediary menu |
| SET_REAR_DISPLAY_LOCK | OemPhaseSevenActions | No intermediary menu |
| SET_AUDIO_VOLUME | OemAudioBinder / qg.audio_policy, feedback checked | No intermediary menu |
| ADJUST_AUDIO_VOLUME | OemAudioBinder / qg.audio_policy, feedback checked | No intermediary menu |
| SET_AUDIO_MUTE | OemAudioBinder / qg.audio_policy, feedback checked | No intermediary menu |
| PLAY_MEDIA | OemPhaseSevenActions | No intermediary menu |
| PAUSE_MEDIA | OemPhaseSevenActions | No intermediary menu |
| NEXT_MEDIA | OemPhaseSevenActions | No intermediary menu |
| PREVIOUS_MEDIA | OemPhaseSevenActions | No intermediary menu |
| OPEN_CAMERA | OemPhaseSevenActions | Camera own feature UI; stop does not relaunch |
| TAKE_PHOTO | OemPhaseSevenActions | Camera own feature UI; stop does not relaunch |
| START_VIDEO_RECORDING | OemPhaseSevenActions | Camera own feature UI; stop does not relaunch |
| STOP_VIDEO_RECORDING | OemPhaseSevenActions | Camera own feature UI; stop does not relaunch |

## Changes

Removed Settings activity launch for audio, brightness, day/night/auto and rear-display lock. Audio uses the installed IAudioPolicyService Binder and reads back the requested state, with flags=0 (no volume UI or extra tone). Display calls the existing BackLightManager directly, retaining high-temperature refusal. Rear-screen lock follows the OEM QGSettings Global callback and platform guard. Removed unreachable legacy scene launch code from OemEnergySceneActions; active scene routing is unchanged.

Display/rear-lock still require their OEM process to be running; failure returns an error without opening a menu. Display service acceptance is not proof of physical screen brightness. Audio needs hardware validation for OEM caller/permission behavior. No new permission or installer UI change. Re-publish/install to update bridge.apk.

Evidence: decompiled SoundFragment/SoundPresenter + IAudioPolicyService (channels 3/6/1/22/5); DisplayFragment/DisplayPresenter (brightness screens 1/2/16, modes day=1/night=0/auto=2, setDimmer scope19); DrivePreferenceFragment callback MainScreenVehicleRearSCRLock=2/0.