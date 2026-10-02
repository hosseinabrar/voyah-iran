.class public final Lcom/android/server/wm/VoyahIranLegacyFrames;
.super Ljava/lang/Object;
.source "VoyahIranLegacyFrames.java"


# direct methods
.method public constructor <init>()V
    .registers 1

    .line 7
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method private static activity(Lcom/android/server/wm/AppWindowToken;)Ljava/lang/Object;
    .registers 2

    .line 10
    const/4 v0, 0x0

    if-nez p0, :cond_0

    return-object v0

    .line 11
    :cond_0
    invoke-virtual {p0}, Lcom/android/server/wm/AppWindowToken;->getController()Lcom/android/server/wm/WindowContainerController;

    move-result-object p0

    .line 12
    if-nez p0, :cond_1

    goto :goto_0

    :cond_1
    iget-object v0, p0, Lcom/android/server/wm/WindowContainerController;->mListener:Lcom/android/server/wm/WindowContainerListener;

    :goto_0
    return-object v0
.end method

.method public static after(Lcom/android/server/wm/WindowState;)V
    .registers 2

    .line 36
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;

    move-result-object v0

    .line 37
    if-eqz v0, :cond_0

    iget-object p0, p0, Lcom/android/server/wm/WindowState;->mFrame:Landroid/graphics/Rect;

    invoke-static {p0, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 38
    :cond_0
    return-void
.end method

.method public static applies(Lcom/android/server/wm/AppWindowToken;)Z
    .registers 1

    .line 15
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->activity(Lcom/android/server/wm/AppWindowToken;)Ljava/lang/Object;

    move-result-object p0

    invoke-static {p0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->applies(Ljava/lang/Object;)Z

    move-result p0

    return p0
.end method

.method public static before(Lcom/android/server/wm/WindowState;Landroid/graphics/Rect;Landroid/graphics/Rect;)V
    .registers 4

    .line 29
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;

    move-result-object v0

    .line 30
    if-nez v0, :cond_0

    return-void

    .line 31
    :cond_0
    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    invoke-static {p2, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 32
    iget-object p1, p0, Lcom/android/server/wm/WindowState;->mParentFrame:Landroid/graphics/Rect;

    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    iget-object p1, p0, Lcom/android/server/wm/WindowState;->mDisplayFrame:Landroid/graphics/Rect;

    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    iget-object p1, p0, Lcom/android/server/wm/WindowState;->mContainingFrame:Landroid/graphics/Rect;

    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 33
    invoke-virtual {p0}, Lcom/android/server/wm/WindowState;->getContentFrameLw()Landroid/graphics/Rect;

    move-result-object p1

    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    iget-object p1, p0, Lcom/android/server/wm/WindowState;->mVisibleFrame:Landroid/graphics/Rect;

    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    invoke-virtual {p0}, Lcom/android/server/wm/WindowState;->getStableFrameLw()Landroid/graphics/Rect;

    move-result-object p0

    invoke-static {p0, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 34
    return-void
.end method

.method private static clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V
    .registers 3

    .line 26
    if-eqz p0, :cond_0

    invoke-virtual {p0}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v0

    if-nez v0, :cond_0

    invoke-virtual {p0, p1}, Landroid/graphics/Rect;->intersect(Landroid/graphics/Rect;)Z

    move-result v0

    if-nez v0, :cond_0

    invoke-virtual {p0, p1}, Landroid/graphics/Rect;->set(Landroid/graphics/Rect;)V

    .line 27
    :cond_0
    return-void
.end method

.method public static orientation(Lcom/android/server/wm/AppWindowToken;I)I
    .registers 2

    .line 18
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->activity(Lcom/android/server/wm/AppWindowToken;)Ljava/lang/Object;

    move-result-object p0

    invoke-static {p0, p1}, Lcom/android/server/am/VoyahIranLegacyDisplay;->orientation(Ljava/lang/Object;I)I

    move-result p0

    return p0
.end method

.method private static safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;
    .registers 4

    .line 21
    iget-object v0, p0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v0, v0, Landroid/view/WindowManager$LayoutParams;->type:I

    const/4 v1, 0x1

    const/4 v2, 0x0

    if-lt v0, v1, :cond_2

    iget-object v0, p0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v0, v0, Landroid/view/WindowManager$LayoutParams;->type:I

    const/16 v1, 0x7d0

    if-ge v0, v1, :cond_2

    iget-object v0, p0, Lcom/android/server/wm/WindowState;->mAppToken:Lcom/android/server/wm/AppWindowToken;

    invoke-static {v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->applies(Lcom/android/server/wm/AppWindowToken;)Z

    move-result v0

    if-nez v0, :cond_0

    goto :goto_0

    .line 22
    :cond_0
    iget-object p0, p0, Lcom/android/server/wm/WindowState;->mAppToken:Lcom/android/server/wm/AppWindowToken;

    invoke-virtual {p0}, Lcom/android/server/wm/AppWindowToken;->getBounds()Landroid/graphics/Rect;

    move-result-object p0

    .line 23
    iget v0, p0, Landroid/graphics/Rect;->left:I

    const/16 v1, 0x91

    if-lt v0, v1, :cond_1

    iget v0, p0, Landroid/graphics/Rect;->top:I

    const/16 v1, 0x32

    if-lt v0, v1, :cond_1

    invoke-virtual {p0}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v0

    if-nez v0, :cond_1

    move-object v2, p0

    :cond_1
    return-object v2

    .line 21
    :cond_2
    :goto_0
    return-object v2
.end method

.method public static traverse(Lcom/android/server/wm/WindowManagerService;)V
    .registers 1

    .line 8
    invoke-virtual {p0}, Lcom/android/server/wm/WindowManagerService;->requestTraversal()V

    return-void
.end method
