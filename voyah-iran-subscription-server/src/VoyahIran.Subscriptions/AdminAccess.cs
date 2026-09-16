using System.Security.Cryptography;
using System.Text;
namespace VoyahIran.Subscriptions;
public sealed class AdminAccess {
 private readonly byte[] digest;
 public AdminAccess(SubscriptionOptions options) {
  var token=File.ReadAllText(options.AdminKeyPath).Trim();
  if(token.Length<32)throw new InvalidOperationException("Admin token must contain at least 32 characters");
  digest=SHA256.HashData(Encoding.UTF8.GetBytes(token));
 }
 public bool Allows(HttpContext context) {
  var header=context.Request.Headers.Authorization.ToString();
  return header.StartsWith("Bearer ",StringComparison.Ordinal)&&header.Length<=512&&CryptographicOperations.FixedTimeEquals(digest,SHA256.HashData(Encoding.UTF8.GetBytes(header[7..])));
 }
}
