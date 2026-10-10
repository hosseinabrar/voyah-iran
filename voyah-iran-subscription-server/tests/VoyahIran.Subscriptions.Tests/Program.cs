using VoyahIran.Subscriptions;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Npgsql;
var directory=Path.Combine(Path.GetTempPath(),"voyah-subscriptions-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(directory);
int count=0;
void Check(bool condition,string name){if(!condition)throw new Exception(name);count++;Console.WriteLine("PASS "+name);}
try {
 const string vin="L123456789012345A";
 Check(Vin.Normalize("  l123456789012345a ")==vin,"VIN normalization");
 foreach(var invalid in new[]{"", "abc", "L123456789012345I", "L123456789012345O", "L123456789012345Q", "L123456789012345;"}) Check(Vin.Normalize(invalid)==null,"Reject invalid VIN");
 using var key=RSA.Create(2048);
 var keyPath=Path.Combine(directory,"key.pem");File.WriteAllText(keyPath,key.ExportPkcs8PrivateKeyPem());
 var options=new SubscriptionOptions{ConnectionString=Environment.GetEnvironmentVariable("TEST_DATABASE")??"",PrivateKeyPath=keyPath};options.Validate();
 var now=DateTimeOffset.Parse("2026-09-15T12:00:00Z");
 var first=RenewalPolicy.Decide(vin,null,now.ToUnixTimeSeconds(),options);
 Check(first.ExpiresAt==now.AddDays(30).ToUnixTimeSeconds(),"Policy: registration");
 Check(RenewalPolicy.Decide(vin,first.ExpiresAt,now.AddDays(27).ToUnixTimeSeconds(),options).Status=="unchanged","Policy: exactly 3 days");
 Check(RenewalPolicy.Decide(vin,first.ExpiresAt,now.AddDays(27).AddSeconds(1).ToUnixTimeSeconds(),options).ExpiresAt==first.ExpiresAt+30*86400,"Policy: below 3 days");
 Check(RenewalPolicy.Decide(vin,first.ExpiresAt,now.AddDays(90).ToUnixTimeSeconds(),options).ExpiresAt==now.AddDays(120).ToUnixTimeSeconds(),"Policy: expired");
 if(!string.IsNullOrEmpty(options.ConnectionString)) {
 MigrationTests.Run(options.ConnectionString,Check);
 var store=new SubscriptionStore(options);
 var dbFirst=store.RegisterOrRenew(vin,now);
 Check(dbFirst.Status=="registered"&&dbFirst.ExpiresAt==now.AddDays(30).ToUnixTimeSeconds(),"Initial 30 days");
 Check(store.RegisterOrRenew(vin,now).ExpiresAt==dbFirst.ExpiresAt,"Duplicate request is idempotent");
 Check(new SubscriptionStore(options).RegisterOrRenew(vin,now.AddDays(1)).ExpiresAt==dbFirst.ExpiresAt,"Database survives restart");
 Check(store.RegisterOrRenew(vin,now.AddDays(27)).Status=="unchanged","Exactly three days does not renew");
 var renew=store.RegisterOrRenew(vin,now.AddDays(27).AddSeconds(1));
 Check(renew.Status=="renewed"&&renew.ExpiresAt==dbFirst.ExpiresAt+30*86400,"Below three days adds 30 days to expiry");
 Check(store.RegisterOrRenew(vin,now.AddDays(28)).ExpiresAt==renew.ExpiresAt,"Repeated renewal does not extend again");
 var expired=store.RegisterOrRenew(vin,now.AddDays(90));
 Check(expired.ExpiresAt==now.AddDays(120).ToUnixTimeSeconds(),"Expired subscription starts from current time");
 var concurrent=Enumerable.Range(0,24).Select(_=>Task.Run(()=>store.RegisterOrRenew("L223456789012345A",now))).ToArray();await Task.WhenAll(concurrent);
 Check(concurrent.Select(t=>t.Result.ExpiresAt).Distinct().Count()==1 && concurrent.Count(t=>t.Result.Status=="registered")==1,"Concurrent initial requests create one grant");
 var concurrentRenew=Enumerable.Range(0,24).Select(_=>Task.Run(()=>store.RegisterOrRenew("L223456789012345A",now.AddDays(29)))).ToArray();await Task.WhenAll(concurrentRenew);
 Check(concurrentRenew.Select(t=>t.Result.ExpiresAt).Distinct().Count()==1&&concurrentRenew.Count(t=>t.Result.Status=="renewed")==1,"Concurrent renewal extends once");
 var info=store.Get(vin)!;
 Check(info.FirstRegisteredAt==now&&info.LastRenewedAt==now.AddDays(90)&&info.RenewalCount==2,"Admin registration and renewal metadata");
 store.SetBlocked(vin,true,now.AddDays(91),"test");
 try{store.RegisterOrRenew(vin,now.AddDays(91));throw new Exception("Blocked VIN accepted");}catch(SubscriptionBlockedException){Check(true,"Blocked VIN cannot get token");}
 Check(store.Get(vin)!.RenewalCount==2,"Blocking does not count as renewal");
 store.SetBlocked(vin,false,now.AddDays(92),"");Check(store.RegisterOrRenew(vin,now.AddDays(92)).Status=="unchanged","Unblock preserves expiry");
 store.SetBlocked("L323456789012345A",true,now,"preblock");Check(store.Get("L323456789012345A")!.FirstRegisteredAt is null,"Can block before first registration");
 } else Console.WriteLine("SKIP PostgreSQL integration: set TEST_DATABASE to a disposable empty database");
 using var signer=new TokenSigner(options);var token=signer.Sign(first,now);var parts=token.Split('.');
 byte[] Decode(string s)=>Convert.FromBase64String(s.Replace('-','+').Replace('_','/').PadRight((s.Length+3)/4*4,'='));
 Check(key.VerifyData(Encoding.ASCII.GetBytes(parts[0]+"."+parts[1]),Decode(parts[2]),HashAlgorithmName.SHA256,RSASignaturePadding.Pkcs1),"RS256 signature verifies");
 Check(!key.VerifyData(Encoding.ASCII.GetBytes(parts[0]+"."+parts[1]+"A"),Decode(parts[2]),HashAlgorithmName.SHA256,RSASignaturePadding.Pkcs1),"Tampered payload fails signature");
 using var wrongKey=RSA.Create(2048);
 Check(!wrongKey.VerifyData(Encoding.ASCII.GetBytes(parts[0]+"."+parts[1]),Decode(parts[2]),HashAlgorithmName.SHA256,RSASignaturePadding.Pkcs1),"Wrong signing key rejected");
 var claims=JsonDocument.Parse(Decode(parts[1])).RootElement;
 Check(claims.GetProperty("vip_user").GetBoolean(),"Existing registration policy signs VIP true");
 var normalToken=signer.Sign(first with {VipUser=false},now).Split('.');
 Check(!JsonDocument.Parse(Decode(normalToken[1])).RootElement.GetProperty("vip_user").GetBoolean(),"Standard subscription signs VIP false");
 Check(claims.GetProperty("sub").GetString()==vin&&claims.GetProperty("exp").GetInt64()==first.ExpiresAt,"Signed VIN and expiry");
 Check(claims.GetProperty("iss").GetString()=="voyahiran.ir"&&claims.GetProperty("aud").GetString()=="com.abrar.voyahiran"&&claims.GetProperty("v").GetInt32()==1,"Signed protocol identity");
 var fixture=Environment.GetEnvironmentVariable("INTEROP_OUTPUT");
 if(!string.IsNullOrEmpty(fixture)) {Directory.CreateDirectory(fixture);File.WriteAllText(Path.Combine(fixture,"public.pem"),signer.PublicKeyPem);File.WriteAllText(Path.Combine(fixture,"token.txt"),token);}
 var adminPath=Path.Combine(directory,"admin.txt");File.WriteAllText(adminPath,new string('a',64));options.AdminKeyPath=adminPath;
 var access=new AdminAccess(options);var http=new Microsoft.AspNetCore.Http.DefaultHttpContext();
 Check(!access.Allows(http),"Admin rejects missing token");http.Request.Headers.Authorization="Bearer wrong";Check(!access.Allows(http),"Admin rejects wrong token");
 http.Request.Headers.Authorization="Bearer "+new string('a',64);Check(access.Allows(http),"Admin accepts correct token");
 Console.WriteLine($"All {count} assertions passed");
} finally {NpgsqlConnection.ClearAllPools();Directory.Delete(directory,true);}
