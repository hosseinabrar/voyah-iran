.method private applyGravityAndUpdateFrame(Lcom/android/server/wm/WindowFrames;Landroid/graphics/Rect;Landroid/graphics/Rect;)V
    .locals 21
    .param p1, "windowFrames"    # Lcom/android/server/wm/WindowFrames;
    .param p2, "containingFrame"    # Landroid/graphics/Rect;
    .param p3, "displayFrame"    # Landroid/graphics/Rect;

    .line 4184
    move-object/from16 v0, p0

    move-object/from16 v1, p1

    invoke-virtual/range {p2 .. p2}, Landroid/graphics/Rect;->width()I

    move-result v2

    .line 4185
    .local v2, "pw":I
    invoke-virtual/range {p2 .. p2}, Landroid/graphics/Rect;->height()I

    move-result v3

    .line 4186
    .local v3, "ph":I
    invoke-virtual/range {p0 .. p0}, Lcom/android/server/wm/WindowState;->getTask()Lcom/android/server/wm/Task;

    move-result-object v4

    .line 4187
    .local v4, "task":Lcom/android/server/wm/Task;
    invoke-direct/range {p0 .. p0}, Lcom/android/server/wm/WindowState;->inAppWindowThatMatchesParentBounds()Z

    move-result v5

    const/4 v6, 0x1

    xor-int/2addr v5, v6

    .line 4188
    .local v5, "inNonFullscreenContainer":Z
    iget-object v7, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v7, v7, Landroid/view/WindowManager$LayoutParams;->flags:I

    and-int/lit16 v7, v7, 0x200

    const/4 v8, 0x0

    if-eqz v7, :cond_0

    move v7, v6

    goto :goto_0

    :cond_0
    move v7, v8

    .line 4197
    .local v7, "noLimits":Z
    :goto_0
    if-eqz v4, :cond_2

    if-eqz v5, :cond_2

    iget-object v9, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v9, v9, Landroid/view/WindowManager$LayoutParams;->type:I

    if-eq v9, v6, :cond_1

    if-nez v7, :cond_1

    goto :goto_1

    :cond_1
    move v6, v8

    .line 4202
    .local v6, "fitToDisplay":Z
    :cond_2
    :goto_1
    invoke-virtual/range {p0 .. p0}, Lcom/android/server/wm/WindowState;->inSizeCompatMode()Z

    move-result v8

    .line 4203
    .local v8, "inSizeCompatMode":Z
    iget-object v9, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v9, v9, Landroid/view/WindowManager$LayoutParams;->flags:I

    and-int/lit16 v9, v9, 0x4000

    const/high16 v10, 0x3f000000    # 0.5f

    if-eqz v9, :cond_7

    .line 4204
    iget-object v9, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v9, v9, Landroid/view/WindowManager$LayoutParams;->width:I

    if-gez v9, :cond_3

    .line 4205
    move v9, v2

    .local v9, "w":I
    goto :goto_2

    .line 4206
    .end local v9    # "w":I
    :cond_3
    if-eqz v8, :cond_4

    .line 4207
    iget-object v9, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v9, v9, Landroid/view/WindowManager$LayoutParams;->width:I

    int-to-float v9, v9

    iget v11, v0, Lcom/android/server/wm/WindowState;->mGlobalScale:F

    mul-float/2addr v9, v11

    add-float/2addr v9, v10

    float-to-int v9, v9

    .restart local v9    # "w":I
    goto :goto_2

    .line 4209
    .end local v9    # "w":I
    :cond_4
    iget-object v9, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v9, v9, Landroid/view/WindowManager$LayoutParams;->width:I

    .line 4211
    .restart local v9    # "w":I
    :goto_2
    iget-object v11, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v11, v11, Landroid/view/WindowManager$LayoutParams;->height:I

    if-gez v11, :cond_5

    .line 4212
    move v10, v3

    .local v10, "h":I
    goto :goto_4

    .line 4213
    .end local v10    # "h":I
    :cond_5
    if-eqz v8, :cond_6

    .line 4214
    iget-object v11, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v11, v11, Landroid/view/WindowManager$LayoutParams;->height:I

    int-to-float v11, v11

    iget v12, v0, Lcom/android/server/wm/WindowState;->mGlobalScale:F

    mul-float/2addr v11, v12

    add-float/2addr v11, v10

    float-to-int v10, v11

    .restart local v10    # "h":I
    goto :goto_4

    .line 4216
    .end local v10    # "h":I
    :cond_6
    iget-object v10, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v10, v10, Landroid/view/WindowManager$LayoutParams;->height:I

    .restart local v10    # "h":I
    goto :goto_4

    .line 4219
    .end local v9    # "w":I
    .end local v10    # "h":I
    :cond_7
    iget-object v9, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v9, v9, Landroid/view/WindowManager$LayoutParams;->width:I

    const/4 v11, -0x1

    if-ne v9, v11, :cond_8

    .line 4220
    move v9, v2

    .restart local v9    # "w":I
    goto :goto_3

    .line 4221
    .end local v9    # "w":I
    :cond_8
    if-eqz v8, :cond_9

    .line 4222
    iget v9, v0, Lcom/android/server/wm/WindowState;->mRequestedWidth:I

    int-to-float v9, v9

    iget v12, v0, Lcom/android/server/wm/WindowState;->mGlobalScale:F

    mul-float/2addr v9, v12

    add-float/2addr v9, v10

    float-to-int v9, v9

    .restart local v9    # "w":I
    goto :goto_3

    .line 4224
    .end local v9    # "w":I
    :cond_9
    iget v9, v0, Lcom/android/server/wm/WindowState;->mRequestedWidth:I

    .line 4226
    .restart local v9    # "w":I
    :goto_3
    iget-object v12, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v12, v12, Landroid/view/WindowManager$LayoutParams;->height:I

    if-ne v12, v11, :cond_a

    .line 4227
    move v10, v3

    .restart local v10    # "h":I
    goto :goto_4

    .line 4228
    .end local v10    # "h":I
    :cond_a
    if-eqz v8, :cond_b

    .line 4229
    iget v11, v0, Lcom/android/server/wm/WindowState;->mRequestedHeight:I

    int-to-float v11, v11

    iget v12, v0, Lcom/android/server/wm/WindowState;->mGlobalScale:F

    mul-float/2addr v11, v12

    add-float/2addr v11, v10

    float-to-int v10, v11

    .restart local v10    # "h":I
    goto :goto_4

    .line 4231
    .end local v10    # "h":I
    :cond_b
    iget v10, v0, Lcom/android/server/wm/WindowState;->mRequestedHeight:I

    .line 4235
    .restart local v10    # "h":I
    :goto_4
    if-eqz v8, :cond_c

    .line 4236
    iget-object v11, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v11, v11, Landroid/view/WindowManager$LayoutParams;->x:I

    int-to-float v11, v11

    iget v12, v0, Lcom/android/server/wm/WindowState;->mGlobalScale:F

    mul-float/2addr v11, v12

    .line 4237
    .local v11, "x":F
    iget-object v12, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v12, v12, Landroid/view/WindowManager$LayoutParams;->y:I

    int-to-float v12, v12

    iget v13, v0, Lcom/android/server/wm/WindowState;->mGlobalScale:F

    mul-float/2addr v12, v13

    .local v12, "y":F
    goto :goto_5

    .line 4239
    .end local v11    # "x":F
    .end local v12    # "y":F
    :cond_c
    iget-object v11, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v11, v11, Landroid/view/WindowManager$LayoutParams;->x:I

    int-to-float v11, v11

    .line 4240
    .restart local v11    # "x":F
    iget-object v12, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v12, v12, Landroid/view/WindowManager$LayoutParams;->y:I

    int-to-float v12, v12

    .line 4243
    .restart local v12    # "y":F
    :goto_5
    if-eqz v5, :cond_d

    invoke-virtual/range {p0 .. p0}, Lcom/android/server/wm/WindowState;->layoutInParentFrame()Z

    move-result v13

    if-nez v13, :cond_d

    .line 4246
    invoke-static {v9, v2}, Ljava/lang/Math;->min(II)I

    move-result v9

    .line 4247
    invoke-static {v10, v3}, Ljava/lang/Math;->min(II)I

    move-result v10

    .line 4251
    :cond_d
    iget-object v13, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v13, v13, Landroid/view/WindowManager$LayoutParams;->gravity:I

    iget-object v14, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v14, v14, Landroid/view/WindowManager$LayoutParams;->horizontalMargin:F

    int-to-float v15, v2

    mul-float/2addr v14, v15

    add-float/2addr v14, v11

    float-to-int v15, v14

    iget-object v14, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v14, v14, Landroid/view/WindowManager$LayoutParams;->verticalMargin:F

    move/from16 v20, v2

    .end local v2    # "pw":I
    .local v20, "pw":I
    int-to-float v2, v3

    mul-float/2addr v14, v2

    add-float/2addr v14, v12

    float-to-int v2, v14

    iget-object v14, v1, Lcom/android/server/wm/WindowFrames;->mFrame:Landroid/graphics/Rect;

    move-object/from16 v19, v14

    move v14, v9

    move/from16 v17, v15

    move v15, v10

    move-object/from16 v16, p2

    move/from16 v18, v2

    invoke-static/range {v13 .. v19}, Landroid/view/Gravity;->apply(IIILandroid/graphics/Rect;IILandroid/graphics/Rect;)V

    .line 4256
    if-eqz v6, :cond_e

    .line 4257
    iget-object v2, v0, Lcom/android/server/wm/WindowState;->mAttrs:Landroid/view/WindowManager$LayoutParams;

    iget v2, v2, Landroid/view/WindowManager$LayoutParams;->gravity:I

    iget-object v13, v1, Lcom/android/server/wm/WindowFrames;->mFrame:Landroid/graphics/Rect;

    move-object/from16 v14, p3

    invoke-static {v2, v14, v13}, Landroid/view/Gravity;->applyDisplay(ILandroid/graphics/Rect;Landroid/graphics/Rect;)V

    goto :goto_6

    .line 4256
    :cond_e
    move-object/from16 v14, p3

    .line 4262
    :goto_6
    iget-object v2, v1, Lcom/android/server/wm/WindowFrames;->mCompatFrame:Landroid/graphics/Rect;

    iget-object v13, v1, Lcom/android/server/wm/WindowFrames;->mFrame:Landroid/graphics/Rect;

    invoke-virtual {v2, v13}, Landroid/graphics/Rect;->set(Landroid/graphics/Rect;)V

    .line 4263
    if-eqz v8, :cond_f

    .line 4265
    iget-object v2, v1, Lcom/android/server/wm/WindowFrames;->mCompatFrame:Landroid/graphics/Rect;

    iget v13, v0, Lcom/android/server/wm/WindowState;->mInvGlobalScale:F

    invoke-virtual {v2, v13}, Landroid/graphics/Rect;->scale(F)V

    .line 4267
    :cond_f
    return-void
.end method
