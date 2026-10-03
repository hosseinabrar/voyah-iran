.class public final Lcom/android/server/am/VoyahIranLegacyDisplay;
.super Ljava/lang/Object;
.source "VoyahIranLegacyDisplay.java"


# static fields
.field private static final DENSITY:Ljava/lang/String; = "voyah_iran_app_density"

.field private static final KEY:Ljava/lang/String; = "voyah_iran_display_policy"

.field private static final PACKAGE_DENSITY:Ljava/lang/String; = "voyah_iran_app_density_packages"

.field private static final THEME:Ljava/lang/String; = "voyah_iran_app_theme"

.field private static volatile appDensity:I

.field private static volatile enabled:Z

.field private static volatile nightMode:I

.field private static observer:Landroid/database/ContentObserver;

.field private static volatile packageDensities:Ljava/util/Map;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/Integer;",
            ">;"
        }
    .end annotation
.end field

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

    .line 21
    invoke-static {}, Ljava/util/Collections;->emptyMap()Ljava/util/Map;

    move-result-object v0

    sput-object v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->packageDensities:Ljava/util/Map;

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

    .line 96
    instance-of v0, p0, Lcom/android/server/am/ActivityRecord;

    const/4 v1, 0x0

    if-nez v0, :cond_0

    return v1

    .line 97
    :cond_0
    check-cast p0, Lcom/android/server/am/ActivityRecord;

    .line 98
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->initialize(Lcom/android/server/am/ActivityRecord;)V

    .line 99
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
    .registers 5

    .line 144
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->densityForActivity(Lcom/android/server/am/ActivityRecord;)I

    move-result v0

    .line 145
    iput v0, p1, Landroid/content/res/Configuration;->densityDpi:I

    .line 146
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplay()Lcom/android/server/am/ActivityDisplay;

    move-result-object v1

    if-nez v1, :cond_0

    return-void

    .line 147
    :cond_0
    iget-object v1, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    if-nez v1, :cond_1

    const/4 v1, 0x0

    goto :goto_0

    :cond_1
    iget-object v1, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v1}, Landroid/app/WindowConfiguration;->getAppBounds()Landroid/graphics/Rect;

    move-result-object v1

    .line 148
    :goto_0
    if-eqz v1, :cond_2

    invoke-virtual {v1}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v2

    if-eqz v2, :cond_3

    :cond_2
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplay()Lcom/android/server/am/ActivityDisplay;

    move-result-object p0

    invoke-virtual {p0}, Lcom/android/server/am/ActivityDisplay;->getConfiguration()Landroid/content/res/Configuration;

    move-result-object p0

    iget-object p0, p0, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {p0}, Landroid/app/WindowConfiguration;->getBounds()Landroid/graphics/Rect;

    move-result-object v1

    .line 149
    :cond_3
    if-eqz v1, :cond_5

    invoke-virtual {v1}, Landroid/graphics/Rect;->isEmpty()Z

    move-result p0

    if-eqz p0, :cond_4

    goto :goto_1

    .line 150
    :cond_4
    invoke-virtual {v1}, Landroid/graphics/Rect;->width()I

    move-result p0

    mul-int/lit16 p0, p0, 0xa0

    div-int/2addr p0, v0

    iput p0, p1, Landroid/content/res/Configuration;->screenWidthDp:I

    .line 151
    invoke-virtual {v1}, Landroid/graphics/Rect;->height()I

    move-result p0

    mul-int/lit16 p0, p0, 0xa0

    div-int/2addr p0, v0

    iput p0, p1, Landroid/content/res/Configuration;->screenHeightDp:I

    .line 152
    iget p0, p1, Landroid/content/res/Configuration;->screenWidthDp:I

    iget v0, p1, Landroid/content/res/Configuration;->screenHeightDp:I

    invoke-static {p0, v0}, Ljava/lang/Math;->min(II)I

    move-result p0

    iput p0, p1, Landroid/content/res/Configuration;->smallestScreenWidthDp:I

    .line 153
    return-void

    .line 149
    :cond_5
    :goto_1
    return-void
.end method

