"""Display labels must never bake a sample's seat, enabled state or value in."""
import re

OVERRIDES={
    'CONFIGURE_FRAGRANCE':'تنظیم ترکیبی رایحه',
    'NAVIGATE_TO_SAVED_PLACE':'مسیریابی به مکان ذخیره‌شده',
    'SET_FRAGRANCE_CHANNEL':'انتخاب رایحه',
    'SET_FRAGRANCE_DURATION':'مدت رایحه',
    'SET_FRAGRANCE_INTENSITY':'شدت رایحه',
    'SET_FRAGRANCE_POWER':'روشن یا خاموش کردن رایحه',
    'SET_LANE_SUPPORT_MODE':'کمک بین خطوط',
    'SET_AUDIO_VOLUME':'تنظیم بلندی صدا',
    'ADJUST_AUDIO_VOLUME':'کم یا زیاد کردن بلندی صدا',
    'SET_AUDIO_MUTE':'بی‌صدا کردن یا بازگرداندن صدا',
}

def titles(values):
    return {k:OVERRIDES.get(k,re.sub(r' (?:راننده \(چپ\)|شاگرد \(راست\)|دو سمت جلو|راننده|شاگرد|مرکزی)$','',v.strip())) for k,v in values.items()}
