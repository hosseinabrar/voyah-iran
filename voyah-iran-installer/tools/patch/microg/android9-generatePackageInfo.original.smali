.method private generatePackageInfo(Lcom/android/server/pm/PackageSetting;II)Landroid/content/pm/PackageInfo;
    .registers 24
    .param p1, "ps"    # Lcom/android/server/pm/PackageSetting;
    .param p2, "flags"    # I
    .param p3, "userId"    # I

    move-object/from16 v0, p0

    move-object/from16 v1, p1

    move/from16 v2, p2

    .line 3962
    move/from16 v12, p3

    sget-object v3, Lcom/android/server/pm/PackageManagerService;->sUserManager:Lcom/android/server/pm/UserManagerService;

    invoke-virtual {v3, v12}, Lcom/android/server/pm/UserManagerService;->exists(I)Z

    move-result v3

    const/4 v13, 0x0

    if-nez v3, :cond_0

    return-object v13

    .line 3963
    :cond_0
    if-nez v1, :cond_1

    .line 3964
    return-object v13

    .line 3966
    :cond_1
    invoke-static {}, Landroid/os/Binder;->getCallingUid()I

    move-result v14

    .line 3973
    .local v14, "callingUid":I
    invoke-direct {v0, v1, v14, v12}, Lcom/android/server/pm/PackageManagerService;->filterAppAccessLPr(Lcom/android/server/pm/PackageSetting;II)Z

    move-result v3

    if-eqz v3, :cond_2

    .line 3974
    return-object v13

    .line 3977
    :cond_2
    and-int/lit16 v3, v2, 0x2000

    if-eqz v3, :cond_3

    .line 3978
    invoke-virtual/range {p1 .. p1}, Lcom/android/server/pm/PackageSetting;->isSystem()Z

    move-result v3

    if-eqz v3, :cond_3

    .line 3979
    const/high16 v3, 0x400000

    or-int/2addr v2, v3

    .line 3982
    .end local p2    # "flags":I
    .local v15, "flags":I
    :cond_3
    move v15, v2

    invoke-virtual {v1, v12}, Lcom/android/server/pm/PackageSetting;->readUserState(I)Landroid/content/pm/PackageUserState;

    move-result-object v11

    .line 3983
    .local v11, "state":Landroid/content/pm/PackageUserState;
    iget-object v10, v1, Lcom/android/server/pm/PackageSetting;->pkg:Landroid/content/pm/PackageParser$Package;

    .line 3984
    .local v10, "p":Landroid/content/pm/PackageParser$Package;
    if-eqz v10, :cond_8

    .line 3985
    invoke-virtual/range {p1 .. p1}, Lcom/android/server/pm/PackageSetting;->getPermissionsState()Lcom/android/server/pm/permission/PermissionsState;

    move-result-object v9

    .line 3988
    .local v9, "permissionsState":Lcom/android/server/pm/permission/PermissionsState;
    and-int/lit16 v2, v15, 0x100

    if-nez v2, :cond_4

    .line 3989
    sget-object v2, Lcom/android/server/pm/PackageManagerService;->EMPTY_INT_ARRAY:[I

    :goto_0
    move-object v3, v2

    goto :goto_1

    :cond_4
    invoke-virtual {v9, v12}, Lcom/android/server/pm/permission/PermissionsState;->computeGids(I)[I

    move-result-object v2

    goto :goto_0

    .line 3991
    .local v3, "gids":[I
    :goto_1
    iget-object v2, v10, Landroid/content/pm/PackageParser$Package;->requestedPermissions:Ljava/util/ArrayList;

    invoke-static {v2}, Lcom/android/internal/util/ArrayUtils;->isEmpty(Ljava/util/Collection;)Z

    move-result v2

    if-eqz v2, :cond_5

    .line 3992
    invoke-static {}, Ljava/util/Collections;->emptySet()Ljava/util/Set;

    move-result-object v2

    goto :goto_2

    :cond_5
    invoke-virtual {v9, v12}, Lcom/android/server/pm/permission/PermissionsState;->getPermissions(I)Ljava/util/Set;

    move-result-object v2

    .line 3993
    .local v2, "permissions":Ljava/util/Set;, "Ljava/util/Set<Ljava/lang/String;>;"
    :goto_2
    iget-boolean v4, v11, Landroid/content/pm/PackageUserState;->instantApp:Z

    if-eqz v4, :cond_6

    .line 3994
    new-instance v4, Landroid/util/ArraySet;

    invoke-direct {v4, v2}, Landroid/util/ArraySet;-><init>(Ljava/util/Collection;)V

    move-object v2, v4

    .line 3995
    new-instance v4, Lcom/android/server/pm/-$$Lambda$PackageManagerService$O3zXDb2FgkdfYNqsCHEFfFF-nEA;

    invoke-direct {v4, v0, v12, v1}, Lcom/android/server/pm/-$$Lambda$PackageManagerService$O3zXDb2FgkdfYNqsCHEFfFF-nEA;-><init>(Lcom/android/server/pm/PackageManagerService;ILcom/android/server/pm/PackageSetting;)V

    invoke-interface {v2, v4}, Ljava/util/Set;->removeIf(Ljava/util/function/Predicate;)Z

    .line 4010
    .end local v2    # "permissions":Ljava/util/Set;, "Ljava/util/Set<Ljava/lang/String;>;"
    .local v16, "permissions":Ljava/util/Set;, "Ljava/util/Set<Ljava/lang/String;>;"
    :cond_6
    move-object/from16 v16, v2

    iget-wide v5, v1, Lcom/android/server/pm/PackageSetting;->firstInstallTime:J

    iget-wide v7, v1, Lcom/android/server/pm/PackageSetting;->lastUpdateTime:J

    move-object v2, v10

    move v4, v15

    move-object/from16 v17, v9

    move-object/from16 v9, v16

    .end local v9    # "permissionsState":Lcom/android/server/pm/permission/PermissionsState;
    .local v17, "permissionsState":Lcom/android/server/pm/permission/PermissionsState;
    move-object/from16 v18, v10

    move-object v10, v11

    .end local v10    # "p":Landroid/content/pm/PackageParser$Package;
    .local v18, "p":Landroid/content/pm/PackageParser$Package;
    move-object/from16 v19, v11

    move v11, v12

    .end local v11    # "state":Landroid/content/pm/PackageUserState;
    .local v19, "state":Landroid/content/pm/PackageUserState;
    invoke-static/range {v2 .. v11}, Landroid/content/pm/PackageParser;->generatePackageInfo(Landroid/content/pm/PackageParser$Package;[IIJJLjava/util/Set;Landroid/content/pm/PackageUserState;I)Landroid/content/pm/PackageInfo;

    move-result-object v2

    .line 4013
    .local v2, "packageInfo":Landroid/content/pm/PackageInfo;
    if-nez v2, :cond_7

    .line 4014
    return-object v13

    .line 4017
    :cond_7
    iget-object v4, v2, Landroid/content/pm/PackageInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    .line 4018
    move-object/from16 v5, v18

    invoke-direct {v0, v5}, Lcom/android/server/pm/PackageManagerService;->resolveExternalPackageNameLPr(Landroid/content/pm/PackageParser$Package;)Ljava/lang/String;

    move-result-object v6

    .end local v18    # "p":Landroid/content/pm/PackageParser$Package;
    .local v5, "p":Landroid/content/pm/PackageParser$Package;
    iput-object v6, v4, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    iput-object v6, v2, Landroid/content/pm/PackageInfo;->packageName:Ljava/lang/String;

    .line 4020
    return-object v2

    .line 4021
    .end local v2    # "packageInfo":Landroid/content/pm/PackageInfo;
    .end local v3    # "gids":[I
    .end local v5    # "p":Landroid/content/pm/PackageParser$Package;
    .end local v16    # "permissions":Ljava/util/Set;, "Ljava/util/Set<Ljava/lang/String;>;"
    .end local v17    # "permissionsState":Lcom/android/server/pm/permission/PermissionsState;
    .end local v19    # "state":Landroid/content/pm/PackageUserState;
    .restart local v10    # "p":Landroid/content/pm/PackageParser$Package;
    .restart local v11    # "state":Landroid/content/pm/PackageUserState;
    :cond_8
    move-object v5, v10

    move-object/from16 v19, v11

    .end local v10    # "p":Landroid/content/pm/PackageParser$Package;
    .end local v11    # "state":Landroid/content/pm/PackageUserState;
    .restart local v5    # "p":Landroid/content/pm/PackageParser$Package;
    .restart local v19    # "state":Landroid/content/pm/PackageUserState;
    and-int/lit16 v2, v15, 0x2000

    if-eqz v2, :cond_a

    move-object/from16 v2, v19

    invoke-virtual {v2, v15}, Landroid/content/pm/PackageUserState;->isAvailable(I)Z

    move-result v3

    .end local v19    # "state":Landroid/content/pm/PackageUserState;
    .local v2, "state":Landroid/content/pm/PackageUserState;
    if-eqz v3, :cond_b

    .line 4022
    new-instance v3, Landroid/content/pm/PackageInfo;

    invoke-direct {v3}, Landroid/content/pm/PackageInfo;-><init>()V

    .line 4023
    .local v3, "pi":Landroid/content/pm/PackageInfo;
    iget-object v4, v1, Lcom/android/server/pm/PackageSetting;->name:Ljava/lang/String;

    iput-object v4, v3, Landroid/content/pm/PackageInfo;->packageName:Ljava/lang/String;

    .line 4024
    iget-wide v6, v1, Lcom/android/server/pm/PackageSetting;->versionCode:J

    invoke-virtual {v3, v6, v7}, Landroid/content/pm/PackageInfo;->setLongVersionCode(J)V

    .line 4025
    iget-object v4, v1, Lcom/android/server/pm/PackageSetting;->sharedUser:Lcom/android/server/pm/SharedUserSetting;

    if-eqz v4, :cond_9

    iget-object v4, v1, Lcom/android/server/pm/PackageSetting;->sharedUser:Lcom/android/server/pm/SharedUserSetting;

    iget-object v13, v4, Lcom/android/server/pm/SharedUserSetting;->name:Ljava/lang/String;

    nop

    :cond_9
    iput-object v13, v3, Landroid/content/pm/PackageInfo;->sharedUserId:Ljava/lang/String;

    .line 4026
    iget-wide v6, v1, Lcom/android/server/pm/PackageSetting;->firstInstallTime:J

    iput-wide v6, v3, Landroid/content/pm/PackageInfo;->firstInstallTime:J

    .line 4027
    iget-wide v6, v1, Lcom/android/server/pm/PackageSetting;->lastUpdateTime:J

    iput-wide v6, v3, Landroid/content/pm/PackageInfo;->lastUpdateTime:J

    .line 4029
    new-instance v4, Landroid/content/pm/ApplicationInfo;

    invoke-direct {v4}, Landroid/content/pm/ApplicationInfo;-><init>()V

    .line 4030
    .local v4, "ai":Landroid/content/pm/ApplicationInfo;
    iget-object v6, v1, Lcom/android/server/pm/PackageSetting;->name:Ljava/lang/String;

    iput-object v6, v4, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    .line 4031
    iget v6, v1, Lcom/android/server/pm/PackageSetting;->appId:I

    invoke-static {v12, v6}, Landroid/os/UserHandle;->getUid(II)I

    move-result v6

    iput v6, v4, Landroid/content/pm/ApplicationInfo;->uid:I

    .line 4032
    iget-object v6, v1, Lcom/android/server/pm/PackageSetting;->primaryCpuAbiString:Ljava/lang/String;

    iput-object v6, v4, Landroid/content/pm/ApplicationInfo;->primaryCpuAbi:Ljava/lang/String;

    .line 4033
    iget-object v6, v1, Lcom/android/server/pm/PackageSetting;->secondaryCpuAbiString:Ljava/lang/String;

    iput-object v6, v4, Landroid/content/pm/ApplicationInfo;->secondaryCpuAbi:Ljava/lang/String;

    .line 4034
    iget-wide v6, v1, Lcom/android/server/pm/PackageSetting;->versionCode:J

    invoke-virtual {v4, v6, v7}, Landroid/content/pm/ApplicationInfo;->setVersionCode(J)V

    .line 4035
    iget v6, v1, Lcom/android/server/pm/PackageSetting;->pkgFlags:I

    iput v6, v4, Landroid/content/pm/ApplicationInfo;->flags:I

    .line 4036
    iget v6, v1, Lcom/android/server/pm/PackageSetting;->pkgPrivateFlags:I

    iput v6, v4, Landroid/content/pm/ApplicationInfo;->privateFlags:I

    .line 4037
    invoke-static {v4, v15, v2, v12}, Landroid/content/pm/PackageParser;->generateApplicationInfo(Landroid/content/pm/ApplicationInfo;ILandroid/content/pm/PackageUserState;I)Landroid/content/pm/ApplicationInfo;

    move-result-object v6

    iput-object v6, v3, Landroid/content/pm/PackageInfo;->applicationInfo:Landroid/content/pm/ApplicationInfo;

    .line 4041
    return-object v3

    .line 4043
    .end local v2    # "state":Landroid/content/pm/PackageUserState;
    .end local v3    # "pi":Landroid/content/pm/PackageInfo;
    .end local v4    # "ai":Landroid/content/pm/ApplicationInfo;
    .restart local v19    # "state":Landroid/content/pm/PackageUserState;
    :cond_a
    move-object/from16 v2, v19

    .end local v19    # "state":Landroid/content/pm/PackageUserState;
    .restart local v2    # "state":Landroid/content/pm/PackageUserState;
    :cond_b
    return-object v13
.end method
