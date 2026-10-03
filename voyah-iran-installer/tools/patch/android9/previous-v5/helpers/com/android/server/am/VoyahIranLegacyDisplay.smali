.class public final Lcom/android/server/am/VoyahIranLegacyDisplay;
.super Ljava/lang/Object;
.source "VoyahIranLegacyDisplay.java"


# static fields
.field private static final DENSITY:Ljava/lang/String; = "voyah_iran_app_density"

.field private static final KEY:Ljava/lang/String; = "voyah_iran_display_policy"

.field private static final THEME:Ljava/lang/String; = "voyah_iran_app_theme"

.field private static volatile appDensity:I

.field private static volatile enabled:Z

.field private static volatile nightMode:I

.field private static observer:Landroid/database/ContentObserver;

.field private static scheduled:Z

.field private static volatile themeMode:Ljava/lang/String;


# direct methods
.method static constructor <clinit>()V
    .registers 1

    .line 16
    const-string v0, "vehicle"

    sput-object v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->themeMode:Ljava/lang/String;

    .line 19
    const/16 v0, 0xa0

    sput v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->appDensity:I

    return-void
.end method

.method public constructor <init>()V
    .registers 1

    .line 13
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method static synthetic access$000()Landroid/database/ContentObserver;
    .registers 1

    .line 13
    sget-object v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->observer:Landroid/database/ContentObserver;

    return-object v0
.end method

.method static synthetic access$002(Landroid/database/ContentObserver;)Landroid/database/ContentObserver;
    .registers 1

    .line 13
    sput-object p0, Lcom/android/server/am/VoyahIranLegacyDisplay;->observer:Landroid/database/ContentObserver;

    return-object p0
.end method

.method static synthetic access$100()I
    .registers 1

    .line 13
    sget v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->nightMode:I

    return v0
.end method

.method static synthetic access$200()I
    .registers 1

    .line 13
    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->desiredNight()I

    move-result v0

    return v0
.end method

.method static synthetic access$300(Lcom/android/server/am/ActivityManagerService;Landroid/content/ContentResolver;)V
    .registers 2

    .line 13
    invoke-static {p0, p1}, Lcom/android/server/am/VoyahIranLegacyDisplay;->refresh(Lcom/android/server/am/ActivityManagerService;Landroid/content/ContentResolver;)V

    return-void
.end method

.method static synthetic access$402(Z)Z
    .registers 1

    .line 13
    sput-boolean p0, Lcom/android/server/am/VoyahIranLegacyDisplay;->enabled:Z

    return p0
.end method

.method static synthetic access$500()Z
    .registers 1

    .line 13
    sget-boolean v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->scheduled:Z

    return v0
.end method

.method static synthetic access$502(Z)Z
    .registers 1

    .line 13
    sput-boolean p0, Lcom/android/server/am/VoyahIranLegacyDisplay;->scheduled:Z

    return p0
.end method

.method public static applies(Ljava/lang/Object;)Z
    .registers 3

    .line 76
    instance-of v0, p0, Lcom/android/server/am/ActivityRecord;

    const/4 v1, 0x0

    if-nez v0, :cond_0

    return v1

    .line 77
    :cond_0
    check-cast p0, Lcom/android/server/am/ActivityRecord;

    .line 78
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->initialize(Lcom/android/server/am/ActivityRecord;)V

    .line 79
    sget-boolean v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->enabled:Z

    if-eqz v0, :cond_1

    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->candidate(Lcom/android/server/am/ActivityRecord;)Z

    move-result p0

    if-eqz p0, :cond_1

    const/4 v1, 0x1

    :cond_1
    return v1
.end method

