.class public final Lcom/android/server/wm/VoyahIranDisplayPolicy;
.super Ljava/lang/Object;
.source "VoyahIranDisplayPolicy.java"


# static fields
.field private static final ACK:Ljava/lang/String; = "voyah_iran_display_policy_ack"

.field private static final KEY:Ljava/lang/String; = "voyah_iran_display_policy"

.field private static final LEFT:I = 0x91

.field private static final TAG:Ljava/lang/String; = "VI/DisplayPolicy"

.field private static final THEME:Ljava/lang/String; = "voyah_iran_app_theme"

.field private static final TOP:I = 0x32

.field private static volatile enabled:Z

.field private static volatile nightMode:I

.field private static observer:Landroid/database/ContentObserver;

.field private static scheduled:Z

.field private static volatile themeMode:Ljava/lang/String;


# direct methods
.method static constructor <clinit>()V
    .locals 1

    .line 18
    const-string v0, "vehicle"

    sput-object v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->themeMode:Ljava/lang/String;

    return-void
.end method

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

.method static synthetic access$100()I
    .locals 1

    .line 14
    sget v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->nightMode:I

    return v0
.end method

.method static synthetic access$200()I
    .locals 1

    .line 14
    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->desiredNight()I

    move-result v0

    return v0
.end method

