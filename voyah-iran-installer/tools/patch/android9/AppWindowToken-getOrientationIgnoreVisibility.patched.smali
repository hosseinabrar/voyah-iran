.method getOrientationIgnoreVisibility()I
    .registers 2

    .line 1322
    iget v0, p0, Lcom/android/server/wm/AppWindowToken;->mOrientation:I

    invoke-static {p0, v0}, Lcom/android/server/wm/VoyahIranLegacyFrames;->orientation(Lcom/android/server/wm/AppWindowToken;I)I

    move-result v0

    return v0
.end method