.method private static applyDensity(Lcom/android/server/am/ActivityRecord;Landroid/content/res/Configuration;)V
    .registers 4

    .line 117
    sget v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->appDensity:I

    iput v0, p1, Landroid/content/res/Configuration;->densityDpi:I

    .line 118
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplay()Lcom/android/server/am/ActivityDisplay;

    move-result-object v0

    if-nez v0, :cond_0

    return-void

    .line 119
    :cond_0
    iget-object v0, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    if-nez v0, :cond_1

    const/4 v0, 0x0

    goto :goto_0

    :cond_1
    iget-object v0, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v0}, Landroid/app/WindowConfiguration;->getAppBounds()Landroid/graphics/Rect;

    move-result-object v0

    .line 120
    :goto_0
    if-eqz v0, :cond_2

    invoke-virtual {v0}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v1

    if-eqz v1, :cond_3

    :cond_2
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplay()Lcom/android/server/am/ActivityDisplay;

    move-result-object p0

    invoke-virtual {p0}, Lcom/android/server/am/ActivityDisplay;->getConfiguration()Landroid/content/res/Configuration;

    move-result-object p0

    iget-object p0, p0, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {p0}, Landroid/app/WindowConfiguration;->getBounds()Landroid/graphics/Rect;

    move-result-object v0

    .line 121
    :cond_3
    if-eqz v0, :cond_5

    invoke-virtual {v0}, Landroid/graphics/Rect;->isEmpty()Z

    move-result p0

    if-eqz p0, :cond_4

    goto :goto_1

    .line 122
    :cond_4
    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result p0

    mul-int/lit16 p0, p0, 0xa0

    sget v1, Lcom/android/server/am/VoyahIranLegacyDisplay;->appDensity:I

    div-int/2addr p0, v1

    iput p0, p1, Landroid/content/res/Configuration;->screenWidthDp:I

    .line 123
    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result p0

    mul-int/lit16 p0, p0, 0xa0

    sget v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->appDensity:I

    div-int/2addr p0, v0

    iput p0, p1, Landroid/content/res/Configuration;->screenHeightDp:I

    .line 124
    iget p0, p1, Landroid/content/res/Configuration;->screenWidthDp:I

    iget v0, p1, Landroid/content/res/Configuration;->screenHeightDp:I

    invoke-static {p0, v0}, Ljava/lang/Math;->min(II)I

    move-result p0

    iput p0, p1, Landroid/content/res/Configuration;->smallestScreenWidthDp:I

    .line 125
    return-void

    .line 121
    :cond_5
    :goto_1
    return-void
.end method

.method public static bounds(Lcom/android/server/am/ActivityRecord;Landroid/graphics/Rect;)V
    .registers 5

    .line 87
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->applies(Ljava/lang/Object;)Z

    move-result v0

    .line 88
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->updateTheme(Lcom/android/server/am/ActivityRecord;)V

    .line 89
    if-eqz v0, :cond_4

    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplay()Lcom/android/server/am/ActivityDisplay;

    move-result-object v0

    if-nez v0, :cond_0

    goto :goto_1

    .line 92
    :cond_0
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplay()Lcom/android/server/am/ActivityDisplay;

    move-result-object v0

    iget-object v0, v0, Lcom/android/server/am/ActivityDisplay;->mDisplay:Landroid/view/Display;

    if-nez v0, :cond_1

    return-void

    .line 93
    :cond_1
    new-instance v0, Landroid/graphics/Point;

    invoke-direct {v0}, Landroid/graphics/Point;-><init>()V

    .line 94
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplay()Lcom/android/server/am/ActivityDisplay;

    move-result-object p0

    iget-object p0, p0, Lcom/android/server/am/ActivityDisplay;->mDisplay:Landroid/view/Display;

    invoke-virtual {p0, v0}, Landroid/view/Display;->getRealSize(Landroid/graphics/Point;)V

    .line 95
    new-instance p0, Landroid/graphics/Rect;

    iget v1, v0, Landroid/graphics/Point;->x:I

    iget v0, v0, Landroid/graphics/Point;->y:I

    const/4 v2, 0x0

    invoke-direct {p0, v2, v2, v1, v0}, Landroid/graphics/Rect;-><init>(IIII)V

    .line 96
    invoke-virtual {p0}, Landroid/graphics/Rect;->width()I

    move-result v0

    const/16 v1, 0x1d1

    if-le v0, v1, :cond_3

    invoke-virtual {p0}, Landroid/graphics/Rect;->height()I

    move-result v0

    const/16 v1, 0xfa

    if-le v0, v1, :cond_3

    invoke-virtual {p0}, Landroid/graphics/Rect;->width()I

    move-result v0

    invoke-virtual {p0}, Landroid/graphics/Rect;->height()I

    move-result v1

    if-gt v0, v1, :cond_2

    goto :goto_0

    .line 97
    :cond_2
    iget v0, p0, Landroid/graphics/Rect;->left:I

    add-int/lit16 v0, v0, 0x91

    iget v1, p0, Landroid/graphics/Rect;->top:I

    add-int/lit8 v1, v1, 0x32

    iget v2, p0, Landroid/graphics/Rect;->right:I

    iget p0, p0, Landroid/graphics/Rect;->bottom:I

    invoke-virtual {p1, v0, v1, v2, p0}, Landroid/graphics/Rect;->set(IIII)V

    .line 98
    return-void

    .line 96
    :cond_3
    :goto_0
    return-void

    .line 89
    :cond_4
    :goto_1
    return-void
