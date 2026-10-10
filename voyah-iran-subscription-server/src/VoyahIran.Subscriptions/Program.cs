using Microsoft.AspNetCore.RateLimiting;
using Npgsql;
using System.Threading.RateLimiting;
using VoyahIran.Subscriptions;
using System.Security.Cryptography;
using System.Text;

var builder=WebApplication.CreateBuilder(args);
builder.Logging.AddFilter("Microsoft.AspNetCore",LogLevel.Warning);
builder.WebHost.ConfigureKestrel(o=>o.Limits.MaxRequestBodySize=2048);
var options=builder.Configuration.GetSection("Subscription").Get<SubscriptionOptions>()??new();
options.Validate();
builder.Services.AddSingleton(options);
builder.Services.AddSingleton<SubscriptionStore>();
builder.Services.AddSingleton<TokenSigner>();
builder.Services.AddSingleton<AdminAccess>();
builder.Services.AddSingleton(TimeProvider.System);
builder.Services.AddProblemDetails();
builder.Services.AddRateLimiter(o=>{
 o.RejectionStatusCode=429;
 // Bounded global limiter also works behind a proxy without trusting client-supplied IP headers.
 o.AddFixedWindowLimiter("subscription",p=>{p.PermitLimit=120;p.Window=TimeSpan.FromMinutes(1);p.QueueLimit=0;p.AutoReplenishment=true;});
 o.OnRejected=(ctx,_)=>{ctx.HttpContext.Response.Headers.RetryAfter="60";return ValueTask.CompletedTask;};
});
var app=builder.Build();
_=app.Services.GetRequiredService<TokenSigner>();
_=app.Services.GetRequiredService<AdminAccess>();
_=app.Services.GetRequiredService<SubscriptionStore>();
app.UseExceptionHandler();
app.UseRateLimiter();
app.MapGet("/health",()=>Results.Ok(new {status="ok"}));
app.MapPost("/registerOrRenewSubscription",(RegistrationRequest request,SubscriptionStore store,TokenSigner signer,TimeProvider clock,HttpContext http,ILogger<Program> log)=>{
 http.Response.Headers.CacheControl="no-store";
 var vin=Vin.Normalize(request.Vin);
 if(vin is null)return Results.Problem(statusCode:400,title:"invalid_vin",detail:"VIN must contain 17 letters/digits, excluding I, O and Q.");
 var now=clock.GetUtcNow();
 try {
  var grant=store.RegisterOrRenew(vin,now);
  var token=signer.Sign(grant,now);
  // Never log the raw VIN, request body or signed token.
  var fingerprint=Convert.ToHexString(SHA256.HashData(Encoding.ASCII.GetBytes(vin)))[..12];
  log.LogInformation("Subscription {Status} vehicle={Vehicle} expiresAt={ExpiresAt} request={RequestId}",grant.Status,fingerprint,grant.ExpiresAt,http.TraceIdentifier);
  return Results.Ok(new {token,expiresAt=DateTimeOffset.FromUnixTimeSeconds(grant.ExpiresAt),serverTime=now,status=grant.Status,vip_user=grant.VipUser});
 } catch(SubscriptionBlockedException) {
  return Results.Problem(statusCode:403,title:"subscription_blocked",detail:"Contact Voyah Iran support.");
 } catch(NpgsqlException e) when(e.IsTransient) {
  http.Response.Headers.RetryAfter="5";
  return Results.Problem(statusCode:503,title:"database_busy",detail:"Retry shortly.");
 }
}).RequireRateLimiting("subscription");
var admin=app.MapGroup("/admin/subscriptions").RequireRateLimiting("subscription");
admin.AddEndpointFilter(async (context,next)=>{
 context.HttpContext.Response.Headers.CacheControl="no-store";
 return context.HttpContext.RequestServices.GetRequiredService<AdminAccess>().Allows(context.HttpContext) ? await next(context) : Results.Unauthorized();
});
admin.MapGet("/",(int? offset,int? limit,SubscriptionStore store)=>{
 if(offset is <0 || limit is <1 or >100)return Results.BadRequest();
 return Results.Ok(store.List(offset??0,limit??50));
});
admin.MapGet("/{vin}",(string vin,SubscriptionStore store)=>{
 var normalized=Vin.Normalize(vin);if(normalized is null)return Results.BadRequest();
 var details=store.Get(normalized);return details is null?Results.NotFound():Results.Ok(details);
});
admin.MapPost("/block",(BlockRequest request,SubscriptionStore store,TimeProvider clock)=>{
 var vin=Vin.Normalize(request.Vin);if(vin is null||request.Blocked is null||request.Reason?.Length>200)return Results.BadRequest();
 store.SetBlocked(vin,request.Blocked.Value,clock.GetUtcNow(),request.Reason??"");return Results.Ok(new {vin,blocked=request.Blocked});
});
app.Run();
public sealed record RegistrationRequest(string? Vin);
public partial class Program { }

public sealed record BlockRequest(string? Vin,bool? Blocked,string? Reason);