.method public static bounds(Lcom/android/server/am/ActivityRecord;Landroid/graphics/Rect;)V
    .registers 5

    .line 114
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->applies(Ljava/lang/Object;)Z

    move-result v0

    .line 115
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->updateTheme(Lcom/android/server/am/ActivityRecord;)V

    .line 116
    if-eqz v0, :cond_4

    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplay()Lcom/android/server/am/ActivityDisplay;

    move-result-object v0

    if-nez v0, :cond_0

    goto :goto_1

    .line 119
    :cond_0
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplay()Lcom/android/server/am/ActivityDisplay;

    move-result-object v0

    iget-object v0, v0, Lcom/android/server/am/ActivityDisplay;->mDisplay:Landroid/view/Display;

    if-nez v0, :cond_1

    return-void

    .line 120
    :cond_1
    new-instance v0, Landroid/graphics/Point;

    invoke-direct {v0}, Landroid/graphics/Point;-><init>()V

    .line 121
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplay()Lcom/android/server/am/ActivityDisplay;

    move-result-object p0

    iget-object p0, p0, Lcom/android/server/am/ActivityDisplay;->mDisplay:Landroid/view/Display;

    invoke-virtual {p0, v0}, Landroid/view/Display;->getRealSize(Landroid/graphics/Point;)V

    .line 122
    new-instance p0, Landroid/graphics/Rect;

    iget v1, v0, Landroid/graphics/Point;->x:I

    iget v0, v0, Landroid/graphics/Point;->y:I

    const/4 v2, 0x0

    invoke-direct {p0, v2, v2, v1, v0}, Landroid/graphics/Rect;-><init>(IIII)V

    .line 123
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

    .line 124
    :cond_2
    iget v0, p0, Landroid/graphics/Rect;->left:I

    add-int/lit16 v0, v0, 0x91

    iget v1, p0, Landroid/graphics/Rect;->top:I

    add-int/lit8 v1, v1, 0x32

    iget v2, p0, Landroid/graphics/Rect;->right:I

    iget p0, p0, Landroid/graphics/Rect;->bottom:I

    invoke-virtual {p1, v0, v1, v2, p0}, Landroid/graphics/Rect;->set(IIII)V

    .line 125
    return-void

    .line 123
    :cond_3
    :goto_0
    return-void

    .line 116
    :cond_4
    :goto_1
    return-void
.end method

.method private static candidate(Lcom/android/server/am/ActivityRecord;)Z
    .registers 5

    .line 88
    const/4 v0, 0x0

    if-eqz p0, :cond_4

    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplayId()I

    move-result v1

    const/4 v2, 0x1

    if-eqz v1, :cond_0

    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplayId()I

    move-result v1

    if-ne v1, v2, :cond_4

    .line 89
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

    .line 90
    :cond_1
    iget-object p0, p0, Lcom/android/server/am/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object p0, p0, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    .line 91
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

    .line 89
    :cond_4
    :goto_0
    return v0
.end method

.method public static configuration(Lcom/android/server/am/ActivityRecord;Landroid/content/res/Configuration;)V
    .registers 4

    .line 128
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->initialize(Lcom/android/server/am/ActivityRecord;)V

    .line 129
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

    .line 130
    :cond_0
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->densityCandidate(Lcom/android/server/am/ActivityRecord;)Z

    move-result v0

    if-eqz v0, :cond_1

    invoke-static {p0, p1}, Lcom/android/server/am/VoyahIranLegacyDisplay;->applyDensity(Lcom/android/server/am/ActivityRecord;Landroid/content/res/Configuration;)V

    .line 131
    :cond_1
    new-instance v0, Landroid/graphics/Rect;

    invoke-direct {v0}, Landroid/graphics/Rect;-><init>()V

    invoke-static {p0, v0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->bounds(Lcom/android/server/am/ActivityRecord;Landroid/graphics/Rect;)V

    .line 132
    invoke-virtual {v0}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v1

    if-eqz v1, :cond_2

    return-void

    .line 133
    :cond_2
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->densityCandidate(Lcom/android/server/am/ActivityRecord;)Z

    move-result v1

    if-eqz v1, :cond_3

    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->densityForActivity(Lcom/android/server/am/ActivityRecord;)I

    move-result p0

    goto :goto_0

    :cond_3
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getDisplay()Lcom/android/server/am/ActivityDisplay;

    move-result-object p0

    invoke-virtual {p0}, Lcom/android/server/am/ActivityDisplay;->getConfiguration()Landroid/content/res/Configuration;

    move-result-object p0

    iget p0, p0, Landroid/content/res/Configuration;->densityDpi:I

    .line 134
    :goto_0
    if-gtz p0, :cond_4

    return-void

    .line 135
    :cond_4
    iget-object v1, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v1, v0}, Landroid/app/WindowConfiguration;->setBounds(Landroid/graphics/Rect;)V

    .line 136
    iget-object v1, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v1, v0}, Landroid/app/WindowConfiguration;->setAppBounds(Landroid/graphics/Rect;)V

    .line 137
    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v1

    mul-int/lit16 v1, v1, 0xa0

    div-int/2addr v1, p0

    iput v1, p1, Landroid/content/res/Configuration;->screenWidthDp:I

    .line 138
    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v0

    mul-int/lit16 v0, v0, 0xa0

    div-int/2addr v0, p0

    iput v0, p1, Landroid/content/res/Configuration;->screenHeightDp:I

    .line 139
    iget p0, p1, Landroid/content/res/Configuration;->screenWidthDp:I

    iget v0, p1, Landroid/content/res/Configuration;->screenHeightDp:I

    invoke-static {p0, v0}, Ljava/lang/Math;->min(II)I

    move-result p0

    iput p0, p1, Landroid/content/res/Configuration;->smallestScreenWidthDp:I

    .line 140
    const/4 p0, 0x2

    iput p0, p1, Landroid/content/res/Configuration;->orientation:I

    .line 141
    return-void
