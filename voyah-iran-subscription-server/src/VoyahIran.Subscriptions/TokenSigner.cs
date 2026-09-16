using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
namespace VoyahIran.Subscriptions;
public sealed class TokenSigner : IDisposable {
 private readonly RSA rsa=RSA.Create();
 private readonly object gate=new();
 private readonly SubscriptionOptions options;
 public string KeyId { get; }
 public string PublicKeyPem { get; }
 public TokenSigner(SubscriptionOptions options) {
  this.options=options;
  rsa.ImportFromPem(File.ReadAllText(options.PrivateKeyPath));
  if(rsa.KeySize<2048)throw new InvalidOperationException("RSA key must be at least 2048 bits");
  _=rsa.ExportParameters(true); // Fail at startup if only a public key was supplied.
  PublicKeyPem=rsa.ExportSubjectPublicKeyInfoPem();
  KeyId=Convert.ToHexString(SHA256.HashData(rsa.ExportSubjectPublicKeyInfo()))[..16].ToLowerInvariant();
 }
 public string Sign(SubscriptionGrant grant, DateTimeOffset now) {
  var header=Encode(JsonSerializer.SerializeToUtf8Bytes(new {alg="RS256",typ="JWT",kid=KeyId}));
  var payload=Encode(JsonSerializer.SerializeToUtf8Bytes(new {v=1,iss=options.Issuer,aud=options.Audience,sub=grant.Vin,iat=now.ToUnixTimeSeconds(),exp=grant.ExpiresAt}));
  var input=header+"."+payload;
  byte[] signature;lock(gate) signature=rsa.SignData(Encoding.ASCII.GetBytes(input),HashAlgorithmName.SHA256,RSASignaturePadding.Pkcs1);
  return input+"."+Encode(signature);
 }
 private static string Encode(byte[] data)=>Convert.ToBase64String(data).TrimEnd('=').Replace('+','-').Replace('/','_');
 public void Dispose()=>rsa.Dispose();
}
