import com.fitlog.app.domain.*;

public class BiliVideoCheck {
    private static int count;
    private static void check(boolean value) { count++; if (!value) throw new AssertionError("Bili link assertion " + count); }
    public static void main(String[] args) {
        BiliVideoLink links = BiliVideoLink.INSTANCE;
        String base = "https://www.bilibili.com/video/BV1kx4y1U714/";
        BiliVideoReference first = links.parse(base);
        check(first != null && first.getPage() == 1 && first.getParameter().equals("bvid"));
        check(links.parse(base + "?p=2&t=15").getPage() == 2);
        check(links.parse(base + "?p=1000").getPage() == 1000);
        check(links.parse(base + "?p=1001") == null);
        check(links.parse(base + "?p=0") == null);
        check(links.parse(base + "?p=-1") == null);
        check(links.parse(base + "?p=2&p=3") == null);
        check(links.parse(base + "?p=") == null);
        check(links.parse(base + "?p=abc") == null);
        check(links.parse("https://m.bilibili.com/video/av123456?p=3").getApiId().equals("123456"));
        check(links.parse("https://www.bilibili.com/video/av0") == null);
        check(links.parse("https://www.bilibili.com/video/av9999999999999999999") == null);
        check(links.parse("https://b23.tv/abc") == null);
        check(links.trustedPage("https://b23.tv/abc"));
        for (String url : new String[]{"", "file:///video/BV1kx4y1U714", "http://www.bilibili.com/video/BV1kx4y1U714/", "https://bilibili.com.evil.test/video/BV1kx4y1U714", "https://evil.test/video/BV1kx4y1U714", "https://name@www.bilibili.com/video/BV1kx4y1U714", base + "extra", "https://www.bilibili.com/video/../../abc"}) check(links.parse(url) == null);
        check(links.mediaUrl("http://upos.bilivideo.com/path/video.m4s?a=1&b=2").equals("https://upos.bilivideo.com/path/video.m4s?a=1&b=2"));
        check(links.mediaUrl("https://upos.bilivideo.cn/video.m4s") != null);
        check(links.mediaUrl("https://upos.akamaized.net/video.m4s") != null);
        for (String url : new String[]{"https://localhost/video", "https://127.0.0.1/video", "file:///video", "https://bilivideo.com.evil.test/video", "https://name@upos.bilivideo.com/video", "https://upos.bilivideo.com:8443/video", "https://upos.bilivideo.com/video#fragment"}) check(links.mediaUrl(url) == null);
        System.out.println(count + " Bilibili video link / CDN checks passed");
    }
}
