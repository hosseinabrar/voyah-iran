.class public final Lcom/android/server/wm/VoyahIranDisplayPolicy;
.super Ljava/lang/Object;
.source "VoyahIranDisplayPolicy.java"


# static fields
.field private static final ACK:Ljava/lang/String; = "voyah_iran_display_policy_ack"

.field private static final KEY:Ljava/lang/String; = "voyah_iran_display_policy"

.field private static final LEFT:I = 0x91

.field private static final TAG:Ljava/lang/String; = "VI/DisplayPolicy"

.field private static final TOP:I = 0x32

.field private static volatile enabled:Z

.field private static observer:Landroid/database/ContentObserver;

.field private static scheduled:Z


# direct methods
.method public constructor <init>()V
    .locals 0

    .line 14
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method static synthetic access$000()Landroid/database/ContentObserver;
    .locals 1

    .line 14
    sget-object v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->observer:Landroid/database/ContentObserver;

    return-object v0
.end method

.method static synthetic access$002(Landroid/database/ContentObserver;)Landroid/database/ContentObserver;
    .locals 0

    .line 14
    sput-object p0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->observer:Landroid/database/ContentObserver;

    return-object p0
.end method

.method static synthetic access$100(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V
    .locals 0

    .line 14
    invoke-static {p0, p1}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->refresh(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V

    return-void
.end method

.method static synthetic access$202(Z)Z
    .locals 0

    .line 14
    sput-boolean p0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->enabled:Z

    return p0
.end method

.method static synthetic access$302(Z)Z
    .locals 0

    .line 14
    sput-boolean p0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->scheduled:Z

    return p0
.end method

.method static synthetic access$400(Lcom/android/server/wm/ActivityRecord;)Z
    .locals 0

    .line 14
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->candidate(Lcom/android/server/wm/ActivityRecord;)Z

    move-result p0

    return p0
.end method

.method public static afterFrame(Lcom/android/server/wm/WindowState;Lcom/android/server/wm/WindowFrames;)V
    .locals 0

    .line 161
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;

    move-result-object p0

    .line 162
    if-eqz p0, :cond_0

    iget-object p1, p1, Lcom/android/server/wm/WindowFrames;->mFrame:Landroid/graphics/Rect;

    invoke-static {p1, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 163
    :cond_0
    return-void
.end method

.method public static applies(Lcom/android/server/wm/ActivityRecord;)Z
    .locals 1

    .line 79
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->initialize(Lcom/android/server/wm/ActivityRecord;)V

    .line 80
    sget-boolean v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->enabled:Z

    if-eqz v0, :cond_0

    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->candidate(Lcom/android/server/wm/ActivityRecord;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p0, 0x1

    goto :goto_0

    :cond_0
    const/4 p0, 0x0

    :goto_0
    return p0
.end method

.method public static beforeFrame(Lcom/android/server/wm/WindowState;Lcom/android/server/wm/WindowFrames;Landroid/graphics/Rect;Landroid/graphics/Rect;)V
    .locals 0

    .line 124
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->suppressLoginWelcome(Lcom/android/server/wm/WindowState;)V

    .line 125
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;

    move-result-object p0

    .line 126
    if-nez p0, :cond_0

    return-void

    .line 127
    :cond_0
    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 128
    invoke-static {p3, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 129
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mParentFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 130
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mDisplayFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 131
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mContainingFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 132
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mContentFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 133
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mVisibleFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 134
    iget-object p1, p1, Lcom/android/server/wm/WindowFrames;->mStableFrame:Landroid/graphics/Rect;

    invoke-static {p1, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 135
    return-void
.end method

.method private static candidate(Lcom/android/server/wm/ActivityRecord;)Z
    .locals 4

    .line 70
    const/4 v0, 0x0

    if-eqz p0, :cond_4

    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getDisplayId()I

    move-result v1

    const/4 v2, 0x1

    if-eqz v1, :cond_0

    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getDisplayId()I

    move-result v1

    if-ne v1, v2, :cond_4

    :cond_0
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getActivityType()I

    move-result v1

    if-ne v1, v2, :cond_4

    .line 71
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->inMultiWindowMode()Z

    move-result v1

    if-nez v1, :cond_4

    iget-object v1, p0, Lcom/android/server/wm/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    if-eqz v1, :cond_4

    iget-object v1, p0, Lcom/android/server/wm/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object v1, v1, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    if-nez v1, :cond_1

    goto :goto_0

    .line 72
    :cond_1
    iget-object p0, p0, Lcom/android/server/wm/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object p0, p0, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    .line 73
    const-string v1, "com.abrar.voyahiran"

    iget-object v3, p0, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-virtual {v1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_2

    iget v1, p0, Landroid/content/pm/ApplicationInfo;->flags:I

    and-int/lit16 v1, v1, 0x81

    if-nez v1, :cond_3

    iget p0, p0, Landroid/content/pm/ApplicationInfo;->uid:I

    const/16 v1, 0x2710

    if-lt p0, v1, :cond_3

    :cond_2
    move v0, v2

    :cond_3
    return v0

    .line 71
    :cond_4
    :goto_0
    return v0
.end method

.method private static clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V
    .locals 1

    .line 117
    if-eqz p0, :cond_0

    invoke-virtual {p0}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v0

    if-nez v0, :cond_0

    .line 119
    invoke-virtual {p0, p1}, Landroid/graphics/Rect;->intersect(Landroid/graphics/Rect;)Z

    move-result v0

    if-nez v0, :cond_0

    invoke-virtual {p0, p1}, Landroid/graphics/Rect;->set(Landroid/graphics/Rect;)V

    .line 121
    :cond_0
    return-void
.end method

.method public static configuration(Lcom/android/server/wm/ActivityRecord;Landroid/content/res/Configuration;)V
    .locals 6

    .line 89
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->applies(Lcom/android/server/wm/ActivityRecord;)Z

    move-result v0

    if-nez v0, :cond_0

    return-void

    .line 90
    :cond_0
    iget-object v0, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v0}, Landroid/app/WindowConfiguration;->getBounds()Landroid/graphics/Rect;

    move-result-object v0

    .line 91
    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v1

    const/16 v2, 0x1d1

    if-le v1, v2, :cond_5

    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v1

    const/16 v2, 0xfa

    if-le v1, v2, :cond_5

    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v1

    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v2

    if-gt v1, v2, :cond_1

    goto/16 :goto_1

    .line 92
    :cond_1
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getResolvedOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object v1

    .line 93
    new-instance v2, Landroid/graphics/Rect;

    iget v3, v0, Landroid/graphics/Rect;->left:I

    add-int/lit16 v3, v3, 0x91

    iget v4, v0, Landroid/graphics/Rect;->top:I

    add-int/lit8 v4, v4, 0x32

    iget v5, v0, Landroid/graphics/Rect;->right:I

    iget v0, v0, Landroid/graphics/Rect;->bottom:I

    invoke-direct {v2, v3, v4, v5, v0}, Landroid/graphics/Rect;-><init>(IIII)V

    .line 94
    new-instance v0, Landroid/graphics/Rect;

    invoke-direct {v0, v2}, Landroid/graphics/Rect;-><init>(Landroid/graphics/Rect;)V

    .line 95
    iget-object v3, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v3}, Landroid/app/WindowConfiguration;->getAppBounds()Landroid/graphics/Rect;

    move-result-object v3

    .line 96
    if-eqz v3, :cond_2

    invoke-virtual {v3}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v4

    if-nez v4, :cond_2

    invoke-virtual {v0, v3}, Landroid/graphics/Rect;->intersect(Landroid/graphics/Rect;)Z

    .line 97
    :cond_2
    iget-object v3, v1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v3, v2}, Landroid/app/WindowConfiguration;->setBounds(Landroid/graphics/Rect;)V

    .line 98
    iget-object v3, v1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v3, v0}, Landroid/app/WindowConfiguration;->setAppBounds(Landroid/graphics/Rect;)V

    .line 99
    const/4 v3, 0x2

    iput v3, v1, Landroid/content/res/Configuration;->orientation:I

    .line 100
    iget v3, v1, Landroid/content/res/Configuration;->densityDpi:I

    if-lez v3, :cond_3

    iget p1, v1, Landroid/content/res/Configuration;->densityDpi:I

    goto :goto_0

    :cond_3
    iget p1, p1, Landroid/content/res/Configuration;->densityDpi:I

    .line 101
    :goto_0
    const/16 v3, 0xa0

    if-gtz p1, :cond_4

    move p1, v3

    .line 102
    :cond_4
    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v4

    mul-int/2addr v4, v3

    div-int/2addr v4, p1

    iput v4, v1, Landroid/content/res/Configuration;->screenWidthDp:I

    .line 103
    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v4

    mul-int/2addr v4, v3

    div-int/2addr v4, p1

    iput v4, v1, Landroid/content/res/Configuration;->screenHeightDp:I

    .line 104
    iget p1, v1, Landroid/content/res/Configuration;->screenWidthDp:I

    iget v3, v1, Landroid/content/res/Configuration;->screenHeightDp:I

    invoke-static {p1, v3}, Ljava/lang/Math;->min(II)I

    move-result p1

    iput p1, v1, Landroid/content/res/Configuration;->smallestScreenWidthDp:I

    .line 105
    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "Bounds display="

    invoke-virtual {p1, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getDisplayId()I

    move-result v1

    invoke-virtual {p1, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string v1, " "

    invoke-virtual {p1, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    iget-object p0, p0, Lcom/android/server/wm/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object p0, p0, Landroid/content/pm/ActivityInfo;->packageName:Ljava/lang/String;

    invoke-virtual {p1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    move-result-object p0

    const-string p1, " content="

    invoke-virtual {p0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    const-string p1, "VI/DisplayPolicy"

    invoke-static {p1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 106
    return-void

    .line 91
    :cond_5
    :goto_1
    return-void
.end method

.method private static initialize(Lcom/android/server/wm/ActivityRecord;)V
    .locals 2

    .line 24
    sget-boolean v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->scheduled:Z

    if-nez v0, :cond_1

    if-eqz p0, :cond_1

    iget-object v0, p0, Lcom/android/server/wm/ActivityRecord;->mAtmService:Lcom/android/server/wm/ActivityTaskManagerService;

    if-nez v0, :cond_0

    goto :goto_0

    .line 25
    :cond_0
    const/4 v0, 0x1

    sput-boolean v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->scheduled:Z

    .line 26
    iget-object p0, p0, Lcom/android/server/wm/ActivityRecord;->mAtmService:Lcom/android/server/wm/ActivityTaskManagerService;

    .line 27
    new-instance v0, Landroid/os/Handler;

    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v1

    invoke-direct {v0, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V

    .line 28
    new-instance v1, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;

    invoke-direct {v1, p0, v0}, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;-><init>(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/os/Handler;)V

    invoke-virtual {v0, v1}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    .line 44
    return-void

    .line 24
    :cond_1
    :goto_0
    return-void
.end method

.method public static orientation(Lcom/android/server/wm/ActivityRecord;I)I
    .locals 1

    .line 85
    const/4 v0, -0x2

    if-eq p1, v0, :cond_0

    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->applies(Lcom/android/server/wm/ActivityRecord;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p1, 0x0

    :cond_0
    return p1
.end method

.method private static refresh(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V
    .locals 4

    .line 48
    :try_start_0
    const-string v0, "voyah_iran_display_policy"

    invoke-static {p1, v0}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    .line 49
    if-eqz v0, :cond_1

    const-string v1, "1"

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_1

    const-string v1, "1:[a-f0-9]{32}"

    invoke-virtual {v0, v1}, Ljava/lang/String;->matches(Ljava/lang/String;)Z

    move-result v1

    if-eqz v1, :cond_0

    goto :goto_0

    :cond_0
    const/4 v1, 0x0

    goto :goto_1

    :cond_1
    :goto_0
    const/4 v1, 0x1

    :goto_1
    sput-boolean v1, Lcom/android/server/wm/VoyahIranDisplayPolicy;->enabled:Z

    .line 50
    iget-object v1, p0, Lcom/android/server/wm/ActivityTaskManagerService;->mGlobalLock:Lcom/android/server/wm/WindowManagerGlobalLock;

    monitor-enter v1
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 51
    :try_start_1
    iget-object v2, p0, Lcom/android/server/wm/ActivityTaskManagerService;->mRootWindowContainer:Lcom/android/server/wm/RootWindowContainer;

    new-instance v3, Lcom/android/server/wm/VoyahIranDisplayPolicy$2;

    invoke-direct {v3}, Lcom/android/server/wm/VoyahIranDisplayPolicy$2;-><init>()V

    invoke-virtual {v2, v3}, Lcom/android/server/wm/RootWindowContainer;->forAllActivities(Ljava/util/function/Consumer;)V

    .line 60
    iget-object p0, p0, Lcom/android/server/wm/ActivityTaskManagerService;->mWindowManager:Lcom/android/server/wm/WindowManagerService;

    invoke-virtual {p0}, Lcom/android/server/wm/WindowManagerService;->requestTraversal()V

    .line 61
    monitor-exit v1
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 62
    :try_start_2
    const-string p0, "voyah_iran_display_policy_ack"

    if-nez v0, :cond_2

    const-string v0, "1:default"

    :cond_2
    invoke-static {p1, p0, v0}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 63
    const-string p0, "VI/DisplayPolicy"

    new-instance p1, Ljava/lang/StringBuilder;

    invoke-direct {p1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v0, "Applied enabled="

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    sget-boolean v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->enabled:Z

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object p1

    const-string v0, " displays=0,1 left=145 top=50"

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object p1

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {p0, p1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_2
    .catch Ljava/lang/RuntimeException; {:try_start_2 .. :try_end_2} :catch_0

    .line 66
    goto :goto_2

    .line 61
    :catchall_0
    move-exception p0

    :try_start_3
    monitor-exit v1
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_0

    :try_start_4
    throw p0
    :try_end_4
    .catch Ljava/lang/RuntimeException; {:try_start_4 .. :try_end_4} :catch_0

    .line 64
    :catch_0
    move-exception p0

    .line 65
    const-string p1, "VI/DisplayPolicy"

    const-string v0, "Reconfiguration failed; request not acknowledged"

    invoke-static {p1, v0, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 67
    :goto_2
    return-void
.end method

.method private static safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;
    .locals 3

    .line 110
    iget-object v0, p0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v0, v0, Landroid/view/WindowManager$LayoutParams;->type:I

    const/4 v1, 0x1

    const/4 v2, 0x0

    if-lt v0, v1, :cond_3

    iget-object v0, p0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v0, v0, Landroid/view/WindowManager$LayoutParams;->type:I

    const/16 v1, 0x7d0

    if-ge v0, v1, :cond_3

    iget-object v0, p0, Lcom/android/server/wm/WindowState;->mActivityRecord:Lcom/android/server/wm/ActivityRecord;

    invoke-static {v0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->applies(Lcom/android/server/wm/ActivityRecord;)Z

    move-result v0

    if-nez v0, :cond_0

    goto :goto_1

    .line 111
    :cond_0
    iget-object p0, p0, Lcom/android/server/wm/WindowState;->mActivityRecord:Lcom/android/server/wm/ActivityRecord;

    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getResolvedOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object p0

    iget-object p0, p0, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {p0}, Landroid/app/WindowConfiguration;->getBounds()Landroid/graphics/Rect;

    move-result-object p0

    .line 112
    iget v0, p0, Landroid/graphics/Rect;->left:I

    const/16 v1, 0x91

    if-lt v0, v1, :cond_2

    iget v0, p0, Landroid/graphics/Rect;->top:I

    const/16 v1, 0x32

    if-lt v0, v1, :cond_2

    invoke-virtual {p0}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v0

    if-eqz v0, :cond_1

    goto :goto_0

    .line 113
    :cond_1
    return-object p0

    .line 112
    :cond_2
    :goto_0
    return-object v2

    .line 110
    :cond_3
    :goto_1
    return-object v2
.end method

.method static supportsLoginWelcomeVariant(Ljava/lang/String;)Z
    .locals 1

    .line 138
    const-string v0, "(?i)(?<![a-z0-9])h97[ac](?![a-z0-9])"

    invoke-static {v0}, Ljava/util/regex/Pattern;->compile(Ljava/lang/String;)Ljava/util/regex/Pattern;

    move-result-object v0

    invoke-virtual {v0, p0}, Ljava/util/regex/Pattern;->matcher(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;

    move-result-object p0

    invoke-virtual {p0}, Ljava/util/regex/Matcher;->find()Z

    move-result p0

    return p0
.end method

.method private static suppressLoginWelcome(Lcom/android/server/wm/WindowState;)V
    .locals 5

    .line 142
    const-string v0, "VI/DisplayPolicy"

    iget-object v1, p0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    if-eqz v1, :cond_4

    iget-object v1, p0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget-object v1, v1, Landroid/view/WindowManager$LayoutParams;->packageName:Ljava/lang/String;

    const-string v2, "com.qinggan.app.login"

    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_4

    iget-object v1, p0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    .line 143
    invoke-virtual {v1}, Landroid/view/WindowManager$LayoutParams;->getTitle()Ljava/lang/CharSequence;

    move-result-object v1

    const-string v3, "LoginingView"

    invoke-virtual {v3, v1}, Ljava/lang/String;->contentEquals(Ljava/lang/CharSequence;)Z

    move-result v1

    if-nez v1, :cond_0

    goto :goto_1

    .line 144
    :cond_0
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    sget-object v3, Landroid/os/Build;->DEVICE:Ljava/lang/String;

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v3, " "

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    sget-object v4, Landroid/os/Build;->PRODUCT:Ljava/lang/String;

    invoke-virtual {v1, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    sget-object v4, Landroid/os/Build;->MODEL:Ljava/lang/String;

    invoke-virtual {v1, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    sget-object v4, Landroid/os/Build;->BOARD:Ljava/lang/String;

    invoke-virtual {v1, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    sget-object v3, Landroid/os/Build;->FINGERPRINT:Ljava/lang/String;

    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    sget-object v3, Ljava/util/Locale;->ROOT:Ljava/util/Locale;

    .line 146
    invoke-virtual {v1, v3}, Ljava/lang/String;->toLowerCase(Ljava/util/Locale;)Ljava/lang/String;

    move-result-object v1

    .line 147
    invoke-static {v1}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->supportsLoginWelcomeVariant(Ljava/lang/String;)Z

    move-result v1

    if-nez v1, :cond_1

    return-void

    .line 149
    :cond_1
    :try_start_0
    iget-object v1, p0, Lcom/android/server/wm/WindowState;->mContext:Landroid/content/Context;

    invoke-virtual {v1}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v1

    new-instance v3, Landroid/content/ComponentName;

    const-string v4, "com.qinggan.app.login.RootActivity"

    invoke-direct {v3, v2, v4}, Landroid/content/ComponentName;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    invoke-virtual {v1, v3}, Landroid/content/pm/PackageManager;->getComponentEnabledSetting(Landroid/content/ComponentName;)I

    move-result v1

    .line 151
    const/4 v2, 0x2

    if-eq v1, v2, :cond_2

    return-void

    .line 154
    :cond_2
    const/4 v1, 0x0

    invoke-virtual {p0, v1}, Lcom/android/server/wm/WindowState;->hideLw(Z)Z

    move-result p0

    if-eqz p0, :cond_3

    const-string p0, "Suppressed login welcome window"

    invoke-static {v0, p0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 157
    :cond_3
    goto :goto_0

    .line 155
    :catch_0
    move-exception p0

    .line 156
    const-string v1, "Cannot read login welcome policy"

    invoke-static {v0, v1, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 158
    :goto_0
    return-void

    .line 143
    :cond_4
    :goto_1
    return-void
.end method
