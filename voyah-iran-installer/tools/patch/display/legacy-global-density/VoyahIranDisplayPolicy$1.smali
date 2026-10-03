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

    .line 48
    iput-object p1, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$service:Lcom/android/server/wm/ActivityTaskManagerService;

    iput-object p2, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$handler:Landroid/os/Handler;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .locals 7

    .line 51
    const-wide/16 v0, 0x7d0

    const/4 v2, 0x0

    :try_start_0
    iget-object v3, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$service:Lcom/android/server/wm/ActivityTaskManagerService;

    iget-object v3, v3, Lcom/android/server/wm/ActivityTaskManagerService;->mContext:Landroid/content/Context;

    invoke-virtual {v3}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v3

    .line 52
    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$000()Landroid/database/ContentObserver;

    move-result-object v4

    if-eqz v4, :cond_2

    .line 53
    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$100()I

    move-result v4

    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$200()I

    move-result v5

    if-eq v4, v5, :cond_0

    iget-object v4, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$service:Lcom/android/server/wm/ActivityTaskManagerService;

    invoke-static {v4, v3}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$300(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 68
    :cond_0
    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$500()Z

    move-result v2

    if-eqz v2, :cond_1

    iget-object v2, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$handler:Landroid/os/Handler;

    invoke-virtual {v2, p0, v0, v1}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z

    .line 54
    :cond_1
    return-void

    .line 56
    :cond_2
    :try_start_1
    new-instance v4, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;

    iget-object v5, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$handler:Landroid/os/Handler;

    iget-object v6, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$service:Lcom/android/server/wm/ActivityTaskManagerService;

    invoke-direct {v4, p0, v5, v6, v3}, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;-><init>(Lcom/android/server/wm/VoyahIranDisplayPolicy$1;Landroid/os/Handler;Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V

    invoke-static {v4}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$002(Landroid/database/ContentObserver;)Landroid/database/ContentObserver;

    .line 59
    const-string v4, "voyah_iran_display_policy"

    invoke-static {v4}, Landroid/provider/Settings$Global;->getUriFor(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v4

    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$000()Landroid/database/ContentObserver;

    move-result-object v5

    invoke-virtual {v3, v4, v2, v5}, Landroid/content/ContentResolver;->registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V

    .line 60
    const-string v4, "voyah_iran_app_theme"

    invoke-static {v4}, Landroid/provider/Settings$Global;->getUriFor(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v4

    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$000()Landroid/database/ContentObserver;

    move-result-object v5

    invoke-virtual {v3, v4, v2, v5}, Landroid/content/ContentResolver;->registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V

    .line 61
    const-string v4, "voyah_iran_app_density"

    invoke-static {v4}, Landroid/provider/Settings$Global;->getUriFor(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v4

    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$000()Landroid/database/ContentObserver;

    move-result-object v5

    invoke-virtual {v3, v4, v2, v5}, Landroid/content/ContentResolver;->registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V

    .line 62
    iget-object v4, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$service:Lcom/android/server/wm/ActivityTaskManagerService;

    invoke-static {v4, v3}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$300(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V
    :try_end_1
    .catch Ljava/lang/RuntimeException; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 68
    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$500()Z

    move-result v2

    if-eqz v2, :cond_3

    goto :goto_0

    :catchall_0
    move-exception v2

    goto :goto_1

    .line 63
    :catch_0
    move-exception v3

    .line 64
    :try_start_2
    invoke-static {v2}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$402(Z)Z

    .line 65
    invoke-static {v2}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$502(Z)Z

    .line 66
    const-string v2, "VI/DisplayPolicy"

    const-string v4, "Initialization failed; original layout retained"

    invoke-static {v2, v4, v3}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    .line 68
    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$500()Z

    move-result v2

    if-eqz v2, :cond_3

    :goto_0
    iget-object v2, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$handler:Landroid/os/Handler;

    invoke-virtual {v2, p0, v0, v1}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z

    .line 70
    :cond_3
    return-void

    .line 68
    :goto_1
    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$500()Z

    move-result v3

    if-eqz v3, :cond_4

    iget-object v3, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$handler:Landroid/os/Handler;

    invoke-virtual {v3, p0, v0, v1}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z

    .line 69
    :cond_4
    throw v2
.end method
