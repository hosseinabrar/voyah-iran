namespace VoyahIran.Subscriptions;
public sealed class SubscriptionOptions {
 public string AdminKeyPath { get; set; } = "secrets/admin-token.txt";
 public string ConnectionString { get; set; } = "Host=localhost;Database=voyahiran;Username=voyahiran";
 public string DatabasePasswordPath { get; set; } = "";
 public string PrivateKeyPath { get; set; } = "secrets/private.pem";
 public string Issuer { get; set; } = "voyahiran.ir";
 public string Audience { get; set; } = "com.abrar.voyahiran";
 public int TermDays { get; set; } = 30;
 public int RenewWithinDays { get; set; } = 3;
 public void Validate() {
  if (TermDays is < 1 or > 365 || RenewWithinDays < 0 || RenewWithinDays >= TermDays) throw new InvalidOperationException("Invalid subscription term or renewal window");
  if(string.IsNullOrWhiteSpace(Issuer)||string.IsNullOrWhiteSpace(Audience)) throw new InvalidOperationException("Issuer and audience are required");
 }
}
