using System.Globalization;

namespace VoyahIran.Installer.Core;

public static class EngineeringPassword
{
    public static DateTimeOffset ChinaTime(DateTimeOffset instant) => instant.ToOffset(TimeSpan.FromHours(8));

    public static string ForInstant(DateTimeOffset instant)
    {
        var date = ChinaTime(instant);
        int y = date.Year, m = date.Month, d = date.Day;
        // OEM ValidationView.getDynamicPassword: append each sum, without carrying.
        return string.Concat(new[] { y / 1000 + m / 10, y % 1000 / 100 + m % 10,
            y % 100 / 10 + d / 10, y % 10 + d % 10 }.Select(n => n.ToString(CultureInfo.InvariantCulture)));
    }
}
