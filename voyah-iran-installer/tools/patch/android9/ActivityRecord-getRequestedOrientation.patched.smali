.method getRequestedOrientation()I
    .registers 2

    .line 2298
    iget-object v0, p0, Lcom/android/server/am/ActivityRecord;->mWindowContainerController:Lcom/android/server/wm/AppWindowContainerController;

    invoke-virtual {v0}, Lcom/android/server/wm/AppWindowContainerController;->getOrientation()I

    move-result v0

    invoke-static {p0, v0}, Lcom/android/server/am/VoyahIranLegacyDisplay;->orientation(Ljava/lang/Object;I)I

    move-result v0

    return v0
.end method
