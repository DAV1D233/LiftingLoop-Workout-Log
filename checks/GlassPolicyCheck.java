import com.fitlog.app.domain.GlassPolicy;
import com.fitlog.app.domain.GlassQuality;
import com.fitlog.app.domain.GlassRestriction;

/** Capability/gesture policy tests run on the JVM; they do not claim GPU/device verification. */
public final class GlassPolicyCheck {
    private static int checks;
    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
        checks++;
    }
    public static void main(String[] args) {
        GlassPolicy p = GlassPolicy.INSTANCE;
        for (int api = 26; api <= 35; api++) {
            GlassQuality expected = api >= 33 ? GlassQuality.REFRACTIVE : api >= 31 ? GlassQuality.FROSTED : GlassQuality.SOLID;
            check("API tier " + api, p.quality(api, true, false, false, false, false, false) == expected);
            check("software " + api, p.quality(api, false, false, false, false, false, false) == GlassQuality.SOLID);
            check("low RAM " + api, p.quality(api, true, true, false, false, false, false) == GlassQuality.SOLID);
            check("battery " + api, p.quality(api, true, false, true, false, false, false) == GlassQuality.SOLID);
            check("thermal " + api, p.quality(api, true, false, false, true, false, false) == GlassQuality.SOLID);
            check("opaque " + api, p.quality(api, true, false, false, false, true, false) == GlassQuality.SOLID);
            check("shader error " + api, p.quality(api, true, false, false, false, false, true) == GlassQuality.SOLID);
        }
        check("normal reason", p.restriction(36, true, false, false, false, false, false) == GlassRestriction.NONE);
        check("transparency reason", p.restriction(36, true, false, false, false, true, false) == GlassRestriction.TRANSPARENCY);
        check("hardware reason", p.restriction(36, false, false, false, false, false, false) == GlassRestriction.SOFTWARE);
        check("memory reason", p.restriction(36, true, true, false, false, false, false) == GlassRestriction.MEMORY);
        check("battery reason", p.restriction(36, true, false, true, false, false, false) == GlassRestriction.BATTERY);
        check("thermal reason", p.restriction(36, true, false, false, true, false, false) == GlassRestriction.THERMAL);
        check("failed reason", p.restriction(36, true, false, false, false, false, true) == GlassRestriction.FAILED);
        check("old system reason", p.restriction(26, true, false, false, false, false, false) == GlassRestriction.UNSUPPORTED);
        check("user choice priority", p.restriction(26, false, true, true, true, true, true) == GlassRestriction.TRANSPARENCY);
        check("up starts at nearest", p.selectedAfterKey(-1, 4, true) == 0);
        check("down starts at furthest", p.selectedAfterKey(-1, 4, false) == 3);
        check("up moves", p.selectedAfterKey(1, 4, true) == 2);
        check("down moves", p.selectedAfterKey(1, 4, false) == 0);
        check("upper clamp", p.selectedAfterKey(3, 4, true) == 3);
        check("lower clamp", p.selectedAfterKey(0, 4, false) == 0);
        check("empty menu", p.selectedAfterKey(-1, 0, true) == -1);
        check("release selected", p.confirms(true, 0, 4, true, false));
        check("cancelled pointer", !p.confirms(false, 0, 4, true, false));
        check("off target", !p.confirms(true, -1, 4, true, false));
        check("stale index", !p.confirms(true, 4, 4, true, false));
        check("busy", !p.confirms(true, 0, 4, false, false));
        check("no double navigation", !p.confirms(true, 0, 4, true, true));
        check("no empty confirmation", !p.confirms(true, 0, 0, true, false));
        System.out.println("Glass capabilities and gesture policy: " + checks + " checks passed");
    }
}
