using System.Text;

namespace VoyahIran.Installer.Core;

public sealed class SessionLog : IDisposable
{
    private readonly object gate = new();
    private readonly StreamWriter writer;
    private readonly List<string> secrets = [];
    public string DirectoryPath { get; }
    public string FilePath { get; }
    public event Action<string>? LineAdded;
    public SessionLog(string directory)
    {
        DirectoryPath = directory;
        Directory.CreateDirectory(directory);
        FilePath = Path.Combine(directory, $"install-{DateTime.Now:yyyyMMdd-HHmmss}-{Guid.NewGuid():N}.log");
        writer = new StreamWriter(new FileStream(FilePath, FileMode.CreateNew, FileAccess.Write, FileShare.ReadWrite), new UTF8Encoding(false)) { AutoFlush = true };
    }
    public void Hide(string secret) { lock (gate) secrets.Add(secret); }
    public void Write(string message)
    {
        lock (gate)
        {
            foreach (var secret in secrets) message = message.Replace(secret, "[REDACTED]", StringComparison.Ordinal);
            var line = $"[{DateTimeOffset.Now:O}] {message}";
            writer.WriteLine(line);
            LineAdded?.Invoke(line + Environment.NewLine);
        }
    }
    public string ReadText()
    {
        lock (gate)
        {
            using var stream = new FileStream(FilePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
            using var reader = new StreamReader(stream, Encoding.UTF8);
            return reader.ReadToEnd();
        }
    }
    public void Dispose() { lock (gate) writer.Dispose(); }
}