.end method

.method private static candidate(Lcom/android/server/am/ActivityRecord;)Z
    .registers 5

    .line 68
    const/4 v0, 0x0

    if-eqz p0, :cond_4

    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplayId()I

    move-result v1

    const/4 v2, 0x1

    if-eqz v1, :cond_0

    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplayId()I

    move-result v1

    if-ne v1, v2, :cond_4

    .line 69
    :cond_0
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getActivityType()I

    move-result v1

    if-ne v1, v2, :cond_4

    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->inMultiWindowMode()Z

    move-result v1

    if-nez v1, :cond_4

    iget-object v1, p0, Lcom/android/server/am/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    if-eqz v1, :cond_4

    iget-object v1, p0, Lcom/android/server/am/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object v1, v1, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    if-nez v1, :cond_1

    goto :goto_0

    .line 70
    :cond_1
    iget-object p0, p0, Lcom/android/server/am/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object p0, p0, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    .line 71
    const-string v1, "com.abrar.voyahiran"

    iget-object v3, p0, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-virtual {v1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_2

    iget v1, p0, Landroid/content/pm/ApplicationInfo;->uid:I

    const/16 v3, 0x2710

    if-lt v1, v3, :cond_3

    iget p0, p0, Landroid/content/pm/ApplicationInfo;->flags:I

    and-int/lit16 p0, p0, 0x81

    if-nez p0, :cond_3

    :cond_2
    move v0, v2

    :cond_3
    return v0

    .line 69
    :cond_4
    :goto_0
    return v0
.end method

.method public static configuration(Lcom/android/server/am/ActivityRecord;Landroid/content/res/Configuration;)V
    .registers 4

    .line 101
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->initialize(Lcom/android/server/am/ActivityRecord;)V

    .line 102
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->candidate(Lcom/android/server/am/ActivityRecord;)Z

    move-result v0

    if-eqz v0, :cond_0

    sget v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->nightMode:I

    if-eqz v0, :cond_0

    iget v0, p1, Landroid/content/res/Configuration;->uiMode:I

    and-int/lit8 v0, v0, -0x31

    sget v1, Lcom/android/server/am/VoyahIranLegacyDisplay;->nightMode:I

    or-int/2addr v0, v1

    iput v0, p1, Landroid/content/res/Configuration;->uiMode:I

    .line 103
    :cond_0
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->candidate(Lcom/android/server/am/ActivityRecord;)Z

    move-result v0

    if-eqz v0, :cond_1

    invoke-static {p0, p1}, Lcom/android/server/am/VoyahIranLegacyDisplay;->applyDensity(Lcom/android/server/am/ActivityRecord;Landroid/content/res/Configuration;)V

    .line 104
    :cond_1
    new-instance v0, Landroid/graphics/Rect;

    invoke-direct {v0}, Landroid/graphics/Rect;-><init>()V

    invoke-static {p0, v0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->bounds(Lcom/android/server/am/ActivityRecord;Landroid/graphics/Rect;)V

    .line 105
    invoke-virtual {v0}, Landroid/graphics/Rect;->isEmpty()Z

    move-result p0

    if-eqz p0, :cond_2

    return-void

    .line 106
    :cond_2
    sget p0, Lcom/android/server/am/VoyahIranLegacyDisplay;->appDensity:I

    .line 107
    if-gtz p0, :cond_3

    return-void

    .line 108
    :cond_3
    iget-object v1, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v1, v0}, Landroid/app/WindowConfiguration;->setBounds(Landroid/graphics/Rect;)V

    .line 109
    iget-object v1, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v1, v0}, Landroid/app/WindowConfiguration;->setAppBounds(Landroid/graphics/Rect;)V

    .line 110
    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v1

    mul-int/lit16 v1, v1, 0xa0

    div-int/2addr v1, p0

    iput v1, p1, Landroid/content/res/Configuration;->screenWidthDp:I

    .line 111
    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v0

    mul-int/lit16 v0, v0, 0xa0

    div-int/2addr v0, p0

    iput v0, p1, Landroid/content/res/Configuration;->screenHeightDp:I

    .line 112
    iget p0, p1, Landroid/content/res/Configuration;->screenWidthDp:I

    iget v0, p1, Landroid/content/res/Configuration;->screenHeightDp:I

    invoke-static {p0, v0}, Ljava/lang/Math;->min(II)I

    move-result p0

    iput p0, p1, Landroid/content/res/Configuration;->smallestScreenWidthDp:I

    .line 113
    const/4 p0, 0x2

    iput p0, p1, Landroid/content/res/Configuration;->orientation:I

    .line 114
    return-void
