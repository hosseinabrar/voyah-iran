using System.Text.RegularExpressions;
namespace VoyahIran.Subscriptions;
public static partial class Vin {
 [GeneratedRegex("^[A-HJ-NPR-Z0-9]{17}$", RegexOptions.CultureInvariant)] private static partial Regex Format();
 public static string? Normalize(string? value) {
  if (value is null || value.Length > 32) return null;
  var vin=value.Trim().ToUpperInvariant();
  return Format().IsMatch(vin) ? vin : null;
 }
}
