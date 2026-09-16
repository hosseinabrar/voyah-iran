using Npgsql;
using NpgsqlTypes;
namespace VoyahIran.Subscriptions;
public sealed record SubscriptionGrant(string Vin, long ExpiresAt, string Status);
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
  using var command=connection.CreateCommand();
  command.CommandText="""
   CREATE TABLE IF NOT EXISTS subscriptions (
     vin TEXT PRIMARY KEY NOT NULL,
     created_at BIGINT NOT NULL,
     expires_at BIGINT NOT NULL,
     renewed_at BIGINT NOT NULL
   );
   CREATE TABLE IF NOT EXISTS renewal_events (
     id BIGSERIAL PRIMARY KEY,
     vin TEXT NOT NULL,
     occurred_at BIGINT NOT NULL,
     previous_expiry BIGINT,
     new_expiry BIGINT NOT NULL,
     reason TEXT NOT NULL
   );
   CREATE INDEX IF NOT EXISTS renewal_events_vin ON renewal_events(vin);
   CREATE TABLE IF NOT EXISTS subscription_blocks(vin TEXT PRIMARY KEY NOT NULL, blocked_at BIGINT NOT NULL, reason TEXT NOT NULL);
   CREATE TABLE IF NOT EXISTS block_events(id BIGSERIAL PRIMARY KEY, vin TEXT NOT NULL, blocked INTEGER NOT NULL, occurred_at BIGINT NOT NULL, reason TEXT NOT NULL);
   """;
  command.ExecuteNonQuery();
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
  read.CommandText="SELECT expires_at FROM subscriptions WHERE vin=@vin";
  read.Parameters.AddWithValue("@vin",vin);
  var existing=read.ExecuteScalar();
  long? previous=existing is long expiry ? expiry : null;
  var seconds=now.ToUnixTimeSeconds();
  var grant=RenewalPolicy.Decide(vin,previous,seconds,options);
  var expires=grant.ExpiresAt;var status=grant.Status;var renew=status!="unchanged";
  if(renew) {
   using var write=connection.CreateCommand(); write.Transaction=tx;
   write.CommandText="""
    INSERT INTO subscriptions(vin,created_at,expires_at,renewed_at) VALUES(@vin,@now,@expiry,@now)
    ON CONFLICT(vin) DO UPDATE SET expires_at=@expiry,renewed_at=@now;
    INSERT INTO renewal_events(vin,occurred_at,previous_expiry,new_expiry,reason) VALUES(@vin,@now,@previous,@expiry,@reason);
    """;
   write.Parameters.AddWithValue("@vin",vin);write.Parameters.AddWithValue("@now",seconds);
   write.Parameters.AddWithValue("@expiry",expires);write.Parameters.AddWithValue("@previous",NpgsqlDbType.Bigint,(object?)previous??DBNull.Value);
   write.Parameters.AddWithValue("@reason",status);write.ExecuteNonQuery();
  }
  tx.Commit();return new(vin,expires,status);
 }
 public SubscriptionDetails? Get(string vin) {
  using var c=Open();using var q=c.CreateCommand();
  q.CommandText="""
   SELECT s.created_at,s.expires_at,s.renewed_at,
   (SELECT COUNT(*) FROM renewal_events e WHERE e.vin=@vin AND e.reason='renewed'),
   b.blocked_at,b.reason
   FROM (SELECT @vin AS vin) v
   LEFT JOIN subscriptions s ON s.vin=v.vin
   LEFT JOIN subscription_blocks b ON b.vin=v.vin;
   """;q.Parameters.AddWithValue("@vin",vin);using var reader=q.ExecuteReader();reader.Read();
  if(reader.IsDBNull(0)&&reader.IsDBNull(4))return null;
  var count=reader.GetInt64(3);
  DateTimeOffset? Date(int i)=>reader.IsDBNull(i)?null:DateTimeOffset.FromUnixTimeSeconds(reader.GetInt64(i));
  return new(vin,Date(0),count==0?null:Date(2),Date(1),count,!reader.IsDBNull(4),Date(4),reader.IsDBNull(5)?null:reader.GetString(5));
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
  q.Parameters.AddWithValue("@vin",vin);q.Parameters.AddWithValue("@now",now.ToUnixTimeSeconds());q.Parameters.AddWithValue("@reason",reason);q.ExecuteNonQuery();
  q.CommandText="INSERT INTO block_events(vin,blocked,occurred_at,reason) VALUES(@vin,@blocked,@now,@reason)";q.Parameters.AddWithValue("@blocked",blocked?1:0);q.ExecuteNonQuery();tx.Commit();
 }
}

public sealed class SubscriptionBlockedException : Exception { }
public sealed record SubscriptionDetails(string Vin,DateTimeOffset? FirstRegisteredAt,DateTimeOffset? LastRenewedAt,DateTimeOffset? ExpiresAt,long RenewalCount,bool Blocked,DateTimeOffset? BlockedAt,string? BlockReason);
