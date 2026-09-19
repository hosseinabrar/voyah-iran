.method resolveOverrideConfiguration(Landroid/content/res/Configuration;)V
    .locals 3
    .param p1, "newParentConfiguration"    # Landroid/content/res/Configuration;

    .line 6563
    invoke-super {p0, p1}, Lcom/android/server/wm/WindowToken;->resolveOverrideConfiguration(Landroid/content/res/Configuration;)V

    .line 6564
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getResolvedOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object v0

    .line 6565
    .local v0, "resolvedConfig":Landroid/content/res/Configuration;
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->isFixedRotationTransforming()Z

    move-result v1

    if-eqz v1, :cond_0

    .line 6571
    iget-object v1, p0, Lcom/android/server/wm/ActivityRecord;->mTmpConfig:Landroid/content/res/Configuration;

    invoke-virtual {v1, p1}, Landroid/content/res/Configuration;->setTo(Landroid/content/res/Configuration;)V

    .line 6572
    iget-object v1, p0, Lcom/android/server/wm/ActivityRecord;->mTmpConfig:Landroid/content/res/Configuration;

    invoke-virtual {v1, v0}, Landroid/content/res/Configuration;->updateFrom(Landroid/content/res/Configuration;)I

    .line 6573
    iget-object p1, p0, Lcom/android/server/wm/ActivityRecord;->mTmpConfig:Landroid/content/res/Configuration;

    .line 6575
    :cond_0
    iget-object v1, p0, Lcom/android/server/wm/ActivityRecord;->mCompatDisplayInsets:Lcom/android/server/wm/ActivityRecord$CompatDisplayInsets;

    if-eqz v1, :cond_1

    .line 6576
    invoke-direct {p0, p1}, Lcom/android/server/wm/ActivityRecord;->resolveSizeCompatModeConfiguration(Landroid/content/res/Configuration;)V

    goto :goto_0

    .line 6578
    :cond_1
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->inMultiWindowMode()Z

    move-result v1

    if-eqz v1, :cond_2

    .line 6581
    const/4 v1, 0x0

    iput v1, v0, Landroid/content/res/Configuration;->orientation:I

    .line 6584
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->matchParentBounds()Z

    move-result v1

    if-nez v1, :cond_3

    .line 6585
    iget-object v1, p0, Lcom/android/server/wm/ActivityRecord;->task:Lcom/android/server/wm/Task;

    invoke-virtual {v1, v0, p1}, Lcom/android/server/wm/Task;->computeConfigResourceOverrides(Landroid/content/res/Configuration;Landroid/content/res/Configuration;)V

    goto :goto_0

    .line 6588
    :cond_2
    invoke-direct {p0, p1}, Lcom/android/server/wm/ActivityRecord;->resolveFullscreenConfiguration(Landroid/content/res/Configuration;)V

    .line 6595
    :cond_3
    :goto_0
    iget v1, p0, Lcom/android/server/wm/ActivityRecord;->mConfigurationSeq:I

    const/4 v2, 0x1

    add-int/2addr v1, v2

    iput v1, p0, Lcom/android/server/wm/ActivityRecord;->mConfigurationSeq:I

    invoke-static {v1, v2}, Ljava/lang/Math;->max(II)I

    move-result v1

    iput v1, p0, Lcom/android/server/wm/ActivityRecord;->mConfigurationSeq:I

    .line 6596
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getResolvedOverrideConfiguration()Landroid/content/res/Configuration;

    move-result-object v1

    iget v2, p0, Lcom/android/server/wm/ActivityRecord;->mConfigurationSeq:I

    iput v2, v1, Landroid/content/res/Configuration;->seq:I

    .line 6597
    return-void
.end method
