using Npgsql;
using VoyahIran.Subscriptions;
internal static class MigrationTests {
 public static void Run(string connectionString,Action<bool,string> check) {
  var schema="migration_"+Guid.NewGuid().ToString("N");
  using var admin=new NpgsqlConnection(connectionString);admin.Open();
  using var setup=admin.CreateCommand();setup.CommandText=$"CREATE SCHEMA {schema}";setup.ExecuteNonQuery();
  try {
   var options=new SubscriptionOptions {ConnectionString=new NpgsqlConnectionStringBuilder(connectionString){SearchPath=schema,Timezone="Asia/Tehran"}.ToString()};
   using var c=new NpgsqlConnection(options.ConnectionString);c.Open();
   using var q=c.CreateCommand();
   q.CommandText="""
    CREATE TABLE subscriptions(vin text PRIMARY KEY,created_at bigint NOT NULL,expires_at bigint NOT NULL,renewed_at bigint NOT NULL);
    CREATE TABLE renewal_events(id bigserial PRIMARY KEY,vin text NOT NULL,occurred_at bigint NOT NULL,previous_expiry bigint,new_expiry bigint NOT NULL,reason text NOT NULL);
    CREATE TABLE subscription_blocks(vin text PRIMARY KEY,blocked_at bigint NOT NULL,reason text NOT NULL);
    CREATE TABLE block_events(id bigserial PRIMARY KEY,vin text NOT NULL,blocked integer NOT NULL,occurred_at bigint NOT NULL,reason text NOT NULL);
    INSERT INTO subscriptions VALUES('L123456789012345A',1700000000,1705184000,1702592000);
    INSERT INTO renewal_events(vin,occurred_at,previous_expiry,new_expiry,reason) VALUES
      ('L123456789012345A',1700000000,NULL,1702592000,'registered'),
      ('L123456789012345A',1702592000,1702592000,1705184000,'renewed');
    INSERT INTO subscription_blocks VALUES('L123456789012345A',1702592000,'keep reason');
    INSERT INTO block_events(vin,blocked,occurred_at,reason) VALUES('L123456789012345A',1,1702592000,'keep reason');
    """;
   q.ExecuteNonQuery();
   var store=new SubscriptionStore(options);
   q.CommandText="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@schema AND data_type='timestamp with time zone'";
   q.Parameters.AddWithValue("schema",schema);
   check((long)q.ExecuteScalar()! ==8,"Migration: all eight date columns are timestamptz");
   var info=store.Get("L123456789012345A")!;
   check(info.VipUser==true,"Migration: existing subscriptions become VIP");
   check(info.FirstRegisteredAt==DateTimeOffset.FromUnixTimeSeconds(1700000000)
    && info.ExpiresAt==DateTimeOffset.FromUnixTimeSeconds(1705184000)
    && info.LastRenewedAt==DateTimeOffset.FromUnixTimeSeconds(1702592000),"Migration: exact instants preserved in non-UTC session");
   check(info.RenewalCount==1 && info.Blocked && info.BlockReason=="keep reason"
    && info.BlockedAt==DateTimeOffset.FromUnixTimeSeconds(1702592000),"Migration: audit and block metadata preserved");
   q.CommandText="SELECT previous_expiry IS NULL FROM renewal_events WHERE reason='registered'";
   check((bool)q.ExecuteScalar()!,"Migration: nullable history preserved");
   q.CommandText="SELECT extract(epoch from occurred_at)::bigint FROM block_events LIMIT 1";
   check((long)q.ExecuteScalar()! ==1702592000,"Migration: block history timestamp preserved");
   var restarted=new SubscriptionStore(options);
   check(restarted.Get("L123456789012345A")==info,"Migration: repeated startup is idempotent");
   var now=DateTimeOffset.FromUnixTimeSeconds(1703000000).ToOffset(TimeSpan.FromHours(3.5));
   restarted.SetBlocked("L123456789012345A",false,now,"");
   check(restarted.RegisterOrRenew("L123456789012345A",now).Status=="unchanged","Migration: existing subscription remains usable");
   q.CommandText="UPDATE subscriptions SET vip_user=FALSE WHERE vin='L123456789012345A'";
   q.ExecuteNonQuery();
   var standard=new SubscriptionStore(options).RegisterOrRenew("L123456789012345A",now);
   check(!standard.VipUser,"Migration and unchanged grant preserve explicit standard tier");
   restarted.RegisterOrRenew("L123456789012345A",DateTimeOffset.FromUnixTimeSeconds(1705200000));
   check(restarted.Get("L123456789012345A")!.VipUser==true,"Actual renewal upgrades to VIP");
   check(restarted.Get("L123456789012345A")!.RenewalCount==2,"Migration: renewal writes real timestamps");
  } finally {
   // Only the generated test schema is removed, never existing application tables.
   setup.CommandText=$"DROP SCHEMA {schema} CASCADE";setup.ExecuteNonQuery();
  }
 }
}
