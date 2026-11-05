.method private generatePackageInfo(Lcom/android/server/pm/PackageSetting;II)Landroid/content/pm/PackageInfo;
    .locals 21
    .param p1, "ps"    # Lcom/android/server/pm/PackageSetting;
    .param p2, "flags"    # I
    .param p3, "userId"    # I

    .line 4362
    move-object/from16 v0, p0

    move-object/from16 v12, p1

    move/from16 v1, p2

    move/from16 v13, p3

    iget-object v2, v0, Lcom/android/server/pm/PackageManagerService;->mUserManager:Lcom/android/server/pm/UserManagerService;

    invoke-virtual {v2, v13}, Lcom/android/server/pm/UserManagerService;->exists(I)Z

    move-result v2

    const/4 v14, 0x0

    if-nez v2, :cond_0

    return-object v14

    .line 4363
    :cond_0
    if-nez v12, :cond_1

    .line 4364
    return-object v14

    .line 4366
    :cond_1
    invoke-static {}, Landroid/os/Binder;->getCallingUid()I

    move-result v15

    .line 4373
    .local v15, "callingUid":I
    invoke-direct {v0, v12, v15, v13}, Lcom/android/server/pm/PackageManagerService;->shouldFilterApplicationLocked(Lcom/android/server/pm/PackageSetting;II)Z

    move-result v2

    if-eqz v2, :cond_2

    .line 4374
    return-object v14

    .line 4377
    :cond_2
    and-int/lit16 v2, v1, 0x2000

    if-eqz v2, :cond_3

    .line 4378
    invoke-virtual/range {p1 .. p1}, Lcom/android/server/pm/PackageSetting;->isSystem()Z

    move-result v2

    if-eqz v2, :cond_3

    .line 4379
    const/high16 v2, 0x400000

    or-int/2addr v1, v2

    move v11, v1

    .end local p2    # "flags":I
    .local v1, "flags":I
    goto :goto_0

    .line 4382
    .end local v1    # "flags":I
    .restart local p2    # "flags":I
    :cond_3
    move v11, v1

    .end local p2    # "flags":I
    .local v11, "flags":I
    :goto_0
    invoke-virtual {v12, v13}, Lcom/android/server/pm/PackageSetting;->readUserState(I)Landroid/content/pm/PackageUserState;

    move-result-object v10

    .line 4383
    .local v10, "state":Landroid/content/pm/PackageUserState;
    iget-object v9, v12, Lcom/android/server/pm/PackageSetting;->pkg:Lcom/android/server/pm/parsing/pkg/AndroidPackage;

    .line 4384
    .local v9, "p":Lcom/android/server/pm/parsing/pkg/AndroidPackage;
    if-eqz v9, :cond_8

    .line 4385
    invoke-virtual/range {p1 .. p1}, Lcom/android/server/pm/PackageSetting;->getPermissionsState()Lcom/android/server/pm/permission/PermissionsState;

    move-result-object v8

    .line 4388
    .local v8, "permissionsState":Lcom/android/server/pm/permission/PermissionsState;
    and-int/lit16 v1, v11, 0x100

    if-nez v1, :cond_4

    .line 4389
    sget-object v1, Lcom/android/server/pm/PackageManagerService;->EMPTY_INT_ARRAY:[I

    goto :goto_1

    :cond_4
    invoke-virtual {v8, v13}, Lcom/android/server/pm/permission/PermissionsState;->computeGids(I)[I

    move-result-object v1

    :goto_1
    move-object v2, v1

    .line 4391
    .local v2, "gids":[I
    invoke-interface {v9}, Lcom/android/server/pm/parsing/pkg/AndroidPackage;->getRequestedPermissions()Ljava/util/List;

    move-result-object v1

    invoke-static {v1}, Lcom/android/internal/util/ArrayUtils;->isEmpty(Ljava/util/Collection;)Z

    move-result v1

    if-eqz v1, :cond_5

    .line 4392
    invoke-static {}, Ljava/util/Collections;->emptySet()Ljava/util/Set;

    move-result-object v1

    goto :goto_2

    :cond_5
    invoke-virtual {v8, v13}, Lcom/android/server/pm/permission/PermissionsState;->getPermissions(I)Ljava/util/Set;

    move-result-object v1

    .line 4393
    .local v1, "permissions":Ljava/util/Set;, "Ljava/util/Set<Ljava/lang/String;>;"
    :goto_2
    iget-boolean v3, v10, Landroid/content/pm/PackageUserState;->instantApp:Z

    if-eqz v3, :cond_6

    .line 4394
    new-instance v3, Landroid/util/ArraySet;

    invoke-direct {v3, v1}, Landroid/util/ArraySet;-><init>(Ljava/util/Collection;)V

    move-object v1, v3

    .line 4395
    new-instance v3, Lcom/android/server/pm/-$$Lambda$PackageManagerService$jZ2E6J36ppBtzv_nL5UpgrWRpqQ;

    invoke-direct {v3, v0, v13, v12}, Lcom/android/server/pm/-$$Lambda$PackageManagerService$jZ2E6J36ppBtzv_nL5UpgrWRpqQ;-><init>(Lcom/android/server/pm/PackageManagerService;ILcom/android/server/pm/PackageSetting;)V

    invoke-interface {v1, v3}, Ljava/util/Set;->removeIf(Ljava/util/function/Predicate;)Z

    move-object/from16 v16, v1

    goto :goto_3

    .line 4393
    :cond_6
    move-object/from16 v16, v1

    .line 4410
    .end local v1    # "permissions":Ljava/util/Set;, "Ljava/util/Set<Ljava/lang/String;>;"
    .local v16, "permissions":Ljava/util/Set;, "Ljava/util/Set<Ljava/lang/String;>;"
    :goto_3
    iget-wide v4, v12, Lcom/android/server/pm/PackageSetting;->firstInstallTime:J

    iget-wide v6, v12, Lcom/android/server/pm/PackageSetting;->lastUpdateTime:J

    move-object v1, v9

    move v3, v11

    move-object/from16 v17, v8

    .end local v8    # "permissionsState":Lcom/android/server/pm/permission/PermissionsState;
    .local v17, "permissionsState":Lcom/android/server/pm/permission/PermissionsState;
    move-object/from16 v8, v16

    move-object/from16 v18, v9

    .end local v9    # "p":Lcom/android/server/pm/parsing/pkg/AndroidPackage;
    .local v18, "p":Lcom/android/server/pm/parsing/pkg/AndroidPackage;
    move-object v9, v10

    move-object/from16 v19, v10

    .end local v10    # "state":Landroid/content/pm/PackageUserState;
    .local v19, "state":Landroid/content/pm/PackageUserState;
    move/from16 v10, p3

    move/from16 v20, v11

    .end local v11    # "flags":I
    .local v20, "flags":I
    move-object/from16 v11, p1

    invoke-static/range {v1 .. v11}, Lcom/android/server/pm/parsing/PackageInfoUtils;->generate(Lcom/android/server/pm/parsing/pkg/AndroidPackage;[IIJJLjava/util/Set;Landroid/content/pm/PackageUserState;ILcom/android/server/pm/PackageSetting;)Landroid/content/pm/PackageInfo;

    move-result-object v1

    .line 4413
    .local v1, "packageInfo":Landroid/content/pm/PackageInfo;
    if-nez v1, :cond_7

    .line 4414
    return-object v14

    .line 4417
    :cond_7
    iget-object v3, v1, Landroid/content/pm/PackageInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    .line 4418
    move-object/from16 v4, v18

    .end local v18    # "p":Lcom/android/server/pm/parsing/pkg/AndroidPackage;
    .local v4, "p":Lcom/android/server/pm/parsing/pkg/AndroidPackage;
    invoke-direct {v0, v4}, Lcom/android/server/pm/PackageManagerService;->resolveExternalPackageNameLPr(Lcom/android/server/pm/parsing/pkg/AndroidPackage;)Ljava/lang/String;

    move-result-object v5

    iput-object v5, v3, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    iput-object v5, v1, Landroid/content/pm/PackageInfo;->packageName:Ljava/lang/String;

    .line 4420
    return-object v1

    .line 4421
    .end local v1    # "packageInfo":Landroid/content/pm/PackageInfo;
    .end local v2    # "gids":[I
    .end local v4    # "p":Lcom/android/server/pm/parsing/pkg/AndroidPackage;
    .end local v16    # "permissions":Ljava/util/Set;, "Ljava/util/Set<Ljava/lang/String;>;"
    .end local v17    # "permissionsState":Lcom/android/server/pm/permission/PermissionsState;
    .end local v19    # "state":Landroid/content/pm/PackageUserState;
    .end local v20    # "flags":I
    .restart local v9    # "p":Lcom/android/server/pm/parsing/pkg/AndroidPackage;
    .restart local v10    # "state":Landroid/content/pm/PackageUserState;
    .restart local v11    # "flags":I
    :cond_8
    move-object v4, v9

    move-object/from16 v19, v10

    move/from16 v20, v11

    .end local v9    # "p":Lcom/android/server/pm/parsing/pkg/AndroidPackage;
    .end local v10    # "state":Landroid/content/pm/PackageUserState;
    .end local v11    # "flags":I
    .restart local v4    # "p":Lcom/android/server/pm/parsing/pkg/AndroidPackage;
    .restart local v19    # "state":Landroid/content/pm/PackageUserState;
    .restart local v20    # "flags":I
    move/from16 v1, v20

    .end local v20    # "flags":I
    .local v1, "flags":I
    and-int/lit16 v2, v1, 0x2000

    if-eqz v2, :cond_a

    move-object/from16 v2, v19

    .end local v19    # "state":Landroid/content/pm/PackageUserState;
    .local v2, "state":Landroid/content/pm/PackageUserState;
    invoke-virtual {v2, v1}, Landroid/content/pm/PackageUserState;->isAvailable(I)Z

    move-result v3

    if-eqz v3, :cond_b

    .line 4422
    new-instance v3, Landroid/content/pm/PackageInfo;

    invoke-direct {v3}, Landroid/content/pm/PackageInfo;-><init>()V

    .line 4423
    .local v3, "pi":Landroid/content/pm/PackageInfo;
    iget-object v5, v12, Lcom/android/server/pm/PackageSetting;->name:Ljava/lang/String;

    iput-object v5, v3, Landroid/content/pm/PackageInfo;->packageName:Ljava/lang/String;

    .line 4424
    iget-wide v5, v12, Lcom/android/server/pm/PackageSetting;->versionCode:J

    invoke-virtual {v3, v5, v6}, Landroid/content/pm/PackageInfo;->setLongVersionCode(J)V

    .line 4425
    iget-object v5, v12, Lcom/android/server/pm/PackageSetting;->sharedUser:Lcom/android/server/pm/SharedUserSetting;

    if-eqz v5, :cond_9

    iget-object v5, v12, Lcom/android/server/pm/PackageSetting;->sharedUser:Lcom/android/server/pm/SharedUserSetting;

    iget-object v14, v5, Lcom/android/server/pm/SharedUserSetting;->name:Ljava/lang/String;

    :cond_9
    iput-object v14, v3, Landroid/content/pm/PackageInfo;->sharedUserId:Ljava/lang/String;

    .line 4426
    iget-wide v5, v12, Lcom/android/server/pm/PackageSetting;->firstInstallTime:J

    iput-wide v5, v3, Landroid/content/pm/PackageInfo;->firstInstallTime:J

    .line 4427
    iget-wide v5, v12, Lcom/android/server/pm/PackageSetting;->lastUpdateTime:J

    iput-wide v5, v3, Landroid/content/pm/PackageInfo;->lastUpdateTime:J

    .line 4429
    new-instance v5, Landroid/content/pm/ApplicationInfo;

    invoke-direct {v5}, Landroid/content/pm/ApplicationInfo;-><init>()V

    .line 4430
    .local v5, "ai":Landroid/content/pm/ApplicationInfo;
    iget-object v6, v12, Lcom/android/server/pm/PackageSetting;->name:Ljava/lang/String;

    iput-object v6, v5, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    .line 4431
    iget v6, v12, Lcom/android/server/pm/PackageSetting;->appId:I

    invoke-static {v13, v6}, Landroid/os/UserHandle;->getUid(II)I

    move-result v6

    iput v6, v5, Landroid/content/pm/ApplicationInfo;->uid:I

    .line 4432
    iget-object v6, v12, Lcom/android/server/pm/PackageSetting;->primaryCpuAbiString:Ljava/lang/String;

    iput-object v6, v5, Landroid/content/pm/ApplicationInfo;->primaryCpuAbi:Ljava/lang/String;

    .line 4433
    iget-object v6, v12, Lcom/android/server/pm/PackageSetting;->secondaryCpuAbiString:Ljava/lang/String;

    iput-object v6, v5, Landroid/content/pm/ApplicationInfo;->secondaryCpuAbi:Ljava/lang/String;

    .line 4434
    iget-wide v6, v12, Lcom/android/server/pm/PackageSetting;->versionCode:J

    invoke-virtual {v5, v6, v7}, Landroid/content/pm/ApplicationInfo;->setVersionCode(J)V

    .line 4435
    iget v6, v12, Lcom/android/server/pm/PackageSetting;->pkgFlags:I

    iput v6, v5, Landroid/content/pm/ApplicationInfo;->flags:I

    .line 4436
    iget v6, v12, Lcom/android/server/pm/PackageSetting;->pkgPrivateFlags:I

    iput v6, v5, Landroid/content/pm/ApplicationInfo;->privateFlags:I

    .line 4437
    invoke-static {v5, v1, v2, v13}, Landroid/content/pm/PackageParser;->generateApplicationInfo(Landroid/content/pm/ApplicationInfo;ILandroid/content/pm/PackageUserState;I)Landroid/content/pm/ApplicationInfo;

    move-result-object v6

    iput-object v6, v3, Landroid/content/pm/PackageInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    .line 4441
    return-object v3

    .line 4421
    .end local v2    # "state":Landroid/content/pm/PackageUserState;
    .end local v3    # "pi":Landroid/content/pm/PackageInfo;
    .end local v5    # "ai":Landroid/content/pm/ApplicationInfo;
    .restart local v19    # "state":Landroid/content/pm/PackageUserState;
    :cond_a
    move-object/from16 v2, v19

    .line 4443
    .end local v19    # "state":Landroid/content/pm/PackageUserState;
    .restart local v2    # "state":Landroid/content/pm/PackageUserState;
    :cond_b
    return-object v14
.end method
