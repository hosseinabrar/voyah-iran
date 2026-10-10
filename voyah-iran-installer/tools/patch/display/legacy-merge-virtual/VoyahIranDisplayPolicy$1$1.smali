.class Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;
.super Landroid/database/ContentObserver;
.source "VoyahIranDisplayPolicy.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->run()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/android/server/wm/VoyahIranDisplayPolicy$1;

.field final synthetic val$resolver:Landroid/content/ContentResolver;

.field final synthetic val$service:Lcom/android/server/wm/ActivityTaskManagerService;


# direct methods
.method constructor <init>(Lcom/android/server/wm/VoyahIranDisplayPolicy$1;Landroid/os/Handler;Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V
    .locals 0
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

    .line 75
    iput-object p3, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;->val$service:Lcom/android/server/wm/ActivityTaskManagerService;

    iput-object p4, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;->val$resolver:Landroid/content/ContentResolver;

    iput-object p1, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;->this$0:Lcom/android/server/wm/VoyahIranDisplayPolicy$1;

    invoke-direct {p0, p2}, Landroid/database/ContentObserver;-><init>(Landroid/os/Handler;)V

    return-void
.end method


# virtual methods
.method public onChange(Z)V
    .locals 1

    .line 76
    iget-object p1, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;->val$service:Lcom/android/server/wm/ActivityTaskManagerService;

    iget-object v0, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;->val$resolver:Landroid/content/ContentResolver;

    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$300(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V

    return-void
.end method
