.class Lcom/android/server/wm/VoyahIranDisplayPolicy$1;
.super Ljava/lang/Object;
.source "VoyahIranDisplayPolicy.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/android/server/wm/VoyahIranDisplayPolicy;->initialize(Lcom/android/server/wm/ActivityRecord;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic val$handler:Landroid/os/Handler;

.field final synthetic val$service:Lcom/android/server/wm/ActivityTaskManagerService;


# direct methods
.method constructor <init>(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/os/Handler;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .line 28
    iput-object p1, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$service:Lcom/android/server/wm/ActivityTaskManagerService;

    iput-object p2, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$handler:Landroid/os/Handler;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .locals 5

    .line 31
    const/4 v0, 0x0

    :try_start_0
    iget-object v1, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$service:Lcom/android/server/wm/ActivityTaskManagerService;

    iget-object v1, v1, Lcom/android/server/wm/ActivityTaskManagerService;->mContext:Landroid/content/Context;

    invoke-virtual {v1}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v1

    .line 32
    new-instance v2, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;

    iget-object v3, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$handler:Landroid/os/Handler;

    iget-object v4, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$service:Lcom/android/server/wm/ActivityTaskManagerService;

    invoke-direct {v2, p0, v3, v4, v1}, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;-><init>(Lcom/android/server/wm/VoyahIranDisplayPolicy$1;Landroid/os/Handler;Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V

    invoke-static {v2}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$002(Landroid/database/ContentObserver;)Landroid/database/ContentObserver;

    .line 35
    const-string v2, "voyah_iran_display_policy"

    invoke-static {v2}, Landroid/provider/Settings$Global;->getUriFor(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v2

    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$000()Landroid/database/ContentObserver;

    move-result-object v3

    invoke-virtual {v1, v2, v0, v3}, Landroid/content/ContentResolver;->registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V

    .line 36
    iget-object v2, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$service:Lcom/android/server/wm/ActivityTaskManagerService;

    invoke-static {v2, v1}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$100(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 41
    goto :goto_0

    .line 37
    :catch_0
    move-exception v1

    .line 38
    invoke-static {v0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$202(Z)Z

    .line 39
    invoke-static {v0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$302(Z)Z

    .line 40
    const-string v0, "VI/DisplayPolicy"

    const-string v2, "Initialization failed; original layout retained"

    invoke-static {v0, v2, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 42
    :goto_0
    return-void
.end method
