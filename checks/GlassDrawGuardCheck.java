import com.fitlog.app.ui.glass.GlassDrawGuard;
import kotlin.Unit;

/** Fault injection: platform graphics failures fall back once and never hide app-content errors. */
public final class GlassDrawGuardCheck {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError(); checks++; }
    public static void main(String[] args) {
        GlassDrawGuard normal = new GlassDrawGuard();
        int[] calls = new int[3];
        normal.render(() -> { calls[0]++; return Unit.INSTANCE; },
            () -> { calls[1]++; return Unit.INSTANCE; }, e -> { calls[2]++; return Unit.INSTANCE; });
        check(calls[0] == 1 && calls[1] == 0 && calls[2] == 0);
        check(!normal.getFailed());
        for (Throwable fault : new Throwable[] { new IllegalArgumentException("uniform"),
                new IllegalStateException("released layer"), new NoClassDefFoundError("graphics API") }) {
            GlassDrawGuard guard = new GlassDrawGuard();
            int[] counts = new int[3];
            guard.render(() -> {
                counts[0]++;
                if (fault instanceof RuntimeException) throw (RuntimeException) fault;
                throw (LinkageError) fault;
            }, () -> { counts[1]++; return Unit.INSTANCE; }, e -> {
                check(e == fault); counts[2]++; return Unit.INSTANCE;
            });
            check(guard.getFailed());
            check(counts[0] == 1 && counts[1] == 1 && counts[2] == 1);
            guard.render(() -> { throw new AssertionError("failed effect must not be retried"); },
                () -> { counts[1]++; return Unit.INSTANCE; }, e -> { throw new AssertionError("duplicate report"); });
            check(counts[1] == 2);
            boolean propagated = false;
            try { guard.render(() -> Unit.INSTANCE, () -> { throw new IllegalStateException("fallback"); }, e -> Unit.INSTANCE); }
            catch (IllegalStateException expected) { propagated = true; }
            check(propagated);
        }
        System.out.println("Glass draw failure injection: " + checks + " checks passed");
    }
}
