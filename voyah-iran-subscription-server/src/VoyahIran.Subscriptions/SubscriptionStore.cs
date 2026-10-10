using Npgsql;
using NpgsqlTypes;
namespace VoyahIran.Subscriptions;
public sealed record SubscriptionGrant(string Vin, long ExpiresAt, string Status, bool VipUser = true);
public sealed class SubscriptionStore {
 private readonly string connectionString;
 private readonly SubscriptionOptions options;
 public SubscriptionStore(SubscriptionOptions options) {
  this.options=options;
  var config=new NpgsqlConnectionStringBuilder(options.ConnectionString);
  if(!string.IsNullOrWhiteSpace(options.DatabasePasswordPath))config.Password=File.ReadAllText(options.DatabasePasswordPath).Trim();
  config.Timeout=10;config.CommandTimeout=15;
  connectionString=config.ToString();
  using var connection=Open();
  using var tx=connection.BeginTransaction();
  using var command=connection.CreateCommand(); command.Transaction=tx;
  // Serialize schema migration across concurrent server startups.
  command.CommandText="SELECT pg_advisory_xact_lock(17423, 1)";
  command.ExecuteNonQuery();
  using var schema=new StreamReader(typeof(SubscriptionStore).Assembly.GetManifestResourceStream(
   "VoyahIran.Subscriptions.DatabaseSchema.sql") ?? throw new InvalidOperationException("Missing database schema"));
  command.CommandText=schema.ReadToEnd();
  command.ExecuteNonQuery();
  tx.Commit();
 }
 private static void LockVin(NpgsqlConnection c,NpgsqlTransaction tx,string vin) {
  using var command=c.CreateCommand();command.Transaction=tx;command.CommandText="SELECT pg_advisory_xact_lock(hashtextextended(@vin, 0))";command.Parameters.AddWithValue("@vin",vin);command.ExecuteNonQuery();
 }
 private NpgsqlConnection Open() { var c=new NpgsqlConnection(connectionString); c.Open(); return c; }
 public SubscriptionGrant RegisterOrRenew(string vin, DateTimeOffset now) {
  // Transaction-scoped per-VIN lock also covers first registration and block/unblock races.
  using var connection=Open();
  using var tx=connection.BeginTransaction();
  LockVin(connection,tx,vin);
  using var blocked=connection.CreateCommand();blocked.Transaction=tx;
  blocked.CommandText="SELECT 1 FROM subscription_blocks WHERE vin=@vin";blocked.Parameters.AddWithValue("@vin",vin);
  if(blocked.ExecuteScalar() is not null)throw new SubscriptionBlockedException();
  using var read=connection.CreateCommand(); read.Transaction=tx;
  read.CommandText="SELECT expires_at,vip_user FROM subscriptions WHERE vin=@vin";
  read.Parameters.AddWithValue("@vin",vin);
  long? previous=null; var vipUser=true;
  using(var reader=read.ExecuteReader()) if(reader.Read()) {
   previous=reader.GetFieldValue<DateTimeOffset>(0).ToUnixTimeSeconds();
   vipUser=reader.GetBoolean(1);
  }
  var seconds=now.ToUnixTimeSeconds();
  var grant=RenewalPolicy.Decide(vin,previous,seconds,options);
  var expires=grant.ExpiresAt;var status=grant.Status;var renew=status!="unchanged";
  if(renew) {
   using var write=connection.CreateCommand(); write.Transaction=tx;
   write.CommandText="""
    INSERT INTO subscriptions(vin,created_at,expires_at,renewed_at,vip_user) VALUES(@vin,@now,@expiry,@now,TRUE)
    ON CONFLICT(vin) DO UPDATE SET expires_at=@expiry,renewed_at=@now,vip_user=TRUE;
    INSERT INTO renewal_events(vin,occurred_at,previous_expiry,new_expiry,reason) VALUES(@vin,@now,@previous,@expiry,@reason);
    """;
   write.Parameters.AddWithValue("@vin",vin);write.Parameters.AddWithValue("@now",NpgsqlDbType.TimestampTz,now.UtcDateTime);
   write.Parameters.AddWithValue("@expiry",NpgsqlDbType.TimestampTz,DateTimeOffset.FromUnixTimeSeconds(expires).UtcDateTime);
   write.Parameters.AddWithValue("@previous",NpgsqlDbType.TimestampTz,previous.HasValue
    ? (object)DateTimeOffset.FromUnixTimeSeconds(previous.Value).UtcDateTime : DBNull.Value);
   write.Parameters.AddWithValue("@reason",status);write.ExecuteNonQuery();vipUser=true;
  }
  tx.Commit();return new(vin,expires,status,vipUser);
 }
 public SubscriptionDetails? Get(string vin) {
  using var c=Open();using var q=c.CreateCommand();
  q.CommandText="""
   SELECT s.created_at,s.expires_at,s.renewed_at,
   (SELECT COUNT(*) FROM renewal_events e WHERE e.vin=@vin AND e.reason='renewed'),
   b.blocked_at,b.reason,s.vip_user
   FROM (SELECT @vin AS vin) v
   LEFT JOIN subscriptions s ON s.vin=v.vin
   LEFT JOIN subscription_blocks b ON b.vin=v.vin;
   """;q.Parameters.AddWithValue("@vin",vin);using var reader=q.ExecuteReader();reader.Read();
  if(reader.IsDBNull(0)&&reader.IsDBNull(4))return null;
  var count=reader.GetInt64(3);
  DateTimeOffset? Date(int i)=>reader.IsDBNull(i)?null:reader.GetFieldValue<DateTimeOffset>(i);
  return new(vin,Date(0),count==0?null:Date(2),Date(1),count,!reader.IsDBNull(4),Date(4),reader.IsDBNull(5)?null:reader.GetString(5),reader.IsDBNull(6)?null:reader.GetBoolean(6));
 }
 public IReadOnlyList<SubscriptionDetails> List(int offset,int limit) {
  using var c=Open();using var q=c.CreateCommand();q.CommandText="SELECT vin FROM subscriptions UNION SELECT vin FROM subscription_blocks ORDER BY vin LIMIT @limit OFFSET @offset";
  q.Parameters.AddWithValue("@limit",limit);q.Parameters.AddWithValue("@offset",offset);
  var vins=new List<string>();using(var reader=q.ExecuteReader())while(reader.Read())vins.Add(reader.GetString(0));
  return vins.Select(Get).OfType<SubscriptionDetails>().ToList();
 }
 public void SetBlocked(string vin,bool blocked,DateTimeOffset now,string reason) {
  using var c=Open();using var tx=c.BeginTransaction();LockVin(c,tx,vin);using var q=c.CreateCommand();q.Transaction=tx;
  q.CommandText=blocked ? "INSERT INTO subscription_blocks(vin,blocked_at,reason) VALUES(@vin,@now,@reason) ON CONFLICT(vin) DO UPDATE SET blocked_at=@now,reason=@reason;" : "DELETE FROM subscription_blocks WHERE vin=@vin;";
  q.Parameters.AddWithValue("@vin",vin);q.Parameters.AddWithValue("@now",NpgsqlDbType.TimestampTz,now.UtcDateTime);q.Parameters.AddWithValue("@reason",reason);q.ExecuteNonQuery();
  q.CommandText="INSERT INTO block_events(vin,blocked,occurred_at,reason) VALUES(@vin,@blocked,@now,@reason)";q.Parameters.AddWithValue("@blocked",blocked?1:0);q.ExecuteNonQuery();tx.Commit();
 }
}

public sealed class SubscriptionBlockedException : Exception { }
public sealed record SubscriptionDetails(string Vin,DateTimeOffset? FirstRegisteredAt,DateTimeOffset? LastRenewedAt,DateTimeOffset? ExpiresAt,long RenewalCount,bool Blocked,DateTimeOffset? BlockedAt,string? BlockReason,bool? VipUser = null);
