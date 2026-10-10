.class Lcom/android/server/am/VoyahIranLegacyDisplay$1;
.super Ljava/lang/Object;
.source "VoyahIranLegacyDisplay.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/android/server/am/VoyahIranLegacyDisplay;->initialize(Lcom/android/server/am/ActivityRecord;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic val$handler:Landroid/os/Handler;

.field final synthetic val$service:Lcom/android/server/am/ActivityManagerService;


# direct methods
.method constructor <init>(Lcom/android/server/am/ActivityManagerService;Landroid/os/Handler;)V
    .registers 3
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .line 61
    iput-object p1, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1;->val$service:Lcom/android/server/am/ActivityManagerService;

    iput-object p2, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1;->val$handler:Landroid/os/Handler;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .registers 8

    .line 64
    const-wide/16 v0, 0x7d0

    const/4 v2, 0x0

    :try_start_0
    iget-object v3, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1;->val$service:Lcom/android/server/am/ActivityManagerService;

    iget-object v3, v3, Lcom/android/server/am/ActivityManagerService;->mContext:Landroid/content/Context;

    invoke-virtual {v3}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v3

    .line 65
    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$000()Landroid/database/ContentObserver;

    move-result-object v4

    if-eqz v4, :cond_2

    .line 66
    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$100()I

    move-result v4

    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$200()I

    move-result v5

    if-eq v4, v5, :cond_0

    iget-object v4, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1;->val$service:Lcom/android/server/am/ActivityManagerService;

    invoke-static {v4, v3}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$300(Lcom/android/server/am/ActivityManagerService;Landroid/content/ContentResolver;)V
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 81
    :cond_0
    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$500()Z

    move-result v2

    if-eqz v2, :cond_1

    iget-object v2, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1;->val$handler:Landroid/os/Handler;

    invoke-virtual {v2, p0, v0, v1}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z

    .line 67
    :cond_1
    return-void

    .line 69
    :cond_2
    :try_start_1
    new-instance v4, Lcom/android/server/am/VoyahIranLegacyDisplay$1$1;

    iget-object v5, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1;->val$handler:Landroid/os/Handler;

    iget-object v6, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1;->val$service:Lcom/android/server/am/ActivityManagerService;

    invoke-direct {v4, p0, v5, v6, v3}, Lcom/android/server/am/VoyahIranLegacyDisplay$1$1;-><init>(Lcom/android/server/am/VoyahIranLegacyDisplay$1;Landroid/os/Handler;Lcom/android/server/am/ActivityManagerService;Landroid/content/ContentResolver;)V

    invoke-static {v4}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$002(Landroid/database/ContentObserver;)Landroid/database/ContentObserver;

    .line 72
    const-string v4, "voyah_iran_display_policy"

    invoke-static {v4}, Landroid/provider/Settings$Global;->getUriFor(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v4

    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$000()Landroid/database/ContentObserver;

    move-result-object v5

    invoke-virtual {v3, v4, v2, v5}, Landroid/content/ContentResolver;->registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V

    .line 73
    const-string v4, "voyah_iran_app_theme"

    invoke-static {v4}, Landroid/provider/Settings$Global;->getUriFor(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v4

    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$000()Landroid/database/ContentObserver;

    move-result-object v5

    invoke-virtual {v3, v4, v2, v5}, Landroid/content/ContentResolver;->registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V

    .line 74
    const-string v4, "voyah_iran_app_density"

    invoke-static {v4}, Landroid/provider/Settings$Global;->getUriFor(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v4

    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$000()Landroid/database/ContentObserver;

    move-result-object v5

    invoke-virtual {v3, v4, v2, v5}, Landroid/content/ContentResolver;->registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V

    .line 75
    const-string v4, "voyah_iran_app_density_packages"

    invoke-static {v4}, Landroid/provider/Settings$Global;->getUriFor(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v4

    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$000()Landroid/database/ContentObserver;

    move-result-object v5

    invoke-virtual {v3, v4, v2, v5}, Landroid/content/ContentResolver;->registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V

    .line 76
    iget-object v4, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1;->val$service:Lcom/android/server/am/ActivityManagerService;

    invoke-static {v4, v3}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$300(Lcom/android/server/am/ActivityManagerService;Landroid/content/ContentResolver;)V
    :try_end_1
    .catch Ljava/lang/RuntimeException; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 81
    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$500()Z

    move-result v2

    if-eqz v2, :cond_3

    goto :goto_0

    :catchall_0
    move-exception v2

    goto :goto_1

    .line 77
    :catch_0
    move-exception v3

    .line 78
    :try_start_2
    invoke-static {v2}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$402(Z)Z

    invoke-static {v2}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$502(Z)Z

    .line 79
    const-string v2, "VI/DisplayPolicy9"

    const-string v4, "Initialization failed"

    invoke-static {v2, v4, v3}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    .line 81
    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$500()Z

    move-result v2

    if-eqz v2, :cond_3

    :goto_0
    iget-object v2, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1;->val$handler:Landroid/os/Handler;

    invoke-virtual {v2, p0, v0, v1}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z

    .line 83
    :cond_3
    return-void

    .line 81
    :goto_1
    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->access$500()Z

    move-result v3

    if-eqz v3, :cond_4

    iget-object v3, p0, Lcom/android/server/am/VoyahIranLegacyDisplay$1;->val$handler:Landroid/os/Handler;

    invoke-virtual {v3, p0, v0, v1}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z

    .line 82
    :cond_4
    throw v2
.end method
