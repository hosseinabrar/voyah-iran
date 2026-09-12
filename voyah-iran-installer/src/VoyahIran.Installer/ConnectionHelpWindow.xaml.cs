using System.Windows;
using System.Windows.Threading;
using System.Windows.Interop;
using System.Windows.Media;
using VoyahIran.Installer.Core;

namespace VoyahIran.Installer;

public partial class ConnectionHelpWindow : Window
{
    private readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(1) };
    public ConnectionHelpWindow()
    {
        InitializeComponent();
        UpdatePassword();
        timer.Tick += (_, _) => UpdatePassword();
        Loaded += (_, _) => timer.Start();
        Closed += (_, _) => timer.Stop();
    }
    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);
        // Scope the rendering fallback to this window: some display drivers leave the
        // initial ScrollViewer surface blank until a scroll forces it to repaint.
        if (PresentationSource.FromVisual(this) is HwndSource source && source.CompositionTarget is { } target)
            target.RenderMode = RenderMode.SoftwareOnly;
    }
    protected override void OnContentRendered(EventArgs e)
    {
        base.OnContentRendered(e);
        GuideScroll.UpdateLayout();
        GuideScroll.ScrollToTop();
        GuideScroll.InvalidateVisual();
    }
    private void UpdatePassword()
    {
        var now = DateTimeOffset.UtcNow;
        var password = EngineeringPassword.ForInstant(now);
        if (PasswordText.Text != password) PasswordText.Text = password;
    }
    private void CloseHelp(object sender, RoutedEventArgs e) => Close();
}
