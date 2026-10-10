.class public final Lcom/android/server/wm/VoyahIranDisplayPolicy;
.super Ljava/lang/Object;
.source "VoyahIranDisplayPolicy.java"


# static fields
.field private static final ACK:Ljava/lang/String; = "voyah_iran_display_policy_ack"

.field private static final DENSITY:Ljava/lang/String; = "voyah_iran_app_density"

.field private static final KEY:Ljava/lang/String; = "voyah_iran_display_policy"

.field private static final LEFT:I = 0x91

.field private static final PACKAGE_DENSITY:Ljava/lang/String; = "voyah_iran_app_density_packages"

.field private static final TAG:Ljava/lang/String; = "VI/DisplayPolicy"

.field private static final THEME:Ljava/lang/String; = "voyah_iran_app_theme"

.field private static final TOP:I = 0x32

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
    .locals 1

    .line 18
    const-string v0, "vehicle"

    sput-object v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->themeMode:Ljava/lang/String;

    .line 21
    const/16 v0, 0xa0

    sput v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->appDensity:I

    .line 23
    invoke-static {}, Ljava/util/Collections;->emptyMap()Ljava/util/Map;

    move-result-object v0

    sput-object v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->packageDensities:Ljava/util/Map;

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

    .line 242
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;

    move-result-object p0

    .line 243
    if-eqz p0, :cond_0

    iget-object p1, p1, Lcom/android/server/wm/WindowFrames;->mFrame:Landroid/graphics/Rect;

    invoke-static {p1, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 244
    :cond_0
    return-void
.end method

.method public static applies(Lcom/android/server/wm/ActivityRecord;)Z
    .locals 1

    .line 136
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->initialize(Lcom/android/server/wm/ActivityRecord;)V

    .line 137
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

    .line 205
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->suppressLoginWelcome(Lcom/android/server/wm/WindowState;)V

    .line 206
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;

    move-result-object p0

    .line 207
    if-nez p0, :cond_0

    return-void

    .line 208
    :cond_0
    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 209
    invoke-static {p3, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 210
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mParentFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 211
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mDisplayFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 212
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mContainingFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 213
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mContentFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 214
    iget-object p2, p1, Lcom/android/server/wm/WindowFrames;->mVisibleFrame:Landroid/graphics/Rect;

    invoke-static {p2, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 215
    iget-object p1, p1, Lcom/android/server/wm/WindowFrames;->mStableFrame:Landroid/graphics/Rect;

    invoke-static {p1, p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 216
    return-void
.end method

.method private static candidate(Lcom/android/server/wm/ActivityRecord;)Z
    .locals 4

    .line 127
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

    .line 128
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->inMultiWindowMode()Z

    move-result v1

    if-nez v1, :cond_4

    iget-object v1, p0, Lcom/android/server/wm/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    if-eqz v1, :cond_4

    iget-object v1, p0, Lcom/android/server/wm/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object v1, v1, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    if-nez v1, :cond_1

    goto :goto_0

    .line 129
    :cond_1
    iget-object p0, p0, Lcom/android/server/wm/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object p0, p0, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    .line 130
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

    .line 128
    :cond_4
    :goto_0
    return v0
.end method

.method private static clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V
    .locals 1

    .line 198
    if-eqz p0, :cond_0

    invoke-virtual {p0}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v0

    if-nez v0, :cond_0

    .line 200
    invoke-virtual {p0, p1}, Landroid/graphics/Rect;->intersect(Landroid/graphics/Rect;)Z

    move-result v0

    if-nez v0, :cond_0

    invoke-virtual {p0, p1}, Landroid/graphics/Rect;->set(Landroid/graphics/Rect;)V

    .line 202
    :cond_0
    return-void
.end method

.method public static configuration(Lcom/android/server/wm/ActivityRecord;Landroid/content/res/Configuration;)V
    .locals 7

    .line 153
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->initialize(Lcom/android/server/wm/ActivityRecord;)V

    .line 154
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->candidate(Lcom/android/server/wm/ActivityRecord;)Z

    move-result v0

    if-eqz v0, :cond_0

    sget v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->nightMode:I

    if-eqz v0, :cond_0

    .line 155
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getResolvedOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object v0

    .line 156
    iget v1, v0, Landroid/content/res/Configuration;->uiMode:I

    and-int/lit8 v1, v1, -0x31

    sget v2, Lcom/android/server/wm/VoyahIranDisplayPolicy;->nightMode:I

    or-int/2addr v1, v2

    iput v1, v0, Landroid/content/res/Configuration;->uiMode:I

    .line 158
    :cond_0
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->densityCandidate(Lcom/android/server/wm/ActivityRecord;)Z

    move-result v0

    const/16 v1, 0xa0

    if-eqz v0, :cond_4

    .line 159
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getResolvedOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object v0

    .line 160
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->densityForActivity(Lcom/android/server/wm/ActivityRecord;)I

    move-result v2

    .line 161
    iput v2, v0, Landroid/content/res/Configuration;->densityDpi:I

    .line 162
    iget-object v3, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    if-nez v3, :cond_1

    const/4 v3, 0x0

    goto :goto_0

    :cond_1
    iget-object v3, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v3}, Landroid/app/WindowConfiguration;->getAppBounds()Landroid/graphics/Rect;

    move-result-object v3

    .line 163
    :goto_0
    if-eqz v3, :cond_2

    invoke-virtual {v3}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v4

    if-eqz v4, :cond_3

    :cond_2
    iget-object v4, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    if-eqz v4, :cond_3

    iget-object v3, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v3}, Landroid/app/WindowConfiguration;->getBounds()Landroid/graphics/Rect;

    move-result-object v3

    .line 164
    :cond_3
    if-eqz v3, :cond_4

    invoke-virtual {v3}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v4

    if-nez v4, :cond_4

    .line 165
    invoke-virtual {v3}, Landroid/graphics/Rect;->width()I

    move-result v4

    mul-int/2addr v4, v1

    div-int/2addr v4, v2

    iput v4, v0, Landroid/content/res/Configuration;->screenWidthDp:I

    .line 166
    invoke-virtual {v3}, Landroid/graphics/Rect;->height()I

    move-result v3

    mul-int/2addr v3, v1

    div-int/2addr v3, v2

    iput v3, v0, Landroid/content/res/Configuration;->screenHeightDp:I

    .line 167
    iget v2, v0, Landroid/content/res/Configuration;->screenWidthDp:I

    iget v3, v0, Landroid/content/res/Configuration;->screenHeightDp:I

    invoke-static {v2, v3}, Ljava/lang/Math;->min(II)I

    move-result v2

    iput v2, v0, Landroid/content/res/Configuration;->smallestScreenWidthDp:I

    .line 170
    :cond_4
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->applies(Lcom/android/server/wm/ActivityRecord;)Z

    move-result v0

    if-nez v0, :cond_5

    return-void

    .line 171
    :cond_5
    iget-object v0, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v0}, Landroid/app/WindowConfiguration;->getBounds()Landroid/graphics/Rect;

    move-result-object v0

    .line 172
    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v2

    const/16 v3, 0x1d1

    if-le v2, v3, :cond_a

    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v2

    const/16 v3, 0xfa

    if-le v2, v3, :cond_a

    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v2

    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v3

    if-gt v2, v3, :cond_6

    goto/16 :goto_2

    .line 173
    :cond_6
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getResolvedOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object v2

    .line 174
    new-instance v3, Landroid/graphics/Rect;

    iget v4, v0, Landroid/graphics/Rect;->left:I

    add-int/lit16 v4, v4, 0x91

    iget v5, v0, Landroid/graphics/Rect;->top:I

    add-int/lit8 v5, v5, 0x32

    iget v6, v0, Landroid/graphics/Rect;->right:I

    iget v0, v0, Landroid/graphics/Rect;->bottom:I

    invoke-direct {v3, v4, v5, v6, v0}, Landroid/graphics/Rect;-><init>(IIII)V

    .line 175
    new-instance v0, Landroid/graphics/Rect;

    invoke-direct {v0, v3}, Landroid/graphics/Rect;-><init>(Landroid/graphics/Rect;)V

    .line 176
    iget-object v4, p1, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v4}, Landroid/app/WindowConfiguration;->getAppBounds()Landroid/graphics/Rect;

    move-result-object v4

    .line 177
    if-eqz v4, :cond_7

    invoke-virtual {v4}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v5

    if-nez v5, :cond_7

    invoke-virtual {v0, v4}, Landroid/graphics/Rect;->intersect(Landroid/graphics/Rect;)Z

    .line 178
    :cond_7
    iget-object v4, v2, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v4, v3}, Landroid/app/WindowConfiguration;->setBounds(Landroid/graphics/Rect;)V

    .line 179
    iget-object v4, v2, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {v4, v0}, Landroid/app/WindowConfiguration;->setAppBounds(Landroid/graphics/Rect;)V

    .line 180
    const/4 v4, 0x2

    iput v4, v2, Landroid/content/res/Configuration;->orientation:I

    .line 181
    iget v4, v2, Landroid/content/res/Configuration;->densityDpi:I

    if-lez v4, :cond_8

    iget p1, v2, Landroid/content/res/Configuration;->densityDpi:I

    goto :goto_1

    :cond_8
    iget p1, p1, Landroid/content/res/Configuration;->densityDpi:I

    .line 182
    :goto_1
    if-gtz p1, :cond_9

    move p1, v1

    .line 183
    :cond_9
    invoke-virtual {v0}, Landroid/graphics/Rect;->width()I

    move-result v4

    mul-int/2addr v4, v1

    div-int/2addr v4, p1

    iput v4, v2, Landroid/content/res/Configuration;->screenWidthDp:I

    .line 184
    invoke-virtual {v0}, Landroid/graphics/Rect;->height()I

    move-result v4

    mul-int/2addr v4, v1

    div-int/2addr v4, p1

    iput v4, v2, Landroid/content/res/Configuration;->screenHeightDp:I

    .line 185
    iget p1, v2, Landroid/content/res/Configuration;->screenWidthDp:I

    iget v1, v2, Landroid/content/res/Configuration;->screenHeightDp:I

    invoke-static {p1, v1}, Ljava/lang/Math;->min(II)I

    move-result p1

    iput p1, v2, Landroid/content/res/Configuration;->smallestScreenWidthDp:I

    .line 186
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

    invoke-virtual {p0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

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

    .line 187
    return-void

    .line 172
    :cond_a
    :goto_2
    return-void
.end method

.method private static densityCandidate(Lcom/android/server/wm/ActivityRecord;)Z
    .locals 3

    .line 141
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->candidate(Lcom/android/server/wm/ActivityRecord;)Z

    move-result v0

    const/4 v1, 0x0

    if-nez v0, :cond_0

    return v1

    .line 142
    :cond_0
    iget-object p0, p0, Lcom/android/server/wm/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object p0, p0, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    .line 143
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
    .locals 1

    .line 42
    const-string v0, "120"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    const/16 p0, 0x78

    return p0

    .line 43
    :cond_0
    const-string v0, "140"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_1

    const/16 p0, 0x8c

    return p0

    .line 44
    :cond_1
    const-string v0, "180"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_2

    const/16 p0, 0xb4

    return p0

    .line 45
    :cond_2
    const-string v0, "200"

    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_3

    const/16 p0, 0xc8

    return p0

    .line 46
    :cond_3
    const/16 p0, 0xa0

    return p0
.end method

.method private static densityForActivity(Lcom/android/server/wm/ActivityRecord;)I
    .locals 0

    .line 39
    iget-object p0, p0, Lcom/android/server/wm/ActivityRecord;->info:Landroid/content/pm/ActivityInfo;

    iget-object p0, p0, Landroid/content/pm/ActivityInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    iget-object p0, p0, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->densityForPackage(Ljava/lang/String;)I

    move-result p0

    return p0
.end method

.method static densityForPackage(Ljava/lang/String;)I
    .locals 1

    .line 35
    sget-object v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->packageDensities:Ljava/util/Map;

    invoke-interface {v0, p0}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p0

    check-cast p0, Ljava/lang/Integer;

    .line 36
    if-nez p0, :cond_0

    sget p0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->appDensity:I

    goto :goto_0

    :cond_0
    invoke-virtual {p0}, Ljava/lang/Integer;->intValue()I

    move-result p0

    :goto_0
    return p0
.end method

.method private static desiredNight()I
    .locals 3

    .line 54
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

    .line 63
    sget-boolean v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->scheduled:Z

    if-nez v0, :cond_1

    if-eqz p0, :cond_1

    iget-object v0, p0, Lcom/android/server/wm/ActivityRecord;->mAtmService:Lcom/android/server/wm/ActivityTaskManagerService;

    if-nez v0, :cond_0

    goto :goto_0

    .line 64
    :cond_0
    const/4 v0, 0x1

    sput-boolean v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->scheduled:Z

    .line 65
    iget-object p0, p0, Lcom/android/server/wm/ActivityRecord;->mAtmService:Lcom/android/server/wm/ActivityTaskManagerService;

    .line 66
    new-instance v0, Landroid/os/Handler;

    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v1

    invoke-direct {v0, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V

    .line 67
    new-instance v1, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;

    invoke-direct {v1, p0, v0}, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;-><init>(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/os/Handler;)V

    invoke-virtual {v0, v1}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    .line 92
    return-void

    .line 63
    :cond_1
    :goto_0
    return-void
.end method

.method static nightFor(Ljava/lang/String;Ljava/lang/String;)I
    .locals 1

    .line 51
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

    .line 149
    const/4 v0, -0x2

    if-eq p1, v0, :cond_0

    invoke-static {p0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->applies(Lcom/android/server/wm/ActivityRecord;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p1, 0x0

    :cond_0
    return p1
.end method

.method static parseDensities(Ljava/lang/String;)Ljava/util/Map;
    .locals 8
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

    .line 25
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    .line 26
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

    .line 27
    const-string v5, "="

    invoke-virtual {v4, v5}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object v4

    .line 28
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

    .line 29
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

    .line 30
    :cond_0
    aget-object v6, v4, v2

    aget-object v4, v4, v5

    invoke-static {v4}, Ljava/lang/Integer;->valueOf(Ljava/lang/String;)Ljava/lang/Integer;

    move-result-object v4

    invoke-interface {v0, v6, v4}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 26
    :cond_1
    add-int/lit8 v3, v3, 0x1

    goto :goto_0

    .line 32
    :cond_2
    invoke-static {v0}, Ljava/util/Collections;->unmodifiableMap(Ljava/util/Map;)Ljava/util/Map;

    move-result-object p0

    return-object p0
.end method

.method private static refresh(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V
    .locals 5

    .line 96
    :try_start_0
    const-string v0, "voyah_iran_display_policy"

    invoke-static {p1, v0}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    .line 97
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

    .line 98
    const-string v1, "voyah_iran_app_theme"

    invoke-static {p1, v1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 99
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

    .line 100
    invoke-static {}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->desiredNight()I

    move-result v1

    sput v1, Lcom/android/server/wm/VoyahIranDisplayPolicy;->nightMode:I

    .line 101
    const-string v1, "voyah_iran_app_density"

    invoke-static {p1, v1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-static {v1}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->densityFor(Ljava/lang/String;)I

    move-result v1

    sput v1, Lcom/android/server/wm/VoyahIranDisplayPolicy;->appDensity:I

    .line 102
    const-string v1, "voyah_iran_app_density_packages"

    invoke-static {p1, v1}, Landroid/provider/Settings$Global;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 103
    invoke-static {v1}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->parseDensities(Ljava/lang/String;)Ljava/util/Map;

    move-result-object v2

    sput-object v2, Lcom/android/server/wm/VoyahIranDisplayPolicy;->packageDensities:Ljava/util/Map;

    .line 104
    iget-object v2, p0, Lcom/android/server/wm/ActivityTaskManagerService;->mGlobalLock:Lcom/android/server/wm/WindowManagerGlobalLock;

    monitor-enter v2
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 105
    :try_start_1
    iget-object v3, p0, Lcom/android/server/wm/ActivityTaskManagerService;->mRootWindowContainer:Lcom/android/server/wm/RootWindowContainer;

    new-instance v4, Lcom/android/server/wm/VoyahIranDisplayPolicy$2;

    invoke-direct {v4}, Lcom/android/server/wm/VoyahIranDisplayPolicy$2;-><init>()V

    invoke-virtual {v3, v4}, Lcom/android/server/wm/RootWindowContainer;->forAllActivities(Ljava/util/function/Consumer;)V

    .line 114
    iget-object p0, p0, Lcom/android/server/wm/ActivityTaskManagerService;->mWindowManager:Lcom/android/server/wm/WindowManagerService;

    invoke-virtual {p0}, Lcom/android/server/wm/WindowManagerService;->requestTraversal()V

    .line 115
    monitor-exit v2
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 116
    :try_start_2
    const-string p0, "voyah_iran_display_policy_ack"

    if-nez v0, :cond_4

    const-string v0, "1:default"

    :cond_4
    invoke-static {p1, p0, v0}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 117
    const-string p0, "voyah_iran_app_theme_ack"

    sget-object v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->themeMode:Ljava/lang/String;

    invoke-static {p1, p0, v0}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 118
    const-string p0, "voyah_iran_app_density_ack"

    sget v0, Lcom/android/server/wm/VoyahIranDisplayPolicy;->appDensity:I

    invoke-static {v0}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;

    move-result-object v0

    invoke-static {p1, p0, v0}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 119
    const-string p0, "voyah_iran_app_density_packages_ack"

    if-nez v1, :cond_5

    const-string v1, "-"

    :cond_5
    invoke-static {p1, p0, v1}, Landroid/provider/Settings$Global;->putString(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z

    .line 120
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

    .line 123
    goto :goto_3

    .line 115
    :catchall_0
    move-exception p0

    :try_start_3
    monitor-exit v2
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_0

    :try_start_4
    throw p0
    :try_end_4
    .catch Ljava/lang/RuntimeException; {:try_start_4 .. :try_end_4} :catch_0

    .line 121
    :catch_0
    move-exception p0

    .line 122
    const-string p1, "VI/DisplayPolicy"

    const-string v0, "Reconfiguration failed; request not acknowledged"

    invoke-static {p1, v0, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 124
    :goto_3
    return-void
.end method

.method private static safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;
    .locals 3

    .line 191
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

    .line 192
    :cond_0
    iget-object p0, p0, Lcom/android/server/wm/WindowState;->mActivityRecord:Lcom/android/server/wm/ActivityRecord;

    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getResolvedOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object p0

    iget-object p0, p0, Landroid/content/res/Configuration;->windowConfiguration:Landroid/app/WindowConfiguration;

    invoke-virtual {p0}, Landroid/app/WindowConfiguration;->getBounds()Landroid/graphics/Rect;

    move-result-object p0

    .line 193
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

    .line 194
    :cond_1
    return-object p0

    .line 193
    :cond_2
    :goto_0
    return-object v2

    .line 191
    :cond_3
    :goto_1
    return-object v2
.end method

.method static supportsLoginWelcomeVariant(Ljava/lang/String;)Z
    .locals 1

    .line 219
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

    .line 223
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

    .line 224
    invoke-virtual {v1}, Landroid/view/WindowManager$LayoutParams;->getTitle()Ljava/lang/CharSequence;

    move-result-object v1

    const-string v3, "LoginingView"

    invoke-virtual {v3, v1}, Ljava/lang/String;->contentEquals(Ljava/lang/CharSequence;)Z

    move-result v1

    if-nez v1, :cond_0

    goto :goto_1

    .line 225
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

    .line 227
    invoke-virtual {v1, v3}, Ljava/lang/String;->toLowerCase(Ljava/util/Locale;)Ljava/lang/String;

    move-result-object v1

    .line 228
    invoke-static {v1}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->supportsLoginWelcomeVariant(Ljava/lang/String;)Z

    move-result v1

    if-nez v1, :cond_1

    return-void

    .line 230
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

    .line 232
    const/4 v2, 0x2

    if-eq v1, v2, :cond_2

    return-void

    .line 235
    :cond_2
    const/4 v1, 0x0

    invoke-virtual {p0, v1}, Lcom/android/server/wm/WindowState;->hideLw(Z)Z

    move-result p0

    if-eqz p0, :cond_3

    const-string p0, "Suppressed login welcome window"

    invoke-static {v0, p0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_0
    .catch Ljava/lang/RuntimeException; {:try_start_0 .. :try_end_0} :catch_0

    .line 238
    :cond_3
    goto :goto_0

    .line 236
    :catch_0
    move-exception p0

    .line 237
    const-string v1, "Cannot read login welcome policy"

    invoke-static {v0, v1, p0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 239
    :goto_0
    return-void

    .line 224
    :cond_4
    :goto_1
    return-void
.end method
