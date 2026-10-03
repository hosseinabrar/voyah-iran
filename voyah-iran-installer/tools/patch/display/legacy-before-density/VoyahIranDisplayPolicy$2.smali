.class Lcom/android/server/wm/VoyahIranDisplayPolicy$2;
.super Ljava/lang/Object;
.source "VoyahIranDisplayPolicy.java"

# interfaces
.implements Ljava/util/function/Consumer;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/android/server/wm/VoyahIranDisplayPolicy;->refresh(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ljava/lang/Object;",
        "Ljava/util/function/Consumer<",
        "Lcom/android/server/wm/ActivityRecord;",
        ">;"
    }
.end annotation


# direct methods
.method constructor <init>()V
    .locals 0

    .line 72
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public accept(Lcom/android/server/wm/ActivityRecord;)V
    .locals 2

    .line 75
    invoke-static {p1}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$600(Lcom/android/server/wm/ActivityRecord;)Z

    move-result v0

    if-eqz v0, :cond_0

    invoke-virtual {p1}, Lcom/android/server/wm/ActivityRecord;->getParent()Lcom/android/server/wm/WindowContainer;

    move-result-object v0

    if-eqz v0, :cond_0

    .line 76
    invoke-virtual {p1}, Lcom/android/server/wm/ActivityRecord;->getParent()Lcom/android/server/wm/WindowContainer;

    move-result-object v0

    invoke-virtual {v0}, Lcom/android/server/wm/WindowContainer;->getConfiguration()Landroid/content/res/Configuration;

    move-result-object v0

    invoke-virtual {p1, v0}, Lcom/android/server/wm/ActivityRecord;->onConfigurationChanged(Landroid/content/res/Configuration;)V

    .line 77
    const/4 v0, 0x0

    const/4 v1, 0x1

    invoke-virtual {p1, v0, v1}, Lcom/android/server/wm/ActivityRecord;->ensureActivityConfiguration(IZ)Z

    .line 79
    :cond_0
    return-void
.end method

.method public bridge synthetic accept(Ljava/lang/Object;)V
    .locals 0
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x1000
        }
        names = {
            null
        }
    .end annotation

    .line 72
    check-cast p1, Lcom/android/server/wm/ActivityRecord;

    invoke-virtual {p0, p1}, Lcom/android/server/wm/VoyahIranDisplayPolicy$2;->accept(Lcom/android/server/wm/ActivityRecord;)V

    return-void
.end method
