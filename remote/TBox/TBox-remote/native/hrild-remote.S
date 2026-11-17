.text
.global wrapper
wrapper:
sub sp, sp, #48
stp x29,x30,[sp]
mov x29,sp
stp x0,x1,[sp,#16]
str x2,[sp,#32]
bl signal_singleton
ldr x1,[sp,#24]
ldr x2,[sp,#32]
bl signal_init
bl sms_singleton
ldr x1,[sp,#24]
ldr x2,[sp,#32]
bl sms_init
ldp x0,x1,[sp,#16]
ldr x2,[sp,#32]
bl voice_init
ldp x29,x30,[sp]
add sp,sp,#48
ret
