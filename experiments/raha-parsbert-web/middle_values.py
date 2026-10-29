"""Owner-approved absolute midpoints; never halves the current state."""
import re
VALUES={
 'SET_AUDIO_VOLUME':('level',15),'SET_AMBIENT_LIGHT_BRIGHTNESS':('percent',50),
 'SET_DISPLAY_BRIGHTNESS':('percent',50),'SET_WINDOW_POSITION':('percent',50),
 'SET_SUNROOF_POSITION':('percent',50),'SET_SUNSHADE_POSITION':('position',50),
 'SET_CABIN_TEMPERATURE':('degreesC',24),'SET_FAN_SPEED':('level',4),
 'SET_SEAT_HEATING':('level',2),'SET_SEAT_VENTILATION':('level',2),
 'SET_SEAT_MASSAGE':('level',2),'SET_WIPER_SENSITIVITY':('level',2),
 'SET_FRAGRANCE_INTENSITY':('intensity','medium'),
 'SET_REGENERATION_LEVEL':('level','medium'),'SET_SUSPENSION_HEIGHT':('level','medium'),
 'SET_BATTERY_RESERVE_TARGET':('percent',50)}

def is_middle(text):
    if not re.search(r'\b(?:نصف|نصفه|وسط|متوسط|میانه)\b',text):return False
    # Fractional units and explicit numbers retain their original meaning.
    if re.search(r'\b(?:نصف|نصفه)\s+(?:درجه|ساعت|دقیقه|درصد|پله)\b',text):return False
    if re.search(r'\d|\b(?:صفر|یک|دو|سه|چهار|پنج|شش|هفت|هشت|نه|ده|بیست|سی|چهل|پنجاه|شصت|هفتاد|هشتاد|نود|صد|فعلی|الان)\b',text):return False
    return True
