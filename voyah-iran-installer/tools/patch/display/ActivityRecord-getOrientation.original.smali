.method getOrientation(I)I
    .locals 1
    .param p1, "candidate"    # I

    .line 6359
    const/4 v0, 0x3

    if-ne p1, v0, :cond_0

    .line 6363
    iget v0, p0, Lcom/android/server/wm/ActivityRecord;->mOrientation:I

    return v0

    .line 6369
    :cond_0
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getDisplayContent()Lcom/android/server/wm/DisplayContent;

    move-result-object v0

    iget-object v0, v0, Lcom/android/server/wm/DisplayContent;->mClosingApps:Landroid/util/ArraySet;

    invoke-virtual {v0, p0}, Landroid/util/ArraySet;->contains(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_2

    .line 6370
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->isVisible()Z

    move-result v0

    if-nez v0, :cond_1

    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getDisplayContent()Lcom/android/server/wm/DisplayContent;

    move-result-object v0

    iget-object v0, v0, Lcom/android/server/wm/DisplayContent;->mOpeningApps:Landroid/util/ArraySet;

    invoke-virtual {v0, p0}, Landroid/util/ArraySet;->contains(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_2

    .line 6371
    :cond_1
    iget v0, p0, Lcom/android/server/wm/ActivityRecord;->mOrientation:I

    return v0

    .line 6374
    :cond_2
    const/4 v0, -0x2

    return v0
.end method
