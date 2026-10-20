.class Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;
.super Landroid/database/ContentObserver;
.source "VoyahIranDisplayPolicy.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->run()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/android/server/wm/VoyahIranDisplayPolicy$1;

.field final synthetic val$resolver:Landroid/content/ContentResolver;


# direct methods
.method constructor <init>(Lcom/android/server/wm/VoyahIranDisplayPolicy$1;Landroid/os/Handler;Landroid/content/ContentResolver;)V
    .registers 4

    .line 77
    iput-object p1, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;->this$0:Lcom/android/server/wm/VoyahIranDisplayPolicy$1;

    iput-object p3, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;->val$resolver:Landroid/content/ContentResolver;

    invoke-direct {p0, p2}, Landroid/database/ContentObserver;-><init>(Landroid/os/Handler;)V

    return-void
.end method


# virtual methods
.method public onChange(Z)V
    .registers 3

    .line 78
    iget-object p1, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;->this$0:Lcom/android/server/wm/VoyahIranDisplayPolicy$1;

    iget-object p1, p1, Lcom/android/server/wm/VoyahIranDisplayPolicy$1;->val$service:Lcom/android/server/wm/ActivityTaskManagerService;

    iget-object v0, p0, Lcom/android/server/wm/VoyahIranDisplayPolicy$1$1;->val$resolver:Landroid/content/ContentResolver;

    invoke-static {p1, v0}, Lcom/android/server/wm/VoyahIranDisplayPolicy;->access$500(Lcom/android/server/wm/ActivityTaskManagerService;Landroid/content/ContentResolver;)V

    return-void
.end method
