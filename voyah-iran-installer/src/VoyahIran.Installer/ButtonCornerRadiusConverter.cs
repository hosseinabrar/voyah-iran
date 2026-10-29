using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace VoyahIran.Installer;

public sealed class ButtonCornerRadiusConverter : IMultiValueConverter
{
    public object Convert(object[] values, Type targetType, object parameter, CultureInfo culture)
    {
        // Clamp both axes together: oversized WPF radii can produce elliptical
        // corners rather than the circular capsule used by the macOS client.
        if (values.Length < 2 || values[0] is not double width || values[1] is not double height
            || !double.IsFinite(width) || !double.IsFinite(height))
            return new CornerRadius(0);
        return new CornerRadius(Math.Max(0, Math.Min(35, Math.Min(width, height) / 2)));
    }

    public object[] ConvertBack(object value, Type[] targetTypes, object parameter, CultureInfo culture)
        => throw new NotSupportedException();
}