.method static synthetic access$300(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V
    .locals 0

    .line 14
    invoke-static {p0, p1}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->refresh(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V

    return-void
.end method

.method static synthetic access$402(Z)Z
    .locals 0

    .line 14
    sput-boolean p0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->enabled:Z

    return p0
.end method

.method static synthetic access$500()Z
    .locals 1

    .line 14
    sget-boolean v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->scheduled:Z

    return v0
.end method

.method static synthetic access$502(Z)Z
    .locals 0

    .line 14
    sput-boolean p0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->scheduled:Z

    return p0
.end method

.method static synthetic access$600(Lcom/android/server/wm/ActivityRecord;)Z
    .locals 0

    .line 14
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->candidate(Lcom/android/server/wm/ActivityRecord;)Z

    move-result p0

    return p0
.end method

.method public static afterFrame(Lcom/android/server/wm/WindowState;Lcom/android/server/wm/WindowFrames;)V
    .locals 0

    .line 164
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;

    move-result-object p0

    .line 165
    if-eqz p0, :cond_0

    iget-object p1, p1, Lcom/android/server/wm/WindowFrames;->mFrame:Landroid/graphics/Rect;

    invoke-static {p1, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 166
    :cond_0
    return-void
.end method

.method public static applies(Lcom/android/server/wm/ActivityRecord;)Z
    .locals 1

    .line 101
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->initialize(Lcom/android/server/wm/ActivityRecord;)V

    .line 102
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

    .line 151
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;

    move-result-object p0

    .line 152
    if-nez p0, :cond_0

    return-void

    .line 153
    :cond_0
    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 154
    invoke-static {p3, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 155
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mParentFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 156
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mDisplayFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 157
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mContainingFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 158
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mContentFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 159
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mVisibleFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 160
    iget-object p1, p1, Lcom/android/server/wm/WindowFrames;->mStableFrame:Landroid/graphics/Rect;

    invoke-static {p1, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 161
    return-void
.end method

.method private static candidate(Lcom/android/server/wm/ActivityRecord;)Z
    .locals 4

    .line 92
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

    .line 93
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->inMultiWindowMode()Z

    move-result v1

    if-nez v1, :cond_4

    iget-object v1, p0, Lcom/android/server/wm/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    if-eqz v1, :cond_4

    iget-object v1, p0, Lcom/android/server/wm/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object v1, v1, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    if-nez v1, :cond_1

    goto :goto_0

    .line 94
    :cond_1
    iget-object p0, p0, Lcom/android/server/wm/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object p0, p0, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    .line 95
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

    .line 93
    :cond_4
    :goto_0
    return v0
.end method

.method private static clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V
    .locals 1

    .line 144
    if-eqz p0, :cond_0

    invoke-virtual {p0}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v0

    if-nez v0, :cond_0

    .line 146
    invoke-virtual {p0, p1}, Landroid/graphics/Rect;->intersect(Landroid/graphics/Rect;)Z

    move-result v0

    if-nez v0, :cond_0

    invoke-virtual {p0, p1}, Landroid/graphics/Rect;->set(Landroid/graphics/Rect;)V

    .line 148
    :cond_0
    return-void
.end method

.method public static configuration(Lcom/android/server/wm/ActivityRecord;Landroid/content/res/Configuration;)V
    .locals 6

    .line 111
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->initialize(Lcom/android/server/wm/ActivityRecord;)V

    .line 112
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->candidate(Lcom/android/server/wm/ActivityRecord;)Z

    move-result v0

    if-eqz v0, :cond_0

    sget v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->nightMode:I

    if-eqz v0, :cond_0

    .line 113
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getResolvedOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object v0

    .line 114
    iget v1, v0, Landroid/content/res/Configuration;->uiMode:I

    and-int/lit8 v1, v1, -0x31

    sget v2, Lcom/android/server/wm/VoyahIranDisplayPolicy;->nightMode:I

    or-int/2addr v1, v2

    iput v1, v0, Landroid/content/res/Configuration;->uiMode:I

    .line 116
    :cond_0
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->applies(Lcom/android/server/wm/ActivityRecord;)Z

    move-result v0

    if-nez v0, :cond_1

    return-void

    .line 117
    :cond_1
    iget-object v0, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v0}, Landroid/app/WindowConfiguration;->getBounds()Landroid/graphics/Rect;

    move-result-object v0

    .line 118
    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v1

    const/16 v2, 0x1d1

    if-le v1, v2, :cond_6

    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v1

    const/16 v2, 0xfa

    if-le v1, v2, :cond_6

    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v1

    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v2

    if-gt v1, v2, :cond_2

    goto/16 :goto_1

    .line 119
    :cond_2
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getResolvedOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object v1

    .line 120
    new-instance v2, Landroid/graphics/Rect;

    iget v3, v0, Landroid/graphics/Rect;->left:I

    add-int/lit16 v3, v3, 0x91

    iget v4, v0, Landroid/graphics/Rect;->top:I

    add-int/lit8 v4, v4, 0x32

    iget v5, v0, Landroid/graphics/Rect;->right:I

    iget v0, v0, Landroid/graphics/Rect;->bottom:I

    invoke-direct {v2, v3, v4, v5, v0}, Landroid/graphics/Rect;-><init>(IIII)V

    .line 121
    new-instance v0, Landroid/graphics/Rect;

    invoke-direct {v0, v2}, Landroid/graphics/Rect;-><init>(Landroid/graphics/Rect;)V

    .line 122
    iget-object v3, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v3}, Landroid/app/WindowConfiguration;->getAppBounds()Landroid/graphics/Rect;

    move-result-object v3

    .line 123
    if-eqz v3, :cond_3

    invoke-virtual {v3}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v4

    if-nez v4, :cond_3

    invoke-virtual {v0, v3}, Landroid/graphics/Rect;->intersect(Landroid/graphics/Rect;)Z

    .line 124
    :cond_3
    iget-object v3, v1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v3, v2}, Landroid/app/WindowConfiguration;->setBounds(Landroid/graphics/Rect;)V

    .line 125
    iget-object v3, v1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v3, v0}, Landroid/app/WindowConfiguration;->setAppBounds(Landroid/graphics/Rect;)V

    .line 126
    const/4 v3, 0x2

    iput v3, v1, Landroid/content/res/Configuration;->orientation:I

    .line 127
    iget v3, v1, Landroid/content/res/Configuration;->densityDpi:I

    if-lez v3, :cond_4

    iget p1, v1, Landroid/content/res/Configuration;->densityDpi:I

    goto :goto_0

    :cond_4
    iget p1, p1, Landroid/content/res/Configuration;->densityDpi:I

    .line 128
    :goto_0
    const/16 v3, 0xa0

    if-gtz p1, :cond_5

    move p1, v3

    .line 129
    :cond_5
    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v4

    mul-int/2addr v4, v3

    div-int/2addr v4, p1

    iput v4, v1, Landroid/content/res/Configuration;->screenWidthDp:I

    .line 130
    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v4

    mul-int/2addr v4, v3

    div-int/2addr v4, p1

    iput v4, v1, Landroid/content/res/Configuration;->screenHeightDp:I

    .line 131
    iget p1, v1, Landroid/content/res/Configuration;->screenWidthDp:I

    iget v3, v1, Landroid/content/res/Configuration;->screenHeightDp:I

    invoke-static {p1, v3}, Ljava/lang/Math;->min(II)I

    move-result p1

    iput p1, v1, Landroid/content/res/Configuration;->smallestScreenWidthDp:I

    .line 132
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

    .line 133
    return-void

    .line 118
    :cond_6
    :goto_1
    return-void
.end method

.method private static desiredNight()I
    .locals 3

    .line 26
    sget-object v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->themeMode:Ljava/lang/String;

    const-string v1, "persist.sys.qg.currenttheme"

    const-string v2, "default"

    invoke-static {v1, v2}, Landroid/os/SystemProperties;->get(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->nightFor(Ljava/lang/String;Ljava/lang/String;)I

    move-result v0

    return v0
.end method

.method private static initialize(Lcom/android/server/wm/ActivityRecord;)V
    .locals 2

    .line 35
    sget-boolean v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->scheduled:Z

    if-nez v0, :cond_1

    if-eqz p0, :cond_1

    iget-object v0, p0, Lcom/android/server/wm/ActivityRecord;->mAtmService:Lcom/android/server/wm/ActivityTaskManagerService;

    if-nez v0, :cond_0

    goto :goto_0

    .line 36
    :cond_0
    const/4 v0, 0x1

    sput-boolean v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->scheduled:Z

    .line 37
    iget-object p0, p0, Lcom/android/server/wm/ActivityRecord;->mAtmService:Lcom/android/server/wm/ActivityTaskManagerService;

    .line 38
    new-instance v0, Landroid/os/Handler;

    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v1

    invoke-direct {v0, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V

    .line 39
    new-instance v1, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;

    invoke-direct {v1, p0, v0}, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;-><init>(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/os/Handler;)V

    invoke-virtual {v0, v1}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    .line 62
    return-void

    .line 35
    :cond_1
    :goto_0
    return-void
.end method

.method static nightFor(Ljava/lang/String;Ljava/lang/String;)I
    .locals 1

    .line 23
    const-string v0, "light"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_1

    const-string v0, "vehicle"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const-string p0, "simple"

    invoke-virtual {p0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    goto :goto_0

    :cond_0
    const/16 p0, 0x20

    goto :goto_1

    :cond_1
    :goto_0
    const/16 p0, 0x10

    :goto_1
    return p0
.end method

.method public static orientation(Lcom/android/server/wm/ActivityRecord;I)I
    .locals 1

    .line 107
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

    .line 66
    :try_start_0
    const-string v0, "voyah_iran_display_policy"

    invoke-static {p1, v0}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    .line 67
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

    .line 68
    const-string v1, "voyah_iran_app_theme"

    invoke-static {p1, v1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 69
    const-string v2, "dark"

    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_3

    const-string v2, "light"

    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_2

    goto :goto_2

    :cond_2
    const-string v1, "vehicle"

    :cond_3
    :goto_2
    sput-object v1, Lcom/android/server/wm/VoyahIranDisplayPolicy;->themeMode:Ljava/lang/String;

    .line 70
    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->desiredNight()I

    move-result v1

    sput v1, Lcom/android/server/wm/VoyahIranDisplayPolicy;->nightMode:I

    .line 71
    iget-object v1, p0, Lcom/android/server/wm/ActivityTaskManagerService;->mGlobalLock:Lcom/android/server/wm/WindowManagerGlobalLock;

    monitor-enter v1
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 72
    :try_start_1
    iget-object v2, p0, Lcom/android/server/wm/ActivityTaskManagerService;->mRootWindowContainer:Lcom/android/server/wm/RootWindowContainer;

    new-instance v3, Lcom/android/server/wm/VoyahIranDisplayPolicy$2;

    invoke-direct {v3}, Lcom/android/server/wm/VoyahIranDisplayPolicy$2;-><init>()V

    invoke-virtual {v2, v3}, Lcom/android/server/wm/RootWindowContainer;->forAllActivities(Ljava/util/function/Consumer;)V

    .line 81
    iget-object p0, p0, Lcom/android/server/wm/ActivityTaskManagerService;->mWindowManager:Lcom/android/server/wm/WindowManagerService;

    invoke-virtual {p0}, Lcom/android/server/wm/WindowManagerService;->requestTraversal()V

    .line 82
    monitor-exit v1
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 83
    :try_start_2
    const-string p0, "voyah_iran_display_policy_ack"

    if-nez v0, :cond_4

    const-string v0, "1:default"

    :cond_4
    invoke-static {p1, p0, v0}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 84
    const-string p0, "voyah_iran_app_theme_ack"

    sget-object v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->themeMode:Ljava/lang/String;

    invoke-static {p1, p0, v0}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 85
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

    .line 88
    goto :goto_3

    .line 82
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

    .line 86
    :catch_0
    move-exception p0

    .line 87
    const-string p1, "VI/DisplayPolicy"

    const-string v0, "Reconfiguration failed; request not acknowledged"

    invoke-static {p1, v0, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 89
    :goto_3
    return-void
.end method

.method private static safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;
    .locals 3

    .line 137
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

    .line 138
    :cond_0
    iget-object p0, p0, Lcom/android/server/wm/WindowState;->mActivityRecord:Lcom/android/server/wm/ActivityRecord;

    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getResolvedOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object p0

    iget-object p0, p0, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {p0}, Landroid/app/WindowConfiguration;->getBounds()Landroid/graphics/Rect;

    move-result-object p0

    .line 139
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

    .line 140
    :cond_1
    return-object p0

    .line 139
    :cond_2
    :goto_0
    return-object v2

    .line 137
    :cond_3
    :goto_1
    return-object v2
.end method