.end method

.method private static densityCandidate(Lcom/android/server/am/ActivityRecord;)Z
    .registers 4

    .line 103
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->candidate(Lcom/android/server/am/ActivityRecord;)Z

    move-result v0

    const/4 v1, 0x0

    if-nez v0, :cond_0

    return v1

    .line 104
    :cond_0
    iget-object p0, p0, Lcom/android/server/am/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object p0, p0, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    .line 105
    const-string v0, "com.abrar.voyahiran"

    iget-object v2, p0, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-virtual {v0, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_1

    iget v0, p0, Landroid/content/pm/ApplicationInfo;->uid:I

    const/16 v2, 0x2710

    if-lt v0, v2, :cond_1

    iget p0, p0, Landroid/content/pm/ApplicationInfo;->flags:I

    and-int/lit16 p0, p0, 0x81

    if-nez p0, :cond_1

    const/4 v1, 0x1

    :cond_1
    return v1
.end method

.method static densityFor(Ljava/lang/String;)I
    .registers 2

    .line 40
    const-string v0, "120"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    const/16 p0, 0x78

    return p0

    .line 41
    :cond_0
    const-string v0, "140"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_1

    const/16 p0, 0x8c

    return p0

    .line 42
    :cond_1
    const-string v0, "180"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_2

    const/16 p0, 0xb4

    return p0

    .line 43
    :cond_2
    const-string v0, "200"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_3

    const/16 p0, 0xc8

    return p0

    .line 44
    :cond_3
    const/16 p0, 0xa0

    return p0
.end method

.method private static densityForActivity(Lcom/android/server/am/ActivityRecord;)I
    .registers 1

    .line 37
    iget-object p0, p0, Lcom/android/server/am/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object p0, p0, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    iget-object p0, p0, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->densityForPackage(Ljava/lang/String;)I

    move-result p0

    return p0
.end method

.method static densityForPackage(Ljava/lang/String;)I
    .registers 2

    .line 33
    sget-object v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->packageDensities:Ljava/util/Map;

    invoke-interface {v0, p0}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p0

    check-cast p0, Ljava/lang/Integer;

    .line 34
    if-nez p0, :cond_0

    sget p0, Lcom/android/server/am/VoyahIranLegacyDisplay;->appDensity:I

    goto :goto_0

    :cond_0
    invoke-virtual {p0}, Ljava/lang/Integer;->intValue()I

    move-result p0

    :goto_0
    return p0
.end method

.method private static desiredNight()I
    .registers 3

    .line 50
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

    .line 57
    sget-boolean v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->scheduled:Z

    if-nez v0, :cond_1

    iget-object v0, p0, Lcom/android/server/am/ActivityRecord;->service:Lcom/android/server/am/ActivityManagerService;

    if-nez v0, :cond_0

    goto :goto_0

    .line 58
    :cond_0
    const/4 v0, 0x1

    sput-boolean v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->scheduled:Z

    .line 59
    iget-object p0, p0, Lcom/android/server/am/ActivityRecord;->service:Lcom/android/server/am/ActivityManagerService;

    .line 60
    new-instance v0, Landroid/os/Handler;

    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v1

    invoke-direct {v0, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V

    .line 61
    new-instance v1, Lcom/android/server/am/VoyahIranLegacyDisplay$1;

    invoke-direct {v1, p0, v0}, Lcom/android/server/am/VoyahIranLegacyDisplay$1;-><init>(Lcom/android/server/am/ActivityManagerService;Landroid/os/Handler;)V

    invoke-virtual {v0, v1}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    .line 85
    return-void

    .line 57
    :cond_1
    :goto_0
    return-void
.end method

.method static nightFor(Ljava/lang/String;Ljava/lang/String;)I
    .registers 3

    .line 47
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

    .line 110
    const/4 v0, -0x2

    if-eq p1, v0, :cond_0

    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->applies(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p1, 0x0

    :cond_0
    return p1
.end method

.method static parseDensities(Ljava/lang/String;)Ljava/util/Map;
    .registers 9
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/String;",
            ")",
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/Integer;",
            ">;"
        }
    .end annotation

    .line 23
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    .line 24
    if-eqz p0, :cond_2

    const-string v1, ";"

    invoke-virtual {p0, v1}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object p0

    array-length v1, p0

    const/4 v2, 0x0

    move v3, v2

    :goto_0
    if-ge v3, v1, :cond_2

    aget-object v4, p0, v3

    .line 25
    const-string v5, "="

    invoke-virtual {v4, v5}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object v4

    .line 26
    array-length v5, v4

    const/4 v6, 0x2

    if-ne v5, v6, :cond_1

    aget-object v5, v4, v2

    invoke-virtual {v5}, Ljava/lang/String;->length()I

    move-result v5

    const/16 v6, 0xff

    if-gt v5, v6, :cond_1

    aget-object v5, v4, v2

    const-string v6, "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"

    invoke-virtual {v5, v6}, Ljava/lang/String;->matches(Ljava/lang/String;)Z

    move-result v5

    if-eqz v5, :cond_1

    const/4 v5, 0x1

    aget-object v6, v4, v5

    .line 27
    const-string v7, "120"

    invoke-virtual {v6, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v6

    if-nez v6, :cond_0

    aget-object v6, v4, v5

    const-string v7, "140"

    invoke-virtual {v6, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v6

    if-nez v6, :cond_0

    aget-object v6, v4, v5

    const-string v7, "160"

    invoke-virtual {v6, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v6

    if-nez v6, :cond_0

    aget-object v6, v4, v5

    const-string v7, "180"

    invoke-virtual {v6, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v6

    if-nez v6, :cond_0

    aget-object v6, v4, v5

    const-string v7, "200"

    invoke-virtual {v6, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v6

    if-eqz v6, :cond_1

    .line 28
    :cond_0
    aget-object v6, v4, v2

    aget-object v4, v4, v5

    invoke-static {v4}, Ljava/lang/Integer;->valueOf(Ljava/lang/String;)Ljava/lang/Integer;

    move-result-object v4

    invoke-interface {v0, v6, v4}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 24
    :cond_1
    add-int/lit8 v3, v3, 0x1

    goto :goto_0

    .line 30
    :cond_2
    invoke-static {v0}, Ljava/util/Collections;->unmodifiableMap(Ljava/util/Map;)Ljava/util/Map;

    move-result-object p0

    return-object p0
.end method

.method private static refresh(Lcom/android/server/am/ActivityManagerService;Landroid/content/ContentResolver;)V
    .registers 19

    .line 167
    move-object/from16 v1, p0

    move-object/from16 v0, p1

    :try_start_0
    const-string v2, "voyah_iran_display_policy"

    invoke-static {v0, v2}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    .line 168
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

    .line 169
    const-string v5, "voyah_iran_app_theme"

    invoke-static {v0, v5}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v5

    .line 170
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

    .line 171
    invoke-static {}, Lcom/android/server/am/VoyahIranLegacyDisplay;->desiredNight()I

    move-result v5

    sput v5, Lcom/android/server/am/VoyahIranLegacyDisplay;->nightMode:I

    .line 172
    const-string v5, "voyah_iran_app_density"

    invoke-static {v0, v5}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v5

    invoke-static {v5}, Lcom/android/server/am/VoyahIranLegacyDisplay;->densityFor(Ljava/lang/String;)I

    move-result v5

    sput v5, Lcom/android/server/am/VoyahIranLegacyDisplay;->appDensity:I

    .line 173
    const-string v5, "voyah_iran_app_density_packages"

    invoke-static {v0, v5}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v5

    .line 174
    invoke-static {v5}, Lcom/android/server/am/VoyahIranLegacyDisplay;->parseDensities(Ljava/lang/String;)Ljava/util/Map;

    move-result-object v6

    sput-object v6, Lcom/android/server/am/VoyahIranLegacyDisplay;->packageDensities:Ljava/util/Map;

    .line 175
    monitor-enter p0
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 176
    :try_start_1
    iget-object v6, v1, Lcom/android/server/am/ActivityManagerService;->mStackSupervisor:Lcom/android/server/am/ActivityStackSupervisor;

    .line 177
    invoke-virtual {v6}, Lcom/android/server/am/ActivityStackSupervisor;->getChildCount()I

    move-result v7

    sub-int/2addr v7, v4

    :goto_3
    if-ltz v7, :cond_9

    .line 178
    invoke-virtual {v6, v7}, Lcom/android/server/am/ActivityStackSupervisor;->getChildAt(I)Lcom/android/server/am/ActivityDisplay;

    move-result-object v8

    .line 179
    invoke-virtual {v8}, Lcom/android/server/am/ActivityDisplay;->getChildCount()I

    move-result v9

    sub-int/2addr v9, v4

    :goto_4
    if-ltz v9, :cond_8

    .line 180
    invoke-virtual {v8, v9}, Lcom/android/server/am/ActivityDisplay;->getChildAt(I)Lcom/android/server/am/ActivityStack;

    move-result-object v10

    .line 181
    invoke-virtual {v10}, Lcom/android/server/am/ActivityStack;->getChildCount()I

    move-result v11

    sub-int/2addr v11, v4

    :goto_5
    if-ltz v11, :cond_7

    .line 182
    invoke-virtual {v10, v11}, Lcom/android/server/am/ActivityStack;->getChildAt(I)Lcom/android/server/wm/ConfigurationContainer;

    move-result-object v12

    check-cast v12, Lcom/android/server/am/TaskRecord;

    .line 183
    invoke-virtual {v12}, Lcom/android/server/am/TaskRecord;->getChildCount()I

    move-result v13

    sub-int/2addr v13, v4

    :goto_6
    if-ltz v13, :cond_6

    .line 184
    invoke-virtual {v12, v13}, Lcom/android/server/am/TaskRecord;->getChildAt(I)Lcom/android/server/am/ActivityRecord;

    move-result-object v14

    .line 185
    invoke-static {v14}, Lcom/android/server/am/VoyahIranLegacyDisplay;->candidate(Lcom/android/server/am/ActivityRecord;)Z

    move-result v15

    if-eqz v15, :cond_5

    .line 186
    invoke-static {v14}, Lcom/android/server/am/VoyahIranLegacyDisplay;->updateTheme(Lcom/android/server/am/ActivityRecord;)V

    .line 187
    invoke-virtual {v14}, Lcom/android/server/am/ActivityRecord;->getBounds()Landroid/graphics/Rect;

    move-result-object v15

    .line 188
    sget-boolean v16, Lcom/android/server/am/VoyahIranLegacyDisplay;->enabled:Z

    if-nez v16, :cond_4

    iget v3, v15, Landroid/graphics/Rect;->left:I

    const/16 v4, 0x91

    if-ne v3, v4, :cond_4

    iget v3, v15, Landroid/graphics/Rect;->top:I

    const/16 v4, 0x32

    if-ne v3, v4, :cond_4

    new-instance v3, Landroid/graphics/Rect;

    invoke-direct {v3}, Landroid/graphics/Rect;-><init>()V

    invoke-virtual {v14, v3}, Lcom/android/server/am/ActivityRecord;->setBounds(Landroid/graphics/Rect;)I

    .line 189
    :cond_4
    const/4 v3, 0x0

    const/4 v4, 0x1

    invoke-virtual {v14, v3, v4}, Lcom/android/server/am/ActivityRecord;->ensureActivityConfiguration(IZ)Z

    goto :goto_7

    .line 185
    :cond_5
    const/4 v3, 0x0

    .line 183
    :goto_7
    add-int/lit8 v13, v13, -0x1

    goto :goto_6

    .line 181
    :cond_6
    const/4 v3, 0x0

    add-int/lit8 v11, v11, -0x1

    goto :goto_5

    .line 179
    :cond_7
    const/4 v3, 0x0

    add-int/lit8 v9, v9, -0x1

    goto :goto_4

    .line 177
    :cond_8
    const/4 v3, 0x0

    add-int/lit8 v7, v7, -0x1

    goto :goto_3

    .line 195
    :cond_9
    iget-object v3, v1, Lcom/android/server/am/ActivityManagerService;->mWindowManager:Lcom/android/server/wm/WindowManagerService;

    invoke-static {v3}, Lcom/android/server/wm/VoyahIranLegacyFrames;->traverse(Lcom/android/server/wm/WindowManagerService;)V

    .line 196
    monitor-exit p0
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 197
    :try_start_2
    const-string v1, "voyah_iran_display_policy_ack"

    if-nez v2, :cond_a

    const-string v2, "1:default"

    :cond_a
    invoke-static {v0, v1, v2}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 198
    const-string v1, "voyah_iran_app_theme_ack"

    sget-object v2, Lcom/android/server/am/VoyahIranLegacyDisplay;->themeMode:Ljava/lang/String;

    invoke-static {v0, v1, v2}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 199
    const-string v1, "voyah_iran_app_density_ack"

    sget v2, Lcom/android/server/am/VoyahIranLegacyDisplay;->appDensity:I

    invoke-static {v2}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;

    move-result-object v2

    invoke-static {v0, v1, v2}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 200
    const-string v1, "voyah_iran_app_density_packages_ack"

    if-nez v5, :cond_b

    const-string v5, "-"

    :cond_b
    invoke-static {v0, v1, v5}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 201
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

    .line 204
    goto :goto_8

    .line 196
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

    .line 202
    :catch_0
    move-exception v0

    .line 203
    const-string v1, "VI/DisplayPolicy9"

    const-string v2, "Reconfiguration failed; no acknowledgement"

    invoke-static {v1, v2, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 205
    :goto_8
    return-void
.end method

.method private static updateTheme(Lcom/android/server/am/ActivityRecord;)V
    .registers 4

    .line 156
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->candidate(Lcom/android/server/am/ActivityRecord;)Z

    move-result v0

    if-eqz v0, :cond_4

    sget v0, Lcom/android/server/am/VoyahIranLegacyDisplay;->nightMode:I

    if-nez v0, :cond_0

    goto :goto_0

    .line 157
    :cond_0
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object v0

    .line 158
    iget v1, v0, Landroid/content/res/Configuration;->uiMode:I

    and-int/lit8 v1, v1, 0x30

    sget v2, Lcom/android/server/am/VoyahIranLegacyDisplay;->nightMode:I

    if-ne v1, v2, :cond_2

    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->densityCandidate(Lcom/android/server/am/ActivityRecord;)Z

    move-result v1

    if-eqz v1, :cond_1

    iget v1, v0, Landroid/content/res/Configuration;->densityDpi:I

    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->densityForActivity(Lcom/android/server/am/ActivityRecord;)I

    move-result v2

    if-ne v1, v2, :cond_2

    :cond_1
    return-void

    .line 159
    :cond_2
    new-instance v1, Landroid/content/res/Configuration;

    invoke-direct {v1, v0}, Landroid/content/res/Configuration;-><init>(Landroid/content/res/Configuration;)V

    .line 160
    iget v0, v1, Landroid/content/res/Configuration;->uiMode:I

    and-int/lit8 v0, v0, -0x31

    sget v2, Lcom/android/server/am/VoyahIranLegacyDisplay;->nightMode:I

    or-int/2addr v0, v2

    iput v0, v1, Landroid/content/res/Configuration;->uiMode:I

    .line 161
    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->densityCandidate(Lcom/android/server/am/ActivityRecord;)Z

    move-result v0

    if-eqz v0, :cond_3

    invoke-static {p0, v1}, Lcom/android/server/am/VoyahIranLegacyDisplay;->applyDensity(Lcom/android/server/am/ActivityRecord;Landroid/content/res/Configuration;)V

    .line 162
    :cond_3
    invoke-virtual {p0, v1}, Lcom/android/server/am/ActivityRecord;->onOverrideConfigurationChanged(Landroid/content/res/Configuration;)V

    .line 163
    return-void

    .line 156
    :cond_4
    :goto_0
    return-void
.end method
