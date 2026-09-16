namespace VoyahIran.Subscriptions;
public static class RenewalPolicy {
 public static SubscriptionGrant Decide(string vin,long? previous,long now,SubscriptionOptions options) {
  var renew=previous is null || previous.Value-now<(long)options.RenewWithinDays*86400;
  var status=previous is null?"registered":renew?"renewed":"unchanged";
  var expires=renew?checked(Math.Max(previous??now,now)+(long)options.TermDays*86400):previous!.Value;
  return new(vin,expires,status);
 }
}
