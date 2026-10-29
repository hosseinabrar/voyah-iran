using System.ComponentModel;
using System.Windows;
namespace VoyahIran.Installer;
public partial class MainWindow : Window
{
    private readonly MainViewModel model = new();
    public MainWindow()
    {
        InitializeComponent(); DataContext = model;
        ThemeButton.Content = InstallerTheme.Caption;
        model.StepProgressed += index => Dispatcher.BeginInvoke(
            System.Windows.Threading.DispatcherPriority.Loaded, new Action(() =>
            {
                if (!IsLoaded) return;
                StepsList.UpdateLayout();
                if (StepsList.ItemContainerGenerator.ContainerFromIndex(index) is FrameworkElement row)
                    row.BringIntoView();
            }));
        model.LogLine += line => Dispatcher.BeginInvoke(new Action(() =>
        {
            var following = LogBox.SelectionLength == 0 && LogBox.VerticalOffset >= LogBox.ExtentHeight - LogBox.ViewportHeight - 1;
            LogBox.AppendText(line);
            if (following) LogBox.ScrollToEnd();
        }));
    }
    private void ChangeTheme(object sender, RoutedEventArgs e)
    {
        InstallerTheme.Cycle();
        ThemeButton.Content = InstallerTheme.Caption;
    }
    private async void WindowLoaded(object sender, RoutedEventArgs e)
    {
        if (!DesignerProperties.GetIsInDesignMode(this)) await model.InitializeAsync();
    }
    private async void WindowClosing(object? sender, CancelEventArgs e)
    {
        if (model.Busy)
        {
            e.Cancel = true;
            MessageBox.Show(this, "نصب در حال انجام است. تا پایان عملیات، برنامه را باز و کابل را متصل نگه دارید.", "نصب وویا ایران", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        else if (model.CanOperationBusy) { e.Cancel = true; }
        else if (model.HasCanReport) { e.Cancel = true; await model.FinishCanAsync(); if (!model.HasCanReport) Close(); }
        else if (model.ReportOperationBusy) { e.Cancel = true; }
        else if (model.HasReportToSave) {
            e.Cancel = true;
            await model.FinishCaptureAsync();
            if (!model.HasReportToSave && !model.ReportOperationBusy) Close();
        }
        else model.Dispose();
    }
}
