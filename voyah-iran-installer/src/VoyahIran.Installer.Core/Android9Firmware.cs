namespace VoyahIran.Installer.Core;

/// <summary>Original VDEX revisions reviewed with exact method and full round-trip checks.</summary>
public static class Android9Firmware
{
    public static bool IsSupportedVdex(string hash) => hash is
        "a9303263150cc12ee9e941c9d295a3bf0aa8282284b9c5b15adc6efd59c41c06" or
        "664c053504beb67f977132419b2a8080b207965f958530b9366d84758ead7299";
}
