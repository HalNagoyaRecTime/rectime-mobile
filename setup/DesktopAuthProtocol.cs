using System;
using System.Net;
using System.Reflection;
using System.Runtime.InteropServices;

[assembly: AssemblyTitle("rectime-mobile")]
[assembly: AssemblyDescription("rectime-mobile")]
[assembly: AssemblyProduct("rectime-mobile")]

internal static class DesktopAuthProtocol
{
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int MessageBox(IntPtr window, string text, string title, uint type);

    private static int Main(string[] args)
    {
        Uri callback;
        if (args.Length != 1 || args[0].Length > 8192 ||
            !Uri.TryCreate(args[0], UriKind.Absolute, out callback) ||
            callback.Scheme != "com.rectime.mobile" || callback.Host != "auth" ||
            callback.AbsolutePath != "/callback" || callback.Port != -1 ||
            callback.UserInfo.Length != 0 || callback.Fragment.Length != 0)
        {
            MessageBox(IntPtr.Zero, "認証の戻り先が不正です。", "rectime-mobile", 0x10);
            return 1;
        }

        try
        {
            // ブラウザの応答を起動中のアプリへ渡す。認証情報は保存しない。
            var request = (HttpWebRequest)WebRequest.Create(
                "http://127.0.0.1:49152/auth/callback" + callback.Query);
            request.Proxy = null;
            request.AllowAutoRedirect = false;
            request.Timeout = 5000;
            request.ReadWriteTimeout = 5000;
            using (var response = (HttpWebResponse)request.GetResponse())
            {
                if (response.StatusCode != HttpStatusCode.OK) throw new InvalidOperationException();
            }
            return 0;
        }
        catch
        {
            MessageBox(IntPtr.Zero, "アプリを起動してから、もう一度ログインしてください。", "rectime-mobile", 0x10);
            return 1;
        }
    }
}
