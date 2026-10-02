using System.IO;
using System.Windows;
using VoyahIran.Installer.Core;

namespace VoyahIran.Installer;
public sealed partial class MainViewModel
{
    public ActionCommand CanTraceCommand { get; private set; } = null!;
    public ActionCommand CanMarkCommand { get; private set; } = null!;
    CanTraceCapture? canTrace;
    string? canReport;
    bool canChanging;
    public bool CanOperationBusy => canChanging;
    public bool HasCanReport => canTrace!=null || canReport!=null;
    bool CanBusy => canChanging || canTrace!=null;
    public string CanTraceTitle => canTrace!=null ? "پایان شنود CAN (آزمایشی)" : canReport!=null ? "ذخیره شنود CAN" : "شروع شنود CAN (آزمایشی)";
    public string CanTraceStatus => canTrace == null ? "" : canTrace.ReceivedBytes == 0
        ? "شنود native متصل است؛ هنوز دریافت داده تأیید نشده است."
        : $"دادهٔ دریافتی CAN: {canTrace.ReceivedBytes:N0} بایت";
    void InitCanTrace() {
        timer.Tick += (_, _) => { Notify(nameof(CanTraceStatus)); CanMarkCommand.Refresh(); };
        CanTraceCommand=new ActionCommand(async()=>await ToggleCanAsync(),()=>!canChanging && (HasCanReport || (!Busy && !starting && !captureChanging && bundle!=null)));
        CanMarkCommand=new ActionCommand(async()=> {
            if(canTrace==null) return;
            try { await canTrace.MarkAsync(); }
            catch(Exception e) { MessageBox.Show(e.Message,"علامت اتوپارک"); }
        },()=>!canChanging && canTrace?.Running==true);
    }
    void CanChanged() { Notify(nameof(CanTraceTitle)); Notify(nameof(CanTraceStatus)); CanTraceCommand.Refresh(); CanMarkCommand.Refresh(); PagesChanged(); }
    async Task ToggleCanAsync() {
        if(HasCanReport) { await FinishCanAsync(); return; }
        if(bundle==null || adb==null || canChanging) return;
        canChanging=true; CanChanged();
        try {
            while(scanning) await Task.Delay(50);
            await ScanAsync();
            if(device==null) throw new IOException("ابتدا خودرو را متصل کنید.");
            var folder=Path.Combine(dataRoot,"Reports"); Directory.CreateDirectory(folder);
            canReport=Path.Combine(folder,"can-"+DateTime.Now.ToString("yyyy-MM-dd_HH-mm-ss")+"-"+Guid.NewGuid().ToString("N")+".txt");
            canTrace=await CanTraceCapture.StartAsync(adb,bundle.File("adb.exe"),device.Serial,bundle.File("roa/frida-inject"),canReport);
            _=WatchCanAsync(canTrace);
        } catch(Exception e) {
            log.Write(e.ToString());
            if(canReport!=null && !File.Exists(canReport)) canReport=null;
            MessageBox.Show("شروع شنود CAN ناموفق بود.\n"+e.Message,"شنود CAN");
        } finally { canChanging=false; CanChanged(); }
    }
    async Task WatchCanAsync(CanTraceCapture session) {
        try { await session.Completion; } catch(Exception e) { log.Write(e.Message); }
        if(canTrace!=session || canChanging || disposed) return;
        MessageBox.Show("شنود CAN قطع شد. گزارش تا این لحظه حفظ شده است.","شنود CAN");
        await FinishCanAsync();
    }
    public async Task FinishCanAsync() {
        if(canChanging) return;
        canChanging=true; CanChanged();
        try {
            if(canTrace is {} session) { canTrace=null; await session.StopAsync(); }
            if(canReport==null) return;
            var save=new Microsoft.Win32.SaveFileDialog {Title="ذخیره شنود CAN",Filter="CAN log (*.txt)|*.txt",FileName=$"can-log-{DateTime.Now:yyyy-MM-dd_HH-mm-ss}.txt",AddExtension=true,DefaultExt=".txt"};
            if(save.ShowDialog(Application.Current.MainWindow)!=true) return;
            File.Copy(canReport,save.FileName,true);
            canReport=null;
            MessageBox.Show("گزارش CAN ذخیره شد.","شنود CAN");
        } catch(Exception e) { MessageBox.Show("گزارش موقت حفظ شده است.\n"+e.Message,"شنود CAN"); }
        finally { canChanging=false; CanChanged(); }
    }
}
