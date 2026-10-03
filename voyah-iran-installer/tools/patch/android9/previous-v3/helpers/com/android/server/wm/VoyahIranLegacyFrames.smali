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

    .line 50
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;

    move-result-object v0

    .line 51
    if-eqz v0, :cond_0

    iget-object p0, p0, Lcom/android/server/wm/WindowState;->mFrame:Landroid/graphics/Rect;

    invoke-static {p0, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 52
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
    .registers 6

    .line 29
    invoke-static {p0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->safe(Lcom/android/server/wm/WindowState;)Landroid/graphics/Rect;

    move-result-object v0

    .line 30
    if-nez v0, :cond_0

    return-void

    .line 31
    :cond_0
    iget-object v1, p0, Lcom/android/server/wm/WindowState;->mService:Lcom/android/server/wm/WindowManagerService;

    if-nez v1, :cond_1

    const/4 v1, 0x0

    goto :goto_0

    :cond_1
    iget-object v1, p0, Lcom/android/server/wm/WindowState;->mService:Lcom/android/server/wm/WindowManagerService;

    iget-object v1, v1, Lcom/android/server/wm/WindowManagerService;->mInputMethodWindows:Landroid/util/SparseArray;

    invoke-virtual {p0}, Lcom/android/server/wm/WindowState;->getDisplayId()I

    move-result v2

    invoke-virtual {v1, v2}, Landroid/util/SparseArray;->get(I)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/android/server/wm/WindowState;

    .line 34
    :goto_0
    invoke-virtual {p0}, Lcom/android/server/wm/WindowState;->getStableFrameLw()Landroid/graphics/Rect;

    move-result-object v2

    iget v2, v2, Landroid/graphics/Rect;->bottom:I

    .line 35
    if-eqz v1, :cond_2

    invoke-virtual {v1}, Lcom/android/server/wm/WindowState;->isVisibleLw()Z

    move-result v1

    if-nez v1, :cond_3

    .line 36
    :cond_2
    invoke-static {p1, v2, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->restoreBottom(Landroid/graphics/Rect;ILandroid/graphics/Rect;)V

    invoke-static {p2, v2, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->restoreBottom(Landroid/graphics/Rect;ILandroid/graphics/Rect;)V

    .line 37
    iget-object v1, p0, Lcom/android/server/wm/WindowState;->mParentFrame:Landroid/graphics/Rect;

    invoke-static {v1, v2, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->restoreBottom(Landroid/graphics/Rect;ILandroid/graphics/Rect;)V

    iget-object v1, p0, Lcom/android/server/wm/WindowState;->mDisplayFrame:Landroid/graphics/Rect;

    invoke-static {v1, v2, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->restoreBottom(Landroid/graphics/Rect;ILandroid/graphics/Rect;)V

    .line 38
    iget-object v1, p0, Lcom/android/server/wm/WindowState;->mContainingFrame:Landroid/graphics/Rect;

    invoke-static {v1, v2, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->restoreBottom(Landroid/graphics/Rect;ILandroid/graphics/Rect;)V

    invoke-virtual {p0}, Lcom/android/server/wm/WindowState;->getContentFrameLw()Landroid/graphics/Rect;

    move-result-object v1

    invoke-static {v1, v2, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->restoreBottom(Landroid/graphics/Rect;ILandroid/graphics/Rect;)V

    .line 39
    iget-object v1, p0, Lcom/android/server/wm/WindowState;->mVisibleFrame:Landroid/graphics/Rect;

    invoke-static {v1, v2, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->restoreBottom(Landroid/graphics/Rect;ILandroid/graphics/Rect;)V

    invoke-virtual {p0}, Lcom/android/server/wm/WindowState;->getStableFrameLw()Landroid/graphics/Rect;

    move-result-object v1

    invoke-static {v1, v2, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->restoreBottom(Landroid/graphics/Rect;ILandroid/graphics/Rect;)V

    .line 41
    :cond_3
    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    invoke-static {p2, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 42
    iget-object p1, p0, Lcom/android/server/wm/WindowState;->mParentFrame:Landroid/graphics/Rect;

    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    iget-object p1, p0, Lcom/android/server/wm/WindowState;->mDisplayFrame:Landroid/graphics/Rect;

    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    iget-object p1, p0, Lcom/android/server/wm/WindowState;->mContainingFrame:Landroid/graphics/Rect;

    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 43
    invoke-virtual {p0}, Lcom/android/server/wm/WindowState;->getContentFrameLw()Landroid/graphics/Rect;

    move-result-object p1

    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    iget-object p1, p0, Lcom/android/server/wm/WindowState;->mVisibleFrame:Landroid/graphics/Rect;

    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    invoke-virtual {p0}, Lcom/android/server/wm/WindowState;->getStableFrameLw()Landroid/graphics/Rect;

    move-result-object p0

    invoke-static {p0, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->clip(Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    .line 44
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

.method static restoreBottom(Landroid/graphics/Rect;ILandroid/graphics/Rect;)V
    .registers 4

    .line 46
    if-eqz p0, :cond_0

    invoke-virtual {p0}, Landroid/graphics/Rect;->isEmpty()Z

    move-result v0

    if-nez v0, :cond_0

    iget v0, p2, Landroid/graphics/Rect;->top:I

    if-le p1, v0, :cond_0

    iget v0, p2, Landroid/graphics/Rect;->bottom:I

    if-ge p1, v0, :cond_0

    iget v0, p0, Landroid/graphics/Rect;->bottom:I

    if-ne v0, p1, :cond_0

    .line 47
    iget p1, p2, Landroid/graphics/Rect;->bottom:I

    iput p1, p0, Landroid/graphics/Rect;->bottom:I

    .line 48
    :cond_0
    return-void
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
