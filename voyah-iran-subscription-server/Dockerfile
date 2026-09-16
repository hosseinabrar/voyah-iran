FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY Directory.Build.props ./
COPY src/VoyahIran.Subscriptions/VoyahIran.Subscriptions.csproj src/VoyahIran.Subscriptions/
RUN dotnet restore src/VoyahIran.Subscriptions
COPY src/ src/
RUN dotnet publish src/VoyahIran.Subscriptions -c Release --no-restore -o /out
FROM mcr.microsoft.com/dotnet/aspnet:8.0
WORKDIR /app
COPY --from=build /out ./
USER app
ENV ASPNETCORE_HTTP_PORTS=8080 Subscription__PrivateKeyPath=/run/secrets/signing_key Subscription__AdminKeyPath=/run/secrets/admin_key Subscription__DatabasePasswordPath=/run/secrets/db_password
EXPOSE 8080
ENTRYPOINT ["dotnet","VoyahIran.Subscriptions.dll"]
