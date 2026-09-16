using System.Security.Cryptography;
if(args.Length!=1){Console.Error.WriteLine("Usage: KeyTool <new-output-directory>");return 2;}
var path=Path.GetFullPath(args[0]);Directory.CreateDirectory(path);
if(File.Exists(Path.Combine(path,"db-password.txt"))||File.Exists(Path.Combine(path,"admin-token.txt"))||File.Exists(Path.Combine(path,"private.pem"))||File.Exists(Path.Combine(path,"public.pem"))) {Console.Error.WriteLine("Refusing to overwrite existing keys");return 1;}
using var rsa=RSA.Create(3072);
File.WriteAllText(Path.Combine(path,"db-password.txt"),Convert.ToHexString(RandomNumberGenerator.GetBytes(32)).ToLowerInvariant());
File.WriteAllText(Path.Combine(path,"admin-token.txt"),Convert.ToHexString(RandomNumberGenerator.GetBytes(32)).ToLowerInvariant());
File.WriteAllText(Path.Combine(path,"private.pem"),rsa.ExportPkcs8PrivateKeyPem());
File.WriteAllText(Path.Combine(path,"public.pem"),rsa.ExportSubjectPublicKeyInfoPem());
if(!OperatingSystem.IsWindows())File.SetUnixFileMode(Path.Combine(path,"private.pem"),UnixFileMode.UserRead|UnixFileMode.UserWrite);
if(!OperatingSystem.IsWindows())File.SetUnixFileMode(Path.Combine(path,"admin-token.txt"),UnixFileMode.UserRead|UnixFileMode.UserWrite);
if(!OperatingSystem.IsWindows())File.SetUnixFileMode(Path.Combine(path,"db-password.txt"),UnixFileMode.UserRead|UnixFileMode.UserWrite);
Console.WriteLine("Created db-password.txt, admin-token.txt, private.pem (server only) and public.pem (Android). Keep a secure backup.");return 0;
