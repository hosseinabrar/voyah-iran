package com.abrar.voyahclustertest;

import android.icu.text.Bidi;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** ASCII-only wire format; the Binder receives the restored Unicode string. */
public final class TextProbe {
 public static String prepare(String raw,int mode)throws Exception {
  String text=raw.trim();
  if(text.isEmpty())throw new IllegalArgumentException("متن را وارد کنید");
  if(mode==1||mode==2)text=ArabicForms.shape(text);
  if(mode==2){Bidi bidi=new Bidi();bidi.setPara(text,Bidi.LEVEL_DEFAULT_RTL,null);text=bidi.writeReordered(Bidi.DO_MIRRORING);}
  validate(text);return text;
 }
 public static void validate(String text){
  if(text.isEmpty()||text.getBytes(StandardCharsets.UTF_8).length>180)throw new IllegalArgumentException("متن خالی یا طولانی است؛ حداکثر ۱۸۰ بایت");
  if(text.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("متن باید یک خط باشد");
 }
 public static String encode(int icon,String text){validate(text);return icon+"\t"+Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));}
 public static String decode(String encoded){String text=new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8);validate(text);return text;}
 public static String codepoints(String text){StringBuilder b=new StringBuilder();text.codePoints().forEach(c->b.append(String.format(java.util.Locale.ROOT,"U+%04X ",c)));return b.toString().trim();}
}
