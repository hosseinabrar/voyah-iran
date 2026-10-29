using System.Windows;
namespace VoyahIran.Installer;
public partial class App : Application
{
    private Mutex? instance;
    private bool ownsMutex;
    protected override void OnStartup(StartupEventArgs e)
    {
        instance = new Mutex(true, @"Local\VoyahIran.Installer", out ownsMutex);
        if (!ownsMutex)
        {
            MessageBox.Show("نصاب وویا ایران از قبل باز است. از همان پنجره استفاده کنید.", "وویا ایران", MessageBoxButton.OK, MessageBoxImage.Information);
            Shutdown(); return;
        }
        InstallerTheme.Initialize();
        base.OnStartup(e);
    }
    protected override void OnExit(ExitEventArgs e)
    {
        InstallerTheme.Stop();
        if (ownsMutex) instance?.ReleaseMutex();
        instance?.Dispose();
        base.OnExit(e);
    }
}
