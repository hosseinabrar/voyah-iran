.method layoutLetterbox(Lcom/android/server/wm/WindowState;)V
    .locals 8
    .param p1, "winHint"    # Lcom/android/server/wm/WindowState;

    .line 1356
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->findMainWindow()Lcom/android/server/wm/WindowState;

    move-result-object v0

    .line 1357
    .local v0, "w":Lcom/android/server/wm/WindowState;
    if-eqz v0, :cond_9

    if-eqz p1, :cond_0

    if-eq v0, p1, :cond_0

    goto/16 :goto_5

    .line 1360
    :cond_0
    invoke-virtual {v0}, Lcom/android/server/wm/WindowState;->isDrawnLw()Z

    move-result v1

    const/4 v2, 0x0

    const/4 v3, 0x1

    if-nez v1, :cond_2

    iget-object v1, v0, Lcom/android/server/wm/WindowState;->mWinAnimator:Lcom/android/server/wm/WindowStateAnimator;

    iget-boolean v1, v1, Lcom/android/server/wm/WindowStateAnimator;->mSurfaceDestroyDeferred:Z

    if-nez v1, :cond_2

    .line 1362
    invoke-virtual {v0}, Lcom/android/server/wm/WindowState;->isDragResizeChanged()Z

    move-result v1

    if-eqz v1, :cond_1

    goto :goto_0

    :cond_1
    move v1, v2

    goto :goto_1

    :cond_2
    :goto_0
    move v1, v3

    .line 1363
    .local v1, "surfaceReady":Z
    :goto_1
    if-eqz v1, :cond_3

    invoke-virtual {v0}, Lcom/android/server/wm/WindowState;->isLetterboxedAppWindow()Z

    move-result v4

    if-eqz v4, :cond_3

    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->fillsParent()Z

    move-result v4

    if-eqz v4, :cond_3

    move v2, v3

    .line 1364
    .local v2, "needsLetterbox":Z
    :cond_3
    if-eqz v2, :cond_7

    .line 1365
    iget-object v3, p0, Lcom/android/server/wm/ActivityRecord;->mLetterbox:Lcom/android/server/wm/Letterbox;

    if-nez v3, :cond_4

    .line 1366
    new-instance v3, Lcom/android/server/wm/Letterbox;

    new-instance v4, Lcom/android/server/wm/-$$Lambda$ActivityRecord$lyqdJlA4QOn1CXj7zglxNJxDy9o;

    invoke-direct {v4, p0}, Lcom/android/server/wm/-$$Lambda$ActivityRecord$lyqdJlA4QOn1CXj7zglxNJxDy9o;-><init>(Lcom/android/server/wm/ActivityRecord;)V

    iget-object v5, p0, Lcom/android/server/wm/ActivityRecord;->mWmService:Lcom/android/server/wm/WindowManagerService;

    iget-object v5, v5, Lcom/android/server/wm/WindowManagerService;->mTransactionFactory:Ljava/util/function/Supplier;

    invoke-direct {v3, v4, v5}, Lcom/android/server/wm/Letterbox;-><init>(Ljava/util/function/Supplier;Ljava/util/function/Supplier;)V

    iput-object v3, p0, Lcom/android/server/wm/ActivityRecord;->mLetterbox:Lcom/android/server/wm/Letterbox;

    .line 1368
    invoke-virtual {v3, v0}, Lcom/android/server/wm/Letterbox;->attachInput(Lcom/android/server/wm/WindowState;)V

    .line 1370
    :cond_4
    iget-object v3, p0, Lcom/android/server/wm/ActivityRecord;->mTmpPoint:Landroid/graphics/Point;

    invoke-virtual {p0, v3}, Lcom/android/server/wm/ActivityRecord;->getPosition(Landroid/graphics/Point;)V

    .line 1375
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getFixedRotationTransformDisplayBounds()Landroid/graphics/Rect;

    move-result-object v3

    .line 1376
    .local v3, "transformedBounds":Landroid/graphics/Rect;
    if-eqz v3, :cond_5

    .line 1377
    move-object v4, v3

    goto :goto_2

    .line 1378
    :cond_5
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->inMultiWindowMode()Z

    move-result v4

    if-eqz v4, :cond_6

    .line 1379
    iget-object v4, p0, Lcom/android/server/wm/ActivityRecord;->task:Lcom/android/server/wm/Task;

    invoke-virtual {v4}, Lcom/android/server/wm/Task;->getBounds()Landroid/graphics/Rect;

    move-result-object v4

    goto :goto_2

    .line 1380
    :cond_6
    invoke-virtual {p0}, Lcom/android/server/wm/ActivityRecord;->getRootTask()Lcom/android/server/wm/ActivityStack;

    move-result-object v4

    invoke-virtual {v4}, Lcom/android/server/wm/ActivityStack;->getParent()Lcom/android/server/wm/WindowContainer;

    move-result-object v4

    invoke-virtual {v4}, Lcom/android/server/wm/WindowContainer;->getBounds()Landroid/graphics/Rect;

    move-result-object v4

    :goto_2
    nop

    .line 1381
    .local v4, "spaceToFill":Landroid/graphics/Rect;
    iget-object v5, p0, Lcom/android/server/wm/ActivityRecord;->mLetterbox:Lcom/android/server/wm/Letterbox;

    invoke-virtual {v0}, Lcom/android/server/wm/WindowState;->getFrameLw()Landroid/graphics/Rect;

    move-result-object v6

    iget-object v7, p0, Lcom/android/server/wm/ActivityRecord;->mTmpPoint:Landroid/graphics/Point;

    invoke-virtual {v5, v4, v6, v7}, Lcom/android/server/wm/Letterbox;->layout(Landroid/graphics/Rect;Landroid/graphics/Rect;Landroid/graphics/Point;)V

    .end local v3    # "transformedBounds":Landroid/graphics/Rect;
    .end local v4    # "spaceToFill":Landroid/graphics/Rect;
    goto :goto_3

    .line 1382
    :cond_7
    iget-object v3, p0, Lcom/android/server/wm/ActivityRecord;->mLetterbox:Lcom/android/server/wm/Letterbox;

    if-eqz v3, :cond_8

    .line 1383
    invoke-virtual {v3}, Lcom/android/server/wm/Letterbox;->hide()V

    goto :goto_4

    .line 1382
    :cond_8
    :goto_3
    nop

    .line 1385
    :goto_4
    return-void

    .line 1358
    .end local v1    # "surfaceReady":Z
    .end local v2    # "needsLetterbox":Z
    :cond_9
    :goto_5
    return-void
.end method
