.method private updateOverrideConfiguration()V
    .registers 8

    .line 2346
    iget-object v0, p0, Lcom/android/server/am/ActivityRecord;->mTmpConfig:Landroid/content/res/Configuration;

    invoke-virtual {v0}, Landroid/content/res/Configuration;->unset()V

    .line 2347
    iget-object v0, p0, Lcom/android/server/am/ActivityRecord;->mTmpBounds:Landroid/graphics/Rect;

    invoke-direct {p0, v0}, Lcom/android/server/am/ActivityRecord;->computeBounds(Landroid/graphics/Rect;)V

    .line 2349
    iget-object v0, p0, Lcom/android/server/am/ActivityRecord;->mTmpBounds:Landroid/graphics/Rect;

    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getOverrideBounds()Landroid/graphics/Rect;

    move-result-object v1

    invoke-virtual {v0, v1}, Landroid/graphics/Rect;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    .line 2350
    return-void

    .line 2353
    :cond_0
    iget-object v0, p0, Lcom/android/server/am/ActivityRecord;->mTmpBounds:Landroid/graphics/Rect;

    invoke-virtual {p0, v0}, Lcom/android/server/am/ActivityRecord;->setBounds(Landroid/graphics/Rect;)I

    .line 2355
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->getOverrideBounds()Landroid/graphics/Rect;

    move-result-object v0

    .line 2358
    .local v0, "updatedBounds":Landroid/graphics/Rect;
    invoke-virtual {p0}, Lcom/android/server/am/ActivityRecord;->matchParentBounds()Z

    move-result v1

    if-nez v1, :cond_1

    .line 2359
    iget-object v1, p0, Lcom/android/server/am/ActivityRecord;->task:Lcom/android/server/am/TaskRecord;

    iget-object v2, p0, Lcom/android/server/am/ActivityRecord;->mTmpConfig:Landroid/content/res/Configuration;

    const/4 v4, 0x0

    const/4 v5, 0x0

    const/4 v6, 0x0

    move-object v3, v0

    invoke-virtual/range {v1 .. v6}, Lcom/android/server/am/TaskRecord;->computeOverrideConfiguration(Landroid/content/res/Configuration;Landroid/graphics/Rect;Landroid/graphics/Rect;ZZ)V

    .line 2363
    :cond_1
    iget-object v1, p0, Lcom/android/server/am/ActivityRecord;->mTmpConfig:Landroid/content/res/Configuration;

    invoke-virtual {p0, v1}, Lcom/android/server/am/ActivityRecord;->onOverrideConfigurationChanged(Landroid/content/res/Configuration;)V

    .line 2364
    return-void
.end method
