.method applyGravityAndUpdateFrame(Landroid/graphics/Rect;Landroid/graphics/Rect;)V
    .registers 21
    .param p1, "containingFrame"    # Landroid/graphics/Rect;
    .param p2, "displayFrame"    # Landroid/graphics/Rect;

    invoke-static/range {p0 .. p2}, Lcom/android/server/wm/VoyahIranLegacyFrames;->before(Lcom/android/server/wm/WindowState;Landroid/graphics/Rect;Landroid/graphics/Rect;)V

    move-object/from16 v0, p0

    .line 3578
    invoke-virtual/range {p1 .. p1}, Landroid/graphics/Rect;->width()I

    move-result v1

    .line 3579
    .local v1, "pw":I
    invoke-virtual/range {p1 .. p1}, Landroid/graphics/Rect;->height()I

    move-result v2

    .line 3580
    .local v2, "ph":I
    invoke-virtual/range {p0 .. p0}, Lcom/android/server/wm/WindowState;->getTask()Lcom/android/server/wm/Task;

    move-result-object v3

    .line 3581
    .local v3, "task":Lcom/android/server/wm/Task;
    invoke-direct/range {p0 .. p0}, Lcom/android/server/wm/WindowState;->inFullscreenContainer()Z

    move-result v4

    const/4 v5, 0x1

    xor-int/2addr v4, v5

    .line 3582
    .local v4, "inNonFullscreenContainer":Z
    iget-object v6, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v6, v6, Landroid/view/WindowManager$LayoutParams;->flags:I

    and-int/lit16 v6, v6, 0x200

    const/4 v7, 0x0

    if-eqz v6, :cond_0

    move v6, v5

    goto :goto_0

    :cond_0
    move v6, v7

    .line 3591
    .local v6, "noLimits":Z
    :goto_0
    if-eqz v3, :cond_2

    if-eqz v4, :cond_2

    iget-object v8, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v8, v8, Landroid/view/WindowManager$LayoutParams;->type:I

    if-eq v8, v5, :cond_1

    if-nez v6, :cond_1

    goto :goto_1

    :cond_1
    move v5, v7

    nop

    .line 3596
    .local v5, "fitToDisplay":Z
    :cond_2
    :goto_1
    iget-object v7, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v7, v7, Landroid/view/WindowManager$LayoutParams;->flags:I

    and-int/lit16 v7, v7, 0x4000

    const/high16 v8, 0x3f000000    # 0.5f

    if-eqz v7, :cond_7

    .line 3597
    iget-object v7, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v7, v7, Landroid/view/WindowManager$LayoutParams;->width:I

    if-gez v7, :cond_3

    .line 3598
    move v7, v1

    .local v7, "w":I
    :goto_2
    goto :goto_3

    .line 3599
    .end local v7    # "w":I
    :cond_3
    iget-boolean v7, v0, Lcom/android/server/wm/WindowState;->mEnforceSizeCompat:Z

    if-eqz v7, :cond_4

    .line 3600
    iget-object v7, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v7, v7, Landroid/view/WindowManager$LayoutParams;->width:I

    int-to-float v7, v7

    iget v9, v0, Lcom/android/server/wm/WindowState;->mGlobalScale:F

    mul-float/2addr v7, v9

    add-float/2addr v7, v8

    float-to-int v7, v7

    goto :goto_2

    .line 3602
    :cond_4
    iget-object v7, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v7, v7, Landroid/view/WindowManager$LayoutParams;->width:I

    .line 3604
    .restart local v7    # "w":I
    :goto_3
    iget-object v9, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v9, v9, Landroid/view/WindowManager$LayoutParams;->height:I

    if-gez v9, :cond_5

    .line 3605
    move v8, v2

    .local v8, "h":I
    :goto_4
    goto :goto_7

    .line 3606
    .end local v8    # "h":I
    :cond_5
    iget-boolean v9, v0, Lcom/android/server/wm/WindowState;->mEnforceSizeCompat:Z

    if-eqz v9, :cond_6

    .line 3607
    iget-object v9, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v9, v9, Landroid/view/WindowManager$LayoutParams;->height:I

    int-to-float v9, v9

    iget v10, v0, Lcom/android/server/wm/WindowState;->mGlobalScale:F

    mul-float/2addr v9, v10

    add-float/2addr v9, v8

    float-to-int v8, v9

    goto :goto_4

    .line 3609
    :cond_6
    iget-object v8, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v8, v8, Landroid/view/WindowManager$LayoutParams;->height:I

    goto :goto_4

    .line 3612
    .end local v7    # "w":I
    :cond_7
    iget-object v7, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v7, v7, Landroid/view/WindowManager$LayoutParams;->width:I

    const/4 v9, -0x1

    if-ne v7, v9, :cond_8

    .line 3613
    move v7, v1

    .restart local v7    # "w":I
    :goto_5
    goto :goto_6

    .line 3614
    .end local v7    # "w":I
    :cond_8
    iget-boolean v7, v0, Lcom/android/server/wm/WindowState;->mEnforceSizeCompat:Z

    if-eqz v7, :cond_9

    .line 3615
    iget v7, v0, Lcom/android/server/wm/WindowState;->mRequestedWidth:I

    int-to-float v7, v7

    iget v10, v0, Lcom/android/server/wm/WindowState;->mGlobalScale:F

    mul-float/2addr v7, v10

    add-float/2addr v7, v8

    float-to-int v7, v7

    goto :goto_5

    .line 3617
    :cond_9
    iget v7, v0, Lcom/android/server/wm/WindowState;->mRequestedWidth:I

    .line 3619
    .restart local v7    # "w":I
    :goto_6
    iget-object v10, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v10, v10, Landroid/view/WindowManager$LayoutParams;->height:I

    if-ne v10, v9, :cond_a

    .line 3620
    move v8, v2

    goto :goto_4

    .line 3621
    :cond_a
    iget-boolean v9, v0, Lcom/android/server/wm/WindowState;->mEnforceSizeCompat:Z

    if-eqz v9, :cond_b

    .line 3622
    iget v9, v0, Lcom/android/server/wm/WindowState;->mRequestedHeight:I

    int-to-float v9, v9

    iget v10, v0, Lcom/android/server/wm/WindowState;->mGlobalScale:F

    mul-float/2addr v9, v10

    add-float/2addr v9, v8

    float-to-int v8, v9

    goto :goto_4

    .line 3624
    :cond_b
    iget v8, v0, Lcom/android/server/wm/WindowState;->mRequestedHeight:I

    .line 3628
    .restart local v8    # "h":I
    :goto_7
    iget-boolean v9, v0, Lcom/android/server/wm/WindowState;->mEnforceSizeCompat:Z

    if-eqz v9, :cond_c

    .line 3629
    iget-object v9, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v9, v9, Landroid/view/WindowManager$LayoutParams;->x:I

    int-to-float v9, v9

    iget v10, v0, Lcom/android/server/wm/WindowState;->mGlobalScale:F

    mul-float/2addr v9, v10

    .line 3630
    .local v9, "x":F
    iget-object v10, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v10, v10, Landroid/view/WindowManager$LayoutParams;->y:I

    int-to-float v10, v10

    iget v11, v0, Lcom/android/server/wm/WindowState;->mGlobalScale:F

    mul-float/2addr v10, v11

    .local v10, "y":F
    goto :goto_8

    .line 3632
    .end local v9    # "x":F
    .end local v10    # "y":F
    :cond_c
    iget-object v9, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v9, v9, Landroid/view/WindowManager$LayoutParams;->x:I

    int-to-float v9, v9

    .line 3633
    .restart local v9    # "x":F
    iget-object v10, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v10, v10, Landroid/view/WindowManager$LayoutParams;->y:I

    int-to-float v10, v10

    .line 3636
    .restart local v10    # "y":F
    :goto_8
    if-eqz v4, :cond_d

    invoke-virtual/range {p0 .. p0}, Lcom/android/server/wm/WindowState;->layoutInParentFrame()Z

    move-result v11

    if-nez v11, :cond_d

    .line 3639
    invoke-static {v7, v1}, Ljava/lang/Math;->min(II)I

    move-result v7

    .line 3640
    invoke-static {v8, v2}, Ljava/lang/Math;->min(II)I

    move-result v8

    .line 3644
    :cond_d
    iget-object v11, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v11, v11, Landroid/view/WindowManager$LayoutParams;->gravity:I

    iget-object v12, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v12, v12, Landroid/view/WindowManager$LayoutParams;->horizontalMargin:F

    int-to-float v13, v1

    mul-float/2addr v12, v13

    add-float/2addr v12, v9

    float-to-int v15, v12

    iget-object v12, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v12, v12, Landroid/view/WindowManager$LayoutParams;->verticalMargin:F

    int-to-float v13, v2

    mul-float/2addr v12, v13

    add-float/2addr v12, v10

    float-to-int v14, v12

    iget-object v13, v0, Lcom/android/server/wm/WindowState;->mFrame:Landroid/graphics/Rect;

    move v12, v7

    move-object/from16 v17, v13

    move v13, v8

    move/from16 v16, v14

    move-object/from16 v14, p1

    invoke-static/range {v11 .. v17}, Landroid/view/Gravity;->apply(IIILandroid/graphics/Rect;IILandroid/graphics/Rect;)V

    .line 3649
    if-eqz v5, :cond_e

    .line 3650
    iget-object v11, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v11, v11, Landroid/view/WindowManager$LayoutParams;->gravity:I

    iget-object v12, v0, Lcom/android/server/wm/WindowState;->mFrame:Landroid/graphics/Rect;

    move-object/from16 v13, p2

    invoke-static {v11, v13, v12}, Landroid/view/Gravity;->applyDisplay(ILandroid/graphics/Rect;Landroid/graphics/Rect;)V

    goto :goto_9

    .line 3655
    :cond_e
    move-object/from16 v13, p2

    :goto_9
    iget-object v11, v0, Lcom/android/server/wm/WindowState;->mCompatFrame:Landroid/graphics/Rect;

    iget-object v12, v0, Lcom/android/server/wm/WindowState;->mFrame:Landroid/graphics/Rect;

    invoke-virtual {v11, v12}, Landroid/graphics/Rect;->set(Landroid/graphics/Rect;)V

    .line 3656
    iget-boolean v11, v0, Lcom/android/server/wm/WindowState;->mEnforceSizeCompat:Z

    if-eqz v11, :cond_f

    .line 3658
    iget-object v11, v0, Lcom/android/server/wm/WindowState;->mCompatFrame:Landroid/graphics/Rect;

    iget v12, v0, Lcom/android/server/wm/WindowState;->mInvGlobalScale:F

    invoke-virtual {v11, v12}, Landroid/graphics/Rect;->scale(F)V

    .line 3660
    :cond_f
    invoke-static/range {p0 .. p0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->after(Lcom/android/server/wm/WindowState;)V

    return-void
.end method