.end method

.method static densityFor(Ljava/lang/String;)I
    .registers 2

    .line 21
    const-string v0, "120"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    const/16 p0, 0x78

    return p0

    .line 22
    :cond_0
    const-string v0, "140"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_1

    const/16 p0, 0x8c

    return p0

    .line 23
    :cond_1
    const-string v0, "180"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_2

    const/16 p0, 0xb4

    return p0

    .line 24
    :cond_2
    const-string v0, "200"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_3

    const/16 p0, 0xc8

    return p0

    .line 25
    :cond_3
    const/16 p0, 0xa0

    return p0
.end method

.method private static desiredNight()I
    .registers 3

    .line 31
    sget-object v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->themeMode:Ljava/lang/String;

    const-string v1, "persist.sys.qg.currenttheme"

    const-string v2, "default"

    invoke-static {v1, v2}, Landroid/os/SystemProperties;->get(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/android/server/am/VoyahIranLegacyDisplay;->nightFor(Ljava/lang/String;Ljava/lang/String;)I

    move-result v0

    return v0
.end method

.method private static initialize(Lcom/android/server/am/ActivityRecord;)V
    .registers 3

    .line 38
    sget-boolean v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->scheduled:Z

    if-nez v0, :cond_1

    iget-object v0, p0, Lcom/android/server/am/ActivityRecord;->service:Lcom/android/server/am/ActivityManagerService;

    if-nez v0, :cond_0

    goto :goto_0

    .line 39
    :cond_0
    const/4 v0, 0x1

    sput-boolean v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->scheduled:Z

    .line 40
    iget-object p0, p0, Lcom/android/server/am/ActivityRecord;->service:Lcom/android/server/am/ActivityManagerService;

    .line 41
    new-instance v0, Landroid/os/Handler;

    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v1

    invoke-direct {v0, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V

    .line 42
    new-instance v1, Lcom/android/server/am/VoyahIranLegacyDisplay$1;

    invoke-direct {v1, p0, v0}, Lcom/android/server/am/VoyahIranLegacyDisplay$1;-><init>(Lcom/android/server/am/ActivityManagerService;Landroid/os/Handler;)V

    invoke-virtual {v0, v1}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    .line 65
    return-void

    .line 38
    :cond_1
    :goto_0
    return-void
.end method

.method static nightFor(Ljava/lang/String;Ljava/lang/String;)I
    .registers 3

    .line 28
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

.method public static orientation(Ljava/lang/Object;I)I
    .registers 3

    .line 83
    const/4 v0, -0x2

    if-eq p1, v0, :cond_0

    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->applies(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p1, 0x0

    :cond_0
    return p1
.end method

.method private static refresh(Lcom/android/server/am/ActivityManagerService;Landroid/content/ContentResolver;)V
    .registers 18

    .line 139
    move-object/from16 v1, p0

    move-object/from16 v0, p1

    :try_start_0
    const-string v2, "voyah_iran_display_policy"

    invoke-static {v0, v2}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    .line 140
    const/4 v4, 0x1

    if-eqz v2, :cond_1

    const-string v5, "1"

    invoke-virtual {v2, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v5

    if-nez v5, :cond_1

    const-string v5, "1:[a-f0-9]{32}"

    invoke-virtual {v2, v5}, Ljava/lang/String;->matches(Ljava/lang/String;)Z

    move-result v5

    if-eqz v5, :cond_0

    goto :goto_0

    :cond_0
    const/4 v5, 0x0

    goto :goto_1

    :cond_1
    :goto_0
    move v5, v4

    :goto_1
    sput-boolean v5, Lcom/android/server/am/VoyahIranLegacyDisplay;->enabled:Z

    .line 141
    const-string v5, "voyah_iran_app_theme"

    invoke-static {v0, v5}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v5

    .line 142
    const-string v6, "dark"

    invoke-virtual {v6, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v6

    if-nez v6, :cond_3

    const-string v6, "light"

    invoke-virtual {v6, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v6

    if-eqz v6, :cond_2

    goto :goto_2

    :cond_2
    const-string v5, "vehicle"

    :cond_3
    :goto_2
    sput-object v5, Lcom/android/server/am/VoyahIranLegacyDisplay;->themeMode:Ljava/lang/String;

    .line 143
    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->desiredNight()I

    move-result v5

    sput v5, Lcom/android/server/am/VoyahIranLegacyDisplay;->nightMode:I

    .line 144
    const-string v5, "voyah_iran_app_density"

    invoke-static {v0, v5}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v5

    invoke-static {v5}, Lcom/android/server/am/VoyahIranLegacyDisplay;->densityFor(Ljava/lang/String;)I

    move-result v5

    sput v5, Lcom/android/server/am/VoyahIranLegacyDisplay;->appDensity:I

    .line 145
    monitor-enter p0
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 146
    :try_start_1
    iget-object v5, v1, Lcom/android/server/am/ActivityManagerService;->mStackSupervisor:Lcom/android/server/am/ActivityStackSupervisor;

    .line 147
    invoke-virtual {v5}, Lcom/android/server/am/ActivityStackSupervisor;->getChildCount()I

    move-result v6

    sub-int/2addr v6, v4

    :goto_3
    if-ltz v6, :cond_9

    .line 148
    invoke-virtual {v5, v6}, Lcom/android/server/am/ActivityStackSupervisor;->getChildAt(I)Lcom/android/server/am/ActivityDisplay;

    move-result-object v7

    .line 149
    invoke-virtual {v7}, Lcom/android/server/am/ActivityDisplay;->getChildCount()I

    move-result v8

    sub-int/2addr v8, v4

    :goto_4
    if-ltz v8, :cond_8

    .line 150
    invoke-virtual {v7, v8}, Lcom/android/server/am/ActivityDisplay;->getChildAt(I)Lcom/android/server/am/ActivityStack;

    move-result-object v9

    .line 151
    invoke-virtual {v9}, Lcom/android/server/am/ActivityStack;->getChildCount()I

    move-result v10

    sub-int/2addr v10, v4

    :goto_5
    if-ltz v10, :cond_7

    .line 152
    invoke-virtual {v9, v10}, Lcom/android/server/am/ActivityStack;->getChildAt(I)Lcom/android/server/wm/ConfigurationContainer;

    move-result-object v11

    check-cast v11, Lcom/android/server/am/TaskRecord;

    .line 153
    invoke-virtual {v11}, Lcom/android/server/am/TaskRecord;->getChildCount()I

    move-result v12

    sub-int/2addr v12, v4

    :goto_6
    if-ltz v12, :cond_6

    .line 154
    invoke-virtual {v11, v12}, Lcom/android/server/am/TaskRecord;->getChildAt(I)Lcom/android/server/am/ActivityRecord;

    move-result-object v13

    .line 155
    invoke-static {v13}, Lcom/android/server/am/VoyahIranLegacyDisplay;->candidate(Lcom/android/server/am/ActivityRecord;)Z

    move-result v14

    if-eqz v14, :cond_5

    .line 156
    invoke-static {v13}, Lcom/android/server/am/VoyahIranLegacyDisplay;->updateTheme(Lcom/android/server/am/ActivityRecord;)V

    .line 157
    invoke-virtual {v13}, Lcom/android/server/am/ActivityRecord;->getBounds()Landroid/graphics/Rect;

    move-result-object v14

    .line 158
    sget-boolean v15, Lcom/android/server/am/VoyahIranLegacyDisplay;->enabled:Z

    if-nez v15, :cond_4

    iget v15, v14, Landroid/graphics/Rect;->left:I

    const/16 v3, 0x91

    if-ne v15, v3, :cond_4

    iget v3, v14, Landroid/graphics/Rect;->top:I

    const/16 v14, 0x32

    if-ne v3, v14, :cond_4

    new-instance v3, Landroid/graphics/Rect;

    invoke-direct {v3}, Landroid/graphics/Rect;-><init>()V

    invoke-virtual {v13, v3}, Lcom/android/server/am/ActivityRecord;->setBounds(Landroid/graphics/Rect;)I

    .line 159
    :cond_4
    const/4 v3, 0x0

    invoke-virtual {v13, v3, v4}, Lcom/android/server/am/ActivityRecord;->ensureActivityConfiguration(IZ)Z

    goto :goto_7

    .line 155
    :cond_5
    const/4 v3, 0x0

    .line 153
    :goto_7
    add-int/lit8 v12, v12, -0x1

    goto :goto_6

    .line 151
    :cond_6
    const/4 v3, 0x0

    add-int/lit8 v10, v10, -0x1

    goto :goto_5

    .line 149
    :cond_7
    const/4 v3, 0x0

    add-int/lit8 v8, v8, -0x1

    goto :goto_4

    .line 147
    :cond_8
    const/4 v3, 0x0

    add-int/lit8 v6, v6, -0x1

    goto :goto_3

    .line 165
    :cond_9
    iget-object v3, v1, Lcom/android/server/am/ActivityManagerService;->mWindowManager:Lcom/android/server/wm/WindowManagerService;

    invoke-static {v3}, Lcom/android/server/wm/VoyahIranLegacyFrames;->traverse(Lcom/android/server/wm/WindowManagerService;)V

    .line 166
    monitor-exit p0
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 167
    :try_start_2
    const-string v1, "voyah_iran_display_policy_ack"

    if-nez v2, :cond_a

    const-string v2, "1:default"

    :cond_a
    invoke-static {v0, v1, v2}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 168
    const-string v1, "voyah_iran_app_theme_ack"

    sget-object v2, Lcom/android/server/am/VoyahIranLegacyDisplay;->themeMode:Ljava/lang/String;

    invoke-static {v0, v1, v2}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 169
    const-string v1, "voyah_iran_app_density_ack"

    sget v2, Lcom/android/server/am/VoyahIranLegacyDisplay;->appDensity:I

    invoke-static {v2}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;

    move-result-object v2

    invoke-static {v0, v1, v2}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 170
    const-string v0, "VI/DisplayPolicy9"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "Applied enabled="

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    sget-boolean v2, Lcom/android/server/am/VoyahIranLegacyDisplay;->enabled:Z

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v2, " displays=0,1 left=145 top=50"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_2
    .catch Ljava/lang/RuntimeException; {:try_start_2 .. :try_end_2} :catch_0

    .line 173
    goto :goto_8

    .line 166
    :catchall_0
    move-exception v0

    :try_start_3
    monitor-exit p0
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_0

    :try_start_4
    throw v0
    :try_end_4
    .catch Ljava/lang/RuntimeException; {:try_start_4 .. :try_end_4} :catch_0

    .line 171
    :catch_0
    move-exception v0

    .line 172
    const-string v1, "VI/DisplayPolicy9"

    const-string v2, "Reconfiguration failed; no acknowledgement"

    invoke-static {v1, v2, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 174
    :goto_8
    return-void
.end method

.method private static updateTheme(Lcom/android/server/am/ActivityRecord;)V
    .registers 4

    .line 128
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->candidate(Lcom/android/server/am/ActivityRecord;)Z

    move-result v0

    if-eqz v0, :cond_2

    sget v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->nightMode:I

    if-nez v0, :cond_0

    goto :goto_0

    .line 129
    :cond_0
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object v0

    .line 130
    iget v1, v0, Landroid/content/res/Configuration;->uiMode:I

    and-int/lit8 v1, v1, 0x30

    sget v2, Lcom/android/server/am/VoyahIranLegacyDisplay;->nightMode:I

    if-ne v1, v2, :cond_1

    iget v1, v0, Landroid/content/res/Configuration;->densityDpi:I

    sget v2, Lcom/android/server/am/VoyahIranLegacyDisplay;->appDensity:I

    if-ne v1, v2, :cond_1

    return-void

    .line 131
    :cond_1
    new-instance v1, Landroid/content/res/Configuration;

    invoke-direct {v1, v0}, Landroid/content/res/Configuration;-><init>(Landroid/content/res/Configuration;)V

    .line 132
    iget v0, v1, Landroid/content/res/Configuration;->uiMode:I

    and-int/lit8 v0, v0, -0x31

    sget v2, Lcom/android/server/am/VoyahIranLegacyDisplay;->nightMode:I

    or-int/2addr v0, v2

    iput v0, v1, Landroid/content/res/Configuration;->uiMode:I

    .line 133
    invoke-static {p0, v1}, Lcom/android/server/am/VoyahIranLegacyDisplay;->applyDensity(Lcom/android/server/am/ActivityRecord;Landroid/content/res/Configuration;)V

    .line 134
    invoke-virtual {p0, v1}, Lcom/android/server/am/ActivityRecord;->onOverrideConfigurationChanged(Landroid/content/res/Configuration;)V

    .line 135
    return-void

    .line 128
    :cond_2
    :goto_0
    return-void
.end method
