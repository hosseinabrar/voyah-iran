.method getOrientation(I)I
    .registers 3
    .param p1, "candidate"    # I

    .line 1302
    const/4 v0, 0x3

    if-ne p1, v0, :cond_0

    .line 1306
    iget v0, p0, Lcom/android/server/wm/AppWindowToken;->mOrientation:I

    invoke-static {p0, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->orientation(Lcom/android/server/wm/AppWindowToken;I)I

    move-result v0

    return v0

    .line 1312
    :cond_0
    iget-boolean v0, p0, Lcom/android/server/wm/AppWindowToken;->sendingToBottom:Z

    if-nez v0, :cond_2

    iget-object v0, p0, Lcom/android/server/wm/AppWindowToken;->mService:Lcom/android/server/wm/WindowManagerService;

    iget-object v0, v0, Lcom/android/server/wm/WindowManagerService;->mClosingApps:Landroid/util/ArraySet;

    invoke-virtual {v0, p0}, Landroid/util/ArraySet;->contains(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_2

    .line 1313
    invoke-virtual {p0}, Lcom/android/server/wm/AppWindowToken;->isVisible()Z

    move-result v0

    if-nez v0, :cond_1

    iget-object v0, p0, Lcom/android/server/wm/AppWindowToken;->mService:Lcom/android/server/wm/WindowManagerService;

    iget-object v0, v0, Lcom/android/server/wm/WindowManagerService;->mOpeningApps:Landroid/util/ArraySet;

    invoke-virtual {v0, p0}, Landroid/util/ArraySet;->contains(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_2

    .line 1314
    :cond_1
    iget v0, p0, Lcom/android/server/wm/AppWindowToken;->mOrientation:I

    invoke-static {p0, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->orientation(Lcom/android/server/wm/AppWindowToken;I)I

    move-result v0

    return v0

    .line 1317
    :cond_2
    const/4 v0, -0x2

    invoke-static {p0, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->orientation(Lcom/android/server/wm/AppWindowToken;I)I

    move-result v0

    return v0
.end method
