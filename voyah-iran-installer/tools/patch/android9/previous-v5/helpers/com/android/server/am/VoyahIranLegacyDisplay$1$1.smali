.class Lcom/android/server/am/VoyahIranLegacyDisplay$1$1;
.super Landroid/database/ContentObserver;
.source "VoyahIranLegacyDisplay.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/android/server/am/VoyahIranLegacyDisplay$1;->run()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/android/server/am/VoyahIranLegacyDisplay$1;

.field final synthetic val$resolver:Landroid/content/ContentResolver;

.field final synthetic val$service:Lcom/android/server/am/ActivityManagerService;


# direct methods
.method constructor <init>(Lcom/android/server/am/VoyahIranLegacyDisplay$1;Landroid/os/Handler;Lcom/android/server/am/ActivityManagerService;Landroid/content/ContentResolver;)V
    .registers 5
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010,
            0x0,
            0x1010,
            0x1010
        }
        names = {
            null,
            null,
            null,
            null
        }
    .end annotation

    .line 50
    iput-object p3, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1$1;->val$service:Lcom/android/server/am/ActivityManagerService;

    iput-object p4, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1$1;->val$resolver:Landroid/content/ContentResolver;

    iput-object p1, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1$1;->this$0:Lcom/android/server/am/VoyahIranLegacyDisplay$1;

    invoke-direct {p0, p2}, Landroid/database/ContentObserver;-><init>(Landroid/os/Handler;)V

    return-void
.end method


# virtual methods
.method public onChange(Z)V
    .registers 3

    .line 51
    iget-object p1, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1$1;->val$service:Lcom/android/server/am/ActivityManagerService;

    iget-object v0, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1$1;->val$resolver:Landroid/content/ContentResolver;

    invoke-static {p1, v0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$300(Lcom/android/server/am/ActivityManagerService;Landroid/content/ContentResolver;)V

    return-void
.end method
